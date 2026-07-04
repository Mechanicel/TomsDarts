package com.mechanicel.tomsdarts.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mechanicel.tomsdarts.TomsDartsApp
import com.mechanicel.tomsdarts.data.entity.Leg
import com.mechanicel.tomsdarts.data.entity.Match
import com.mechanicel.tomsdarts.data.entity.MatchPlayer
import com.mechanicel.tomsdarts.data.entity.Throw
import com.mechanicel.tomsdarts.data.entity.Turn
import com.mechanicel.tomsdarts.data.repository.MatchRepository
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.X01Mode
import com.mechanicel.tomsdarts.game.X01State
import com.mechanicel.tomsdarts.game.engine.LegEngineSnapshot
import com.mechanicel.tomsdarts.game.engine.MatchEngine
import com.mechanicel.tomsdarts.game.engine.MatchSnapshot
import com.mechanicel.tomsdarts.ui.input.DartInputState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel des Spiel-Bildschirms (Mehrspieler, X01, Legs/Sets).
 *
 * Koppelt den Eingabe-Ziffernblock ([DartInputState]) des aktuellen Werfers an
 * die reine Match-Logik ([MatchEngine] mit [X01Mode]) und persistiert
 * abgeschlossene Aufnahmen throw-level pro Spieler ueber das [MatchRepository].
 * Die [MatchEngine] uebernimmt Spielerwechsel sowie Leg-/Set-/Match-Aggregation;
 * dieses ViewModel spiegelt deren Snapshots in den [GameUiState] und schreibt
 * die Persistenz fort. Bleibt rein lokal (offline-first, keine Cloud/Backend/Tracking).
 *
 * Die In-Memory-Logik (Engine, Eingabe, UI-State) wird synchron aktualisiert;
 * nur die Persistenz laeuft asynchron im [viewModelScope].
 *
 * @param matchRepository Repository fuer Match/Leg/Turn/Throw.
 * @param playerRepository Repository fuer Spieler.
 * @param playerIds Teilnehmer in Reihenfolge (>= 2 gueltige fuer ein Match).
 * @param config Regel-Konfiguration des Matches (Startscore, Double-Out, Legs/Sets).
 */
class GameViewModel(
    private val matchRepository: MatchRepository,
    private val playerRepository: PlayerRepository,
    private val playerIds: List<Long>,
    private val config: GameConfig,
) : ViewModel() {

    private lateinit var matchEngine: MatchEngine<X01State>
    private var input: DartInputState = DartInputState()

    private var match: Match? = null
    private var currentLeg: Leg? = null

    /** Laufender Aufnahme-Index innerhalb des aktuellen Legs (ueber alle Spieler). */
    private var turnIndex: Int = 0

    /** Anzahl der im aktuellen Leg geworfenen (akzeptierten) Darts je Spieler. */
    private val legDartsByPlayer: MutableMap<Long, Int> = mutableMapOf()

    /** Zuordnung Spieler-ID -> Anzeigename. */
    private var playerNames: Map<Long, String> = emptyMap()

    private val _uiState = MutableStateFlow<GameUiState>(GameUiState.Loading)

    /** Reaktiver UI-Zustand des Spiel-Bildschirms. */
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _bustEvents = MutableStateFlow(0)

    /**
     * Transientes Bust-Ereignis als hochzaehlender Zaehler. Jede Erhoehung
     * signalisiert genau einen Bust; die UI kann darauf z.B. ein kurzes
     * Feedback ausloesen, ohne dass der Zustand "haengen bleibt".
     */
    val bustEvents: StateFlow<Int> = _bustEvents.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                // Spieler in Reihenfolge aufloesen; unbekannte IDs fallen heraus.
                val resolved = playerIds.mapNotNull { id ->
                    playerRepository.getPlayer(id)?.let { id to it.name }
                }
                if (resolved.size < 2) {
                    _uiState.value = GameUiState.NoPlayer
                    return@launch
                }
                val orderedIds = resolved.map { it.first }
                playerNames = resolved.toMap()
                matchEngine = MatchEngine(X01Mode(), config, orderedIds)

                val now = System.currentTimeMillis()
                val matchId = matchRepository.createMatch(
                    Match(
                        modeType = MODE_TYPE,
                        startScore = config.startScore,
                        doubleOut = config.doubleOut,
                        legsToWin = config.legsToWin,
                        setsToWin = config.setsToWin,
                        startedAt = now,
                    ),
                )
                match = Match(
                    id = matchId,
                    modeType = MODE_TYPE,
                    startScore = config.startScore,
                    doubleOut = config.doubleOut,
                    legsToWin = config.legsToWin,
                    setsToWin = config.setsToWin,
                    startedAt = now,
                )

                orderedIds.forEachIndexed { index, id ->
                    matchRepository.addPlayerToMatch(
                        MatchPlayer(matchId = matchId, playerId = id, position = index),
                    )
                }

                val leg = Leg(
                    matchId = matchId,
                    setNumber = matchEngine.currentSetNumber,
                    legNumber = matchEngine.currentLegNumber,
                    startedAt = now,
                )
                val legId = matchRepository.addLeg(leg)
                currentLeg = leg.copy(id = legId)

                _uiState.value = buildPlaying(matchEngine.snapshot(), input)
            } catch (t: Throwable) {
                _uiState.value = GameUiState.Error
            }
        }
    }

    /** Eingabe einer Zahl-Taste (Segment 1..20). */
    fun onNumber(n: Int) = onInput { it.pressNumber(n) }

    /** Eingabe der Bull-Taste. */
    fun onBull() = onInput { it.pressBull() }

    /** Eingabe eines Fehlwurfs (Miss/Out). */
    fun onOut() = onInput { it.pressOut() }

    /** Umschalten des DOUBLE-Modus (reine Eingabe-Aenderung). */
    fun onToggleDouble() = onInput { it.toggleDouble() }

    /** Umschalten des TRIPLE-Modus (reine Eingabe-Aenderung). */
    fun onToggleTriple() = onInput { it.toggleTriple() }

    /**
     * Nimmt den zuletzt gesetzten Dart der laufenden Aufnahme zurueck: sowohl im
     * Eingabe-Zustand als auch in der Engine ([MatchEngine.undoLastDart]) und
     * aktualisiert die Anzeige.
     */
    fun onUndo() {
        val playing = _uiState.value as? GameUiState.Playing ?: return
        val next = input.undo()
        if (next.darts.size == input.darts.size) return
        matchEngine.undoLastDart()
        input = next
        // Undo entfernt einen akzeptierten Dart des aktuellen Werfers.
        val current = matchEngine.currentPlayerId
        legDartsByPlayer[current]?.let { if (it > 0) legDartsByPlayer[current] = it - 1 }
        _uiState.value = buildPlaying(matchEngine.snapshot(), next)
    }

    /**
     * Startet aus dem [GameUiState.LegWon]-Zustand das naechste Leg. Die Engine
     * hat intern bereits rotiert; hier wird nur das neue [Leg] persistiert und
     * die Eingabe/Indizes zuruckgesetzt.
     */
    fun onNewLeg() {
        if (_uiState.value !is GameUiState.LegWon) return
        val currentMatch = match ?: return
        viewModelScope.launch {
            val snapshot = matchEngine.snapshot()
            val leg = Leg(
                matchId = currentMatch.id,
                setNumber = snapshot.currentSetNumber,
                legNumber = snapshot.currentLegNumber,
                startedAt = System.currentTimeMillis(),
            )
            val legId = matchRepository.addLeg(leg)
            currentLeg = leg.copy(id = legId)

            input = DartInputState()
            turnIndex = 0
            legDartsByPlayer.clear()

            _uiState.value = buildPlaying(snapshot, input)
        }
    }

    /**
     * Wendet eine Eingabe-Transition an. Entstand dabei ein neuer Dart, wird er
     * an die Engine durchgereicht ([onDart]); sonst (Modifier-Toggle oder No-op
     * bei voller Aufnahme) wird nur der Eingabe-Zustand reflektiert.
     */
    private fun onInput(transition: (DartInputState) -> DartInputState) {
        val playing = _uiState.value as? GameUiState.Playing ?: return
        val next = transition(input)
        if (next.darts.size > input.darts.size) {
            onDart(next.darts.last(), next)
        } else {
            input = next
            _uiState.value = playing.copy(input = next)
        }
    }

    /**
     * Reicht genau einen neuen Dart an die [MatchEngine] durch und verarbeitet das
     * Ergebnis: regulaer offen -> Anzeige aktualisieren; Aufnahme-Ende -> Aufnahme
     * des Werfers throw-level persistieren und je nach Ausgang Leg/Match abschliessen
     * ([GameUiState.LegWon]/[GameUiState.MatchWon]) oder zur naechsten Aufnahme/zum
     * naechsten Spieler wechseln (Bust loest zusaetzlich ein [bustEvents]-Ereignis aus).
     */
    private fun onDart(dart: Dart, nextInput: DartInputState) {
        val playing = _uiState.value as? GameUiState.Playing ?: return
        val result = matchEngine.applyDart(dart)
        if (!result.accepted) return

        val throwerId = result.playerId
        legDartsByPlayer[throwerId] = (legDartsByPlayer[throwerId] ?: 0) + 1

        if (!result.turnEnded) {
            input = nextInput
            _uiState.value = buildPlaying(result.snapshot, nextInput)
            return
        }

        val legSnapshot = result.legSnapshot
        val endedTurnIndex = turnIndex
        val bust = result.bust
        val legId = currentLeg?.id
        val winnerDarts = legDartsByPlayer[throwerId]

        if (result.matchWon) {
            val winnerId = result.matchWinnerId ?: throwerId
            _uiState.value = GameUiState.MatchWon(
                players = buildPlayers(result.snapshot),
                matchWinnerName = playerNames[winnerId].orEmpty(),
                dartsUsed = winnerDarts,
            )
            viewModelScope.launch {
                if (legId != null) persistTurn(legId, throwerId, endedTurnIndex, bust, legSnapshot)
                finishLegAndMatch(winnerId)
            }
            return
        }

        if (result.legWon) {
            // Engine hat intern bereits auf das naechste Leg rotiert.
            val snapshot = result.snapshot
            _uiState.value = GameUiState.LegWon(
                players = buildPlayers(snapshot),
                legWinnerName = playerNames[throwerId].orEmpty(),
                nextStarterName = playerNames[snapshot.currentPlayerId].orEmpty(),
                nextLegNumber = snapshot.currentLegNumber,
                dartsUsed = winnerDarts,
            )
            viewModelScope.launch {
                if (legId != null) persistTurn(legId, throwerId, endedTurnIndex, bust, legSnapshot)
                finishLeg(throwerId)
            }
            return
        }

        // Regulaeres oder Bust-Ende: Engine hat den Spieler bereits gewechselt.
        input = DartInputState()
        turnIndex++
        _uiState.value = buildPlaying(result.snapshot, input)
        if (bust) {
            _bustEvents.update { it + 1 }
        }
        viewModelScope.launch {
            if (legId != null) persistTurn(legId, throwerId, endedTurnIndex, bust, legSnapshot)
        }
    }

    /** Baut den [GameUiState.Playing] aus einem Match-Snapshot und der Eingabe. */
    private fun buildPlaying(
        snapshot: MatchSnapshot<X01State>,
        input: DartInputState,
    ): GameUiState.Playing = GameUiState.Playing(
        players = buildPlayers(snapshot),
        startScore = config.startScore,
        input = input,
        currentLegNumber = snapshot.currentLegNumber,
        currentSetNumber = snapshot.currentSetNumber,
        legsToWin = config.legsToWin,
        setsToWin = config.setsToWin,
    )

    /** Baut die Spieler-Zeilen des Scoreboards aus einem Match-Snapshot. */
    private fun buildPlayers(snapshot: MatchSnapshot<X01State>): List<PlayerScoreUi> =
        snapshot.playerStates.mapIndexed { index, ps ->
            PlayerScoreUi(
                playerId = ps.playerId,
                name = playerNames[ps.playerId].orEmpty(),
                remaining = ps.state.remaining,
                legsWon = ps.legsWonInSet,
                setsWon = ps.setsWon,
                isCurrent = index == snapshot.currentPlayerIndex,
            )
        }

    /** Persistiert eine abgeschlossene Aufnahme inkl. aller geworfenen Darts. */
    private suspend fun persistTurn(
        legId: Long,
        playerId: Long,
        turnIndex: Int,
        bust: Boolean,
        snapshot: LegEngineSnapshot<X01State>,
    ) {
        val turnId = matchRepository.addTurn(
            Turn(
                legId = legId,
                playerId = playerId,
                turnIndex = turnIndex,
                bust = bust,
                totalScored = snapshot.turnScored,
            ),
        )
        snapshot.turnDarts.forEachIndexed { i, dart ->
            matchRepository.addThrow(
                Throw(
                    turnId = turnId,
                    dartIndex = i + 1,
                    segment = dart.segment,
                    multiplier = dart.multiplier,
                    value = dart.value,
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** Schliesst das aktuelle Leg mit dem Gewinner ab (kein REPLACE -> kein CASCADE). */
    private suspend fun finishLeg(winnerId: Long) {
        val now = System.currentTimeMillis()
        currentLeg?.let { leg ->
            val finished = leg.copy(endedAt = now, winnerId = winnerId)
            matchRepository.updateLeg(finished)
            currentLeg = finished
        }
    }

    /** Schliesst aktuelles Leg und Match mit dem Gewinner ab. */
    private suspend fun finishLegAndMatch(winnerId: Long) {
        finishLeg(winnerId)
        val now = System.currentTimeMillis()
        match?.let { m ->
            val finished = m.copy(endedAt = now, winnerId = winnerId)
            matchRepository.updateMatch(finished)
            match = finished
        }
    }

    companion object {
        /** Spielmodus-Kennung der persistierten Matches. */
        const val MODE_TYPE: String = "X01"

        /**
         * Factory, die die Repositories aus dem [AppContainer] der [TomsDartsApp]
         * bezieht und den Spiel-Bildschirm mit der X01-Match-Konfiguration startet.
         * Startscore, Double-Out sowie die Gewinnschwellen legsToWin/setsToWin
         * werden im Setup-Bildschirm gewaehlt und hier durchgereicht.
         *
         * @param playerIds Teilnehmer in Reihenfolge (>= 2 fuer ein Match).
         * @param startScore Gewaehlter Startpunktwert (z.B. 301/501/701).
         * @param doubleOut Ob zum Auschecken ein Double noetig ist.
         * @param legsToWin Anzahl zu gewinnender Legs je Set (first to N).
         * @param setsToWin Anzahl zu gewinnender Sets fuer den Matchsieg (first to N).
         */
        fun provideFactory(
            playerIds: List<Long>,
            startScore: Int,
            doubleOut: Boolean,
            legsToWin: Int,
            setsToWin: Int,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TomsDartsApp
                GameViewModel(
                    matchRepository = app.container.matchRepository,
                    playerRepository = app.container.playerRepository,
                    playerIds = playerIds,
                    config = GameConfig(
                        startScore = startScore,
                        doubleOut = doubleOut,
                        legsToWin = legsToWin,
                        setsToWin = setsToWin,
                    ),
                )
            }
        }
    }
}
