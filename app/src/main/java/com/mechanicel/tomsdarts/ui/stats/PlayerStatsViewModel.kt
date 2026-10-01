package com.mechanicel.tomsdarts.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mechanicel.tomsdarts.TomsDartsApp
import com.mechanicel.tomsdarts.analytics.AnalyticsLeg
import com.mechanicel.tomsdarts.analytics.AnalyticsMatchSummary
import com.mechanicel.tomsdarts.analytics.computeHitDistribution
import com.mechanicel.tomsdarts.analytics.computeSequenceStats
import com.mechanicel.tomsdarts.analytics.computeX01Metrics
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.data.repository.StatsRepository
import com.mechanicel.tomsdarts.game.GameModeCatalog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * ViewModel des Spieler-Statistik-Screens (Phase 5, ADR-0037).
 *
 * Laedt Spieler, Legs (`legsForPlayer(playerId, null)`, alle Modi) und Matches
 * **einmal** und filtert danach nur noch im Speicher nach dem gewaehlten Modus
 * ([selectMode]). Die Kennzahlen (pure Funktionen aus `analytics`, ADR-0035)
 * werden je Filterwechsel auf [computeDispatcher] neu berechnet.
 *
 * Rein lokal (offline-first, keine Cloud/Backend/Tracking).
 *
 * @param playerId Spieler, dessen Statistik gezeigt wird.
 * @param computeDispatcher Dispatcher der Kennzahlen-Berechnung (Tests: Test-Dispatcher).
 */
class PlayerStatsViewModel(
    private val playerId: Long,
    private val playerRepository: PlayerRepository,
    private val statsRepository: StatsRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /** Trigger fuer einen erneuten Ladeversuch (siehe [retry]). */
    private val retryTrigger = MutableStateFlow(0)

    /** Gewaehlter Modus-Filter, `null` = Alle (gespiegelt in [PlayerStatsUiState.Content.selectedMode]). */
    private val selectedMode = MutableStateFlow<String?>(null)

    /**
     * Trigger-Wert, fuer den zuletzt ein Ergebnis (kein Fehler) geliefert wurde;
     * `null` vor dem ersten Laden. Steuert, ob [PlayerStatsUiState.Loading]
     * emittiert wird (siehe [uiState]).
     */
    private var loadedTrigger: Int? = null

    /**
     * Reaktiver UI-Zustand.
     *
     * Kehrt der Screen nach mehr als 5 s zurueck (`WhileSubscribed`-Timeout), startet
     * der Upstream neu und laedt die Daten erneut — so erscheinen inzwischen
     * gespielte Matches. [PlayerStatsUiState.Loading] wird dabei aber **nur** beim
     * ersten Laden und nach [retry] (bzw. nach einem Fehler) emittiert: Fuer einen
     * bereits geladenen Trigger-Wert bleibt der gehaltene Inhalt stehen, bis das
     * neue Ergebnis ihn ersetzt — kein Loading-Flackern beim Zurueckkehren.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PlayerStatsUiState> = retryTrigger
        .flatMapLatest { trigger ->
            flow { emit(load()) }
                .flatMapLatest { snapshot ->
                    when {
                        snapshot == null -> flowOf(PlayerStatsUiState.PlayerNotFound)
                        snapshot.legs.isEmpty() -> flowOf(PlayerStatsUiState.Empty(snapshot.playerName))
                        else -> selectedMode
                            .map { mode -> buildContent(snapshot, mode) }
                            .flowOn(computeDispatcher)
                    }
                }
                .onEach { loadedTrigger = trigger }
                .onStart { if (loadedTrigger != trigger) emit(PlayerStatsUiState.Loading) }
                .catch { throwable -> emit(PlayerStatsUiState.Error(throwable.message)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlayerStatsUiState.Loading,
        )

    /** Waehlt den Modus-Filter; `null` = Alle. */
    fun selectMode(modeType: String?) {
        selectedMode.value = modeType
    }

    /** Laedt die Daten erneut (z.B. nach einem Fehler). */
    fun retry() {
        retryTrigger.update { it + 1 }
    }

    /** Einmal geladene Rohdaten; `null`, wenn der Spieler nicht existiert. */
    private class Snapshot(
        val playerName: String,
        val legs: List<AnalyticsLeg>,
        val matches: List<AnalyticsMatchSummary>,
        val modeFilters: List<String>,
    )

    private suspend fun load(): Snapshot? {
        val player = playerRepository.getPlayer(playerId) ?: return null
        val legs = statsRepository.legsForPlayer(playerId, null)
        val matches = statsRepository.matchesForPlayer(playerId)
        return Snapshot(
            playerName = player.name,
            legs = legs,
            matches = matches,
            modeFilters = orderModeFilters(matches.map { it.modeType }),
        )
    }

    /**
     * Baut den [PlayerStatsUiState.Content] fuer den Filter [requestedMode].
     * Ein Filter, der nicht (mehr) unter den gespielten Modi ist oder bei
     * ausgeblendeter Filter-Leiste, gilt als "Alle".
     */
    private fun buildContent(snapshot: Snapshot, requestedMode: String?): PlayerStatsUiState.Content {
        val showFilter = snapshot.modeFilters.size >= 2
        val mode = requestedMode?.takeIf { showFilter && it in snapshot.modeFilters }
        val legs = if (mode == null) snapshot.legs else snapshot.legs.filter { it.modeType == mode }
        val matches = if (mode == null) snapshot.matches else snapshot.matches.filter { it.modeType == mode }

        val sections = buildList {
            add(
                StatsSectionUi.Overview(
                    matches = matches.size,
                    wins = matches.count { it.winnerId == playerId },
                ),
            )
            if (mode == null || mode == GameModeCatalog.X01) {
                val metrics = computeX01Metrics(legs, playerId)
                add(if (metrics.dartsThrown == 0) StatsSectionUi.X01Empty else StatsSectionUi.X01(metrics))
            }
            add(StatsSectionUi.Distribution(computeHitDistribution(legs, playerId)))
            // Dieselbe gefilterte Leg-Liste wie die Trefferverteilung; Top-10, die
            // Aufbereitung filtert Rauschen (Anzahl < 2) heraus.
            add(StatsSectionUi.Sequences(computeSequenceStats(legs, playerId).toSequenceSectionUi()))
        }
        return PlayerStatsUiState.Content(
            playerName = snapshot.playerName,
            modeFilters = snapshot.modeFilters,
            selectedMode = mode,
            sections = sections,
        )
    }

    companion object {
        /**
         * Factory mit dem Spieler [playerId] als Parameter (Muster
         * `GameViewModel.provideFactory`); Repositories aus dem [AppContainer]
         * der [TomsDartsApp].
         */
        fun provideFactory(playerId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TomsDartsApp
                PlayerStatsViewModel(
                    playerId = playerId,
                    playerRepository = app.container.playerRepository,
                    statsRepository = app.container.statsRepository,
                )
            }
        }
    }
}
