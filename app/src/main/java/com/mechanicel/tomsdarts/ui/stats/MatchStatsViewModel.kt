package com.mechanicel.tomsdarts.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mechanicel.tomsdarts.TomsDartsApp
import com.mechanicel.tomsdarts.analytics.AnalyticsLeg
import com.mechanicel.tomsdarts.analytics.computeHitDistribution
import com.mechanicel.tomsdarts.analytics.computeSequenceStats
import com.mechanicel.tomsdarts.analytics.computeX01Metrics
import com.mechanicel.tomsdarts.data.entity.Leg
import com.mechanicel.tomsdarts.data.repository.MatchRepository
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
 * ViewModel des Match-Statistik-Screens (Phase 5, Lieferung 2/2, ADR-0037).
 *
 * Laedt Match, Teilnehmer (Sitzreihenfolge), alle Legs und die Wurf-Daten
 * (`legsForMatch`, alle Spieler) **einmal**; die Kennzahlen je Teilnehmer
 * stammen aus denselben puren Funktionen wie im Spieler-Screen (ADR-0035/-0036).
 * Die Spieler-Chip-Reihe ([selectPlayer]) steuert Trefferverteilung **und**
 * Wurfmuster gemeinsam; beide werden je Wechsel auf [computeDispatcher] neu
 * berechnet.
 *
 * Geloeschte Spieler (`playerId = null` nach SET_NULL) werden unter
 * [DELETED_PARTICIPANT_KEY] zu einem Abschnitt zusammengefasst. Da Legs und
 * Matches beim Abschluss immer mit Sieger gespeichert werden, gilt ein
 * abgeschlossenes Leg/Match ohne (bekannten) Sieger als von einem geloeschten
 * Spieler gewonnen, sofern einer teilnahm — sonst als "Kein Sieger".
 *
 * Rein lokal (offline-first, keine Cloud/Backend/Tracking).
 *
 * @param matchId Match, dessen Statistik gezeigt wird.
 * @param computeDispatcher Dispatcher der Kennzahlen-Berechnung (Tests: Test-Dispatcher).
 */
class MatchStatsViewModel(
    private val matchId: Long,
    private val matchRepository: MatchRepository,
    private val playerRepository: PlayerRepository,
    private val statsRepository: StatsRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /** Trigger fuer einen erneuten Ladeversuch (siehe [retry]). */
    private val retryTrigger = MutableStateFlow(0)

    /** Gewaehlter Teilnehmer der Chip-Reihe; `null` = erster Teilnehmer. */
    private val selectedPlayer = MutableStateFlow<Long?>(null)

    /** Trigger-Wert des zuletzt gelieferten Ergebnisses (Muster [PlayerStatsViewModel]). */
    private var loadedTrigger: Int? = null

    /**
     * Reaktiver UI-Zustand. Wie im Spieler-Screen wird [MatchStatsUiState.Loading]
     * nur beim ersten Laden und nach [retry]/Fehler emittiert; ein Neuladen nach
     * dem `WhileSubscribed`-Timeout ersetzt den gehaltenen Inhalt ohne Flackern.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MatchStatsUiState> = retryTrigger
        .flatMapLatest { trigger ->
            flow { emit(load()) }
                .flatMapLatest { snapshot ->
                    when {
                        snapshot == null -> flowOf(MatchStatsUiState.NotFound)
                        snapshot.legs.isEmpty() -> flowOf(MatchStatsUiState.Empty(snapshot.header))
                        else -> selectedPlayer
                            .map { key -> buildContent(snapshot, key) }
                            .flowOn(computeDispatcher)
                    }
                }
                .onEach { loadedTrigger = trigger }
                .onStart { if (loadedTrigger != trigger) emit(MatchStatsUiState.Loading) }
                .catch { throwable -> emit(MatchStatsUiState.Error(throwable.message)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MatchStatsUiState.Loading,
        )

    /** Waehlt den Teilnehmer fuer Trefferverteilung und Wurfmuster. */
    fun selectPlayer(key: Long) {
        selectedPlayer.value = key
    }

    /** Laedt die Daten erneut (z.B. nach einem Fehler). */
    fun retry() {
        retryTrigger.update { it + 1 }
    }

    /** Ein Teilnehmer-Abschnitt; [name] `null` = geloeschter Spieler. */
    private data class Participant(val key: Long, val name: String?)

    /** Einmal geladene, bereits auf Teilnehmer-Schluessel abgebildete Rohdaten. */
    private class Snapshot(
        val header: MatchHeaderUi,
        val isX01: Boolean,
        val participants: List<Participant>,
        val winnerKey: Long?,
        val legs: List<AnalyticsLeg>,
        val legRows: List<MatchLegUi>,
        val finishedLegWinners: List<Long?>,
    )

    private suspend fun load(): Snapshot? {
        val match = matchRepository.getMatch(matchId) ?: return null
        val seats = matchRepository.getMatchPlayers(matchId).sortedWith(compareBy({ it.position }, { it.id }))
        val participants = mutableListOf<Participant>()
        for (seat in seats) {
            val player = seat.playerId?.let { playerRepository.getPlayer(it) }
            val participant = if (player == null) {
                Participant(DELETED_PARTICIPANT_KEY, null)
            } else {
                Participant(player.id, player.name)
            }
            if (participants.none { it.key == participant.key }) participants += participant
        }
        val known = participants.map { it.key }.filter { it != DELETED_PARTICIPANT_KEY }.toSet()
        val rawLegs = statsRepository.legsForMatch(matchId)
        // Aufnahmen ohne bekannten Teilnehmer (geloescht) gehoeren zum Sammel-Abschnitt.
        val hasUnknownVisits = rawLegs.any { leg -> leg.visits.any { it.playerId !in known } }
        if (hasUnknownVisits && participants.none { it.key == DELETED_PARTICIPANT_KEY }) {
            participants += Participant(DELETED_PARTICIPANT_KEY, null)
        }
        val hasDeleted = participants.any { it.key == DELETED_PARTICIPANT_KEY }

        /** Bildet einen gespeicherten Sieger auf einen Teilnehmer-Schluessel ab. */
        fun winnerKeyOf(winnerId: Long?, finished: Boolean): Long? = when {
            !finished -> null
            winnerId != null && winnerId in known -> winnerId
            hasDeleted -> DELETED_PARTICIPANT_KEY
            else -> null
        }

        fun resultOf(winnerId: Long?, finished: Boolean): MatchResultUi {
            if (!finished) return MatchResultUi.Open
            val key = winnerKeyOf(winnerId, finished = true) ?: return MatchResultUi.NoWinner
            return MatchResultUi.Winner(participants.first { it.key == key }.name)
        }

        val legs = rawLegs.map { leg ->
            leg.copy(
                winnerId = winnerKeyOf(leg.winnerId, leg.finished) ?: leg.winnerId,
                visits = leg.visits.map { visit ->
                    if (visit.playerId in known) visit else visit.copy(playerId = DELETED_PARTICIPANT_KEY)
                },
            )
        }
        val withSets = match.setsToWin > 1
        val entityLegs = matchRepository.getLegs(matchId)
            .sortedWith(compareBy<Leg>({ it.setNumber ?: 0 }, { it.legNumber }, { it.id }))
        val finishedLegs = entityLegs.filter { it.endedAt != null }
        val matchFinished = match.endedAt != null
        return Snapshot(
            header = MatchHeaderUi(
                modeType = match.modeType,
                startedAt = match.startedAt,
                result = resultOf(match.winnerId, matchFinished),
                legsPlayed = finishedLegs.size,
            ),
            isX01 = match.modeType == GameModeCatalog.X01,
            participants = participants,
            winnerKey = winnerKeyOf(match.winnerId, matchFinished),
            legs = legs,
            legRows = entityLegs.map { leg ->
                MatchLegUi(
                    legId = leg.id,
                    setNumber = leg.setNumber?.takeIf { withSets },
                    legNumber = leg.legNumber,
                    result = resultOf(leg.winnerId, leg.endedAt != null),
                )
            },
            finishedLegWinners = finishedLegs.map { winnerKeyOf(it.winnerId, finished = true) },
        )
    }

    /**
     * Baut den [MatchStatsUiState.Content] fuer den gewaehlten Teilnehmer
     * [requestedKey]; ein unbekannter oder fehlender Schluessel faellt auf den
     * ersten Teilnehmer zurueck.
     */
    private fun buildContent(snapshot: Snapshot, requestedKey: Long?): MatchStatsUiState.Content {
        val selected = requestedKey?.takeIf { key -> snapshot.participants.any { it.key == key } }
            ?: snapshot.participants.first().key
        val players = snapshot.participants.map { participant ->
            val darts = snapshot.legs.sumOf { leg ->
                leg.visits.filter { it.playerId == participant.key }.sumOf { it.darts.size }
            }
            MatchPlayerStatsUi(
                key = participant.key,
                name = participant.name,
                isWinner = snapshot.winnerKey == participant.key,
                x01 = if (snapshot.isX01) computeX01Metrics(snapshot.legs, participant.key) else null,
                legsWon = snapshot.finishedLegWinners.count { it == participant.key },
                legsPlayed = snapshot.finishedLegWinners.size,
                dartsThrown = darts.takeIf { it > 0 },
            )
        }
        return MatchStatsUiState.Content(
            header = snapshot.header,
            isX01 = snapshot.isX01,
            players = players,
            selectedPlayerKey = selected,
            distribution = computeHitDistribution(snapshot.legs, selected),
            sequences = computeSequenceStats(snapshot.legs, selected).toSequenceSectionUi(),
            legs = snapshot.legRows,
        )
    }

    companion object {
        /**
         * Factory mit dem Match [matchId] als Parameter (Muster
         * [PlayerStatsViewModel.provideFactory]); Repositories aus dem
         * AppContainer der [TomsDartsApp].
         */
        fun provideFactory(matchId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TomsDartsApp
                MatchStatsViewModel(
                    matchId = matchId,
                    matchRepository = app.container.matchRepository,
                    playerRepository = app.container.playerRepository,
                    statsRepository = app.container.statsRepository,
                )
            }
        }
    }
}
