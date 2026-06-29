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
import com.mechanicel.tomsdarts.game.engine.LegEngine
import com.mechanicel.tomsdarts.game.engine.LegEngineSnapshot
import com.mechanicel.tomsdarts.ui.input.DartInputState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel des Spiel-Bildschirms (Einzelspieler, ein Leg im X01-Modus).
 *
 * Koppelt den Eingabe-Ziffernblock ([DartInputState]) an die reine Spiel-Logik
 * ([LegEngine] mit [X01Mode]) und persistiert abgeschlossene Aufnahmen
 * throw-level ueber das [MatchRepository]. Bleibt rein lokal (offline-first,
 * keine Cloud/Backend/Tracking).
 *
 * Die In-Memory-Spiel-Logik (Engine, Eingabe, UI-State) wird synchron
 * aktualisiert; nur die Persistenz laeuft asynchron im [viewModelScope]. Die
 * Compose-Screen-UI sowie die Verdrahtung in MainActivity/Profil folgen in
 * einem separaten Schritt (B2).
 *
 * @param matchRepository Repository fuer Match/Leg/Turn/Throw.
 * @param playerRepository Repository fuer Spieler.
 * @param playerId Der werfende Spieler.
 * @param config Regel-Konfiguration des Matches (Startscore, Double-Out, ...).
 */
class GameViewModel(
    private val matchRepository: MatchRepository,
    private val playerRepository: PlayerRepository,
    private val playerId: Long,
    private val config: GameConfig,
) : ViewModel() {

    private var engine: LegEngine<X01State> = LegEngine(X01Mode(), config)
    private var input: DartInputState = DartInputState()

    private var match: Match? = null
    private var currentLeg: Leg? = null
    private var turnIndex: Int = 0

    /** Anzahl der im aktuellen Leg geworfenen (akzeptierten) Darts, inkl. Bust-Darts. */
    private var legDarts: Int = 0

    private var playerName: String = ""

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
                val player = playerRepository.getPlayer(playerId)
                if (player == null) {
                    _uiState.value = GameUiState.NoPlayer
                    return@launch
                }
                playerName = player.name

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

                matchRepository.addPlayerToMatch(
                    MatchPlayer(matchId = matchId, playerId = playerId, position = 0),
                )

                val leg = Leg(matchId = matchId, legNumber = 1, startedAt = now)
                val legId = matchRepository.addLeg(leg)
                currentLeg = leg.copy(id = legId)

                _uiState.value = GameUiState.Playing(
                    playerName = playerName,
                    startScore = config.startScore,
                    remaining = config.startScore,
                    input = input,
                )
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
     * Eingabe-Zustand als auch in der Engine ([LegEngine.undoLastDart]) und
     * aktualisiert die verbleibende Restpunktzahl.
     */
    fun onUndo() {
        val playing = _uiState.value as? GameUiState.Playing ?: return
        val next = input.undo()
        if (next.darts.size == input.darts.size) return
        engine.undoLastDart()
        input = next
        _uiState.value = playing.copy(remaining = engine.state.remaining, input = next)
    }

    /**
     * Startet aus dem [GameUiState.Won]-Zustand ein neues Leg im selben Match:
     * neues [Leg] (legNumber + 1), Engine/Eingabe/Indizes zuruecksetzen.
     */
    fun onNewLeg() {
        if (_uiState.value !is GameUiState.Won) return
        val currentMatch = match ?: return
        viewModelScope.launch {
            val nextLegNumber = (currentLeg?.legNumber ?: 0) + 1
            val leg = Leg(
                matchId = currentMatch.id,
                legNumber = nextLegNumber,
                startedAt = System.currentTimeMillis(),
            )
            val legId = matchRepository.addLeg(leg)
            currentLeg = leg.copy(id = legId)

            engine = LegEngine(X01Mode(), config)
            input = DartInputState()
            turnIndex = 0
            legDarts = 0

            _uiState.value = GameUiState.Playing(
                playerName = playerName,
                startScore = config.startScore,
                remaining = config.startScore,
                input = input,
            )
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
     * Reicht genau einen neuen Dart an die Engine durch und verarbeitet das
     * Ergebnis: regulaer offen -> Restpunktzahl aktualisieren; Aufnahme-Ende ->
     * Aufnahme throw-level persistieren und je nach Ausgang Leg/Match abschliessen
     * (Checkout -> [GameUiState.Won]) oder die naechste Aufnahme beginnen
     * (regulaer oder Bust; Bust loest zusaetzlich ein [bustEvents]-Ereignis aus).
     */
    private fun onDart(dart: Dart, nextInput: DartInputState) {
        val playing = _uiState.value as? GameUiState.Playing ?: return
        val result = engine.applyDart(dart)
        if (!result.accepted) return

        legDarts++

        if (!result.turnEnded) {
            input = nextInput
            _uiState.value = playing.copy(remaining = engine.state.remaining, input = nextInput)
            return
        }

        val snapshot = result.snapshot
        val endedTurnIndex = turnIndex
        val bust = result.bust

        if (result.legWon) {
            val dartsUsed = legDarts
            _uiState.value = GameUiState.Won(playerName = playerName, dartsUsed = dartsUsed)
            viewModelScope.launch {
                persistTurn(endedTurnIndex, bust, snapshot)
                finishLegAndMatch()
            }
            return
        }

        // Regulaeres oder Bust-Ende: synchron die naechste Aufnahme beginnen.
        engine.startNewTurn()
        input = DartInputState()
        turnIndex++
        _uiState.value = playing.copy(remaining = engine.state.remaining, input = input)
        if (bust) {
            _bustEvents.update { it + 1 }
        }
        viewModelScope.launch {
            persistTurn(endedTurnIndex, bust, snapshot)
        }
    }

    /** Persistiert eine abgeschlossene Aufnahme inkl. aller geworfenen Darts. */
    private suspend fun persistTurn(
        turnIndex: Int,
        bust: Boolean,
        snapshot: LegEngineSnapshot<X01State>,
    ) {
        val legId = currentLeg?.id ?: return
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

    /** Schliesst das aktuelle Leg und (bei legsToWin == 1) das Match ab. */
    private suspend fun finishLegAndMatch() {
        val now = System.currentTimeMillis()
        currentLeg?.let { leg ->
            val finished = leg.copy(endedAt = now, winnerId = playerId)
            matchRepository.updateLeg(finished)
            currentLeg = finished
        }
        match?.let { m ->
            val finished = m.copy(endedAt = now, winnerId = playerId)
            matchRepository.updateMatch(finished)
            match = finished
        }
    }

    companion object {
        /** Spielmodus-Kennung der persistierten Matches. */
        const val MODE_TYPE: String = "X01"

        /**
         * Factory, die die Repositories aus dem [AppContainer] der [TomsDartsApp]
         * bezieht und den Spiel-Bildschirm mit der Standard-X01-Konfiguration
         * (501, Double-Out, 1 Leg, 1 Set) startet.
         */
        fun provideFactory(playerId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TomsDartsApp
                GameViewModel(
                    matchRepository = app.container.matchRepository,
                    playerRepository = app.container.playerRepository,
                    playerId = playerId,
                    config = GameConfig(
                        startScore = 501,
                        doubleOut = true,
                        legsToWin = 1,
                        setsToWin = 1,
                    ),
                )
            }
        }
    }
}
