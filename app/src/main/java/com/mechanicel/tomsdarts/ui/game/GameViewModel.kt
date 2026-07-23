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
import com.mechanicel.tomsdarts.game.GameMode
import com.mechanicel.tomsdarts.game.GameModeCatalog
import com.mechanicel.tomsdarts.game.X01Mode
import com.mechanicel.tomsdarts.game.engine.LegEngineSnapshot
import com.mechanicel.tomsdarts.game.engine.MatchEngine
import com.mechanicel.tomsdarts.game.engine.MatchSnapshot
import com.mechanicel.tomsdarts.ui.input.DartInputState
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel des Spiel-Bildschirms (Mehrspieler, Legs/Sets), generisch ueber den
 * modus-spezifischen Spielerzustand [S].
 *
 * Koppelt den Eingabe-Ziffernblock ([DartInputState]) des aktuellen Werfers an
 * die reine Match-Logik ([MatchEngine] mit dem injizierten [mode]) und
 * persistiert abgeschlossene Aufnahmen throw-level pro Spieler ueber das
 * [MatchRepository]. Die [MatchEngine] uebernimmt Spielerwechsel sowie Leg-/Set-/
 * Match-Aggregation; dieses ViewModel spiegelt deren Snapshots in den
 * [GameUiState] und schreibt die Persistenz fort. Der [uiAdapter] uebersetzt den
 * modus-spezifischen Zustand in Anzeige-Kern und Checkout-Vorschlag, sodass das
 * ViewModel selbst modus-agnostisch bleibt. Bleibt rein lokal (offline-first,
 * keine Cloud/Backend/Tracking).
 *
 * Die In-Memory-Logik (Engine, Eingabe, UI-State) wird synchron aktualisiert;
 * nur die Persistenz laeuft asynchron im [viewModelScope].
 *
 * @param S Modus-spezifischer Spielerzustand (z.B. [com.mechanicel.tomsdarts.game.X01State]).
 * @param matchRepository Repository fuer Match/Leg/Turn/Throw.
 * @param playerRepository Repository fuer Spieler.
 * @param playerIds Teilnehmer in Reihenfolge (>= 2 gueltige fuer ein Match).
 * @param config Regel-Konfiguration des Matches (Startscore, Double-Out, Legs/Sets).
 * @param mode Die Modus-Strategie (z.B. [X01Mode]); liefert auch die
 *   persistierte Modus-Kennung ([GameMode.key]).
 * @param uiAdapter Uebersetzt den Modus-Zustand in Anzeige-Kern/Checkout.
 */
class GameViewModel<S : Any>(
    private val matchRepository: MatchRepository,
    private val playerRepository: PlayerRepository,
    private val playerIds: List<Long>,
    private val config: GameConfig,
    private val mode: GameMode<S>,
    private val uiAdapter: ModeUiAdapter<S>,
) : ViewModel() {

    private lateinit var matchEngine: MatchEngine<S>
    private var input: DartInputState = DartInputState()

    private var match: Match? = null
    private var currentLeg: Leg? = null

    /** Laufender Aufnahme-Index innerhalb des aktuellen Legs (ueber alle Spieler). */
    private var turnIndex: Int = 0

    /** Anzahl der im aktuellen Leg geworfenen (akzeptierten) Darts je Spieler. */
    private val legDartsByPlayer: MutableMap<Long, Int> = mutableMapOf()

    /** Zuordnung Spieler-ID -> Anzeigename. */
    private var playerNames: Map<Long, String> = emptyMap()

    /**
     * Zuletzt im aktuellen Leg abgeschlossene Aufnahme je Spieler. Wird am
     * Zug-Ende fuer den werfenden Spieler gesetzt und in [buildPlayers] auf die
     * jeweilige [PlayerScoreUi] gespiegelt, damit jede Karte die eigene letzte
     * Aufnahme zeigt. Zu Leg-Beginn leer (siehe [onNewLeg]).
     */
    private val lastTurnByPlayer: MutableMap<Long, LastTurn> = mutableMapOf()

    /**
     * Interner Merker fuer die zuletzt abgeschlossene Aufnahme eines Spielers.
     *
     * @param darts Tatsaechlich geworfene Darts der Aufnahme (bis zu 3).
     * @param bust True, wenn die Aufnahme ein Bust war.
     */
    private data class LastTurn(val darts: List<Dart>, val bust: Boolean)

    /**
     * Stapel der im aktuellen Leg abgeschlossenen Aufnahmen (juengste zuletzt),
     * fuer das Undo ueber Aufnahme-Grenzen. Wird zu jedem neuen Leg geleert.
     *
     * @param turnIdDeferred Ergebnis des asynchronen Turn-Inserts (die neue
     *   Turn-ID); ueber [Deferred.await] laesst sich die Insert-vs-Delete-Race
     *   beim Undo sauber aufloesen.
     * @param playerId Werfer dieser abgeschlossenen Aufnahme.
     * @param darts Tatsaechlich geworfene Darts der Aufnahme (bis zu 3).
     * @param bust True, wenn die Aufnahme ein Bust war.
     */
    private data class CompletedTurn(
        val turnIdDeferred: Deferred<Long>,
        val playerId: Long,
        val darts: List<Dart>,
        val bust: Boolean,
    )

    /** Undo-Stapel der abgeschlossenen Aufnahmen des laufenden Legs. */
    private val completedTurns: ArrayDeque<CompletedTurn> = ArrayDeque()

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
                matchEngine = MatchEngine(mode, config, orderedIds)

                val now = System.currentTimeMillis()
                val matchId = matchRepository.createMatch(
                    Match(
                        modeType = mode.key,
                        startScore = config.startScore,
                        doubleOut = config.doubleOut,
                        legsToWin = config.legsToWin,
                        setsToWin = config.setsToWin,
                        startedAt = now,
                    ),
                )
                match = Match(
                    id = matchId,
                    modeType = mode.key,
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
     * Nimmt den zuletzt im laufenden Leg gesetzten Dart zurueck - unbegrenzt und
     * ueber Aufnahme- sowie Spielerwechsel-Grenzen hinweg (aber nicht ueber Leg-/
     * Set-Grenzen; dort gibt es kein Undo). Jeder Aufruf spult genau einen Dart
     * zurueck.
     *
     * Ablauf: Ist die Aufnahme des aktuellen Werfers leer, aber im Leg wurden
     * bereits Darts geworfen, wird die zuletzt ABGESCHLOSSENE Aufnahme wieder
     * geoeffnet (Cross-Turn-Undo): der zugehoerige [Turn] wird aus der Persistenz
     * geloescht, Zug-Index und letzte-Aufnahme-Merker werden zurueckgedreht.
     * Andernfalls wird nur der letzte Dart der laufenden Aufnahme entfernt. In
     * beiden Faellen ist die [MatchEngine] die Wahrheitsquelle; die Eingabe wird
     * anschliessend aus deren Snapshot abgeleitet.
     */
    fun onUndo() {
        val playing = _uiState.value as? GameUiState.Playing ?: return
        // Nichts zum Zuruecknehmen im laufenden Leg.
        if (matchEngine.dartsThrownInCurrentLeg == 0) return

        // Vor dem Engine-Undo bestimmen, ob die Aufnahme-Grenze ueberschritten
        // wird: leere laufende Aufnahme -> die vorige, abgeschlossene wird wieder
        // geoeffnet (Cross-Turn), sonst reines Intra-Turn-Undo.
        val before = matchEngine.snapshot()
        val currentDartsInTurn = before.playerStates
            .getOrNull(before.currentPlayerIndex)?.legSnapshot?.dartsInTurn ?: 0
        val crossTurn = currentDartsInTurn == 0

        // Rueckgabewert pruefen (frueher ignoriert): nur bei echtem Undo weiter.
        if (!matchEngine.undoLastDart()) return

        val after = matchEngine.snapshot()

        if (crossTurn) {
            // Die zuletzt abgeschlossene Aufnahme wird wieder geoeffnet.
            turnIndex--
            completedTurns.removeLastOrNull()?.let { entry ->
                // Race Insert-vs-Delete ueber await aufloesen.
                viewModelScope.launch { matchRepository.deleteTurn(entry.turnIdDeferred.await()) }
                // Letzte-Aufnahme-Merker des Werfers auf dessen vorherige
                // abgeschlossene Aufnahme im Stack setzen (oder entfernen).
                val prev = completedTurns.lastOrNull { it.playerId == entry.playerId }
                if (prev != null) {
                    lastTurnByPlayer[entry.playerId] = LastTurn(prev.darts, prev.bust)
                } else {
                    lastTurnByPlayer.remove(entry.playerId)
                }
            }
        }

        // Einen akzeptierten Dart des nach dem Undo aktiven Spielers abziehen.
        val current = after.currentPlayerId
        legDartsByPlayer[current]?.let { if (it > 0) legDartsByPlayer[current] = it - 1 }

        // Eingabe einheitlich aus dem Engine-Snapshot ableiten (deckt Intra- und
        // Cross-Turn ab): die laufende Aufnahme des aktuellen Spielers.
        val turnDarts = after.playerStates
            .getOrNull(after.currentPlayerIndex)?.legSnapshot?.turnDarts.orEmpty()
        input = DartInputState(darts = turnDarts)
        _uiState.value = buildPlaying(after, input)
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
            // Undo-Stapel gehoert zum abgeschlossenen Leg (kein Undo ueber
            // Leg-Grenzen).
            completedTurns.clear()
            // Letzte Aufnahme darf nicht ins neue Leg bluten (alle Spieler).
            lastTurnByPlayer.clear()

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
                if (legId != null) {
                    persistTurn(legId, throwerId, endedTurnIndex, bust, legSnapshot).await()
                }
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
                if (legId != null) {
                    persistTurn(legId, throwerId, endedTurnIndex, bust, legSnapshot).await()
                }
                finishLeg(throwerId)
            }
            return
        }

        // Regulaeres oder Bust-Ende: Engine hat den Spieler bereits gewechselt.
        // Die gerade beendete Aufnahme wird als "letzte Aufnahme" des werfenden
        // Spielers (throwerId, vor dem Wechsel ermittelt) gemerkt; das Bust-Flag
        // stammt aus diesem regulaer/Bust-Zweig (result.bust).
        input = DartInputState()
        turnIndex++
        lastTurnByPlayer[throwerId] = LastTurn(darts = legSnapshot.turnDarts, bust = bust)
        _uiState.value = buildPlaying(result.snapshot, input)
        if (bust) {
            _bustEvents.update { it + 1 }
        }
        if (legId != null) {
            // Abgeschlossene Aufnahme persistieren und fuer das Cross-Turn-Undo
            // auf den Stapel legen (juengste zuletzt).
            val deferred = persistTurn(legId, throwerId, endedTurnIndex, bust, legSnapshot)
            completedTurns.addLast(
                CompletedTurn(
                    turnIdDeferred = deferred,
                    playerId = throwerId,
                    darts = legSnapshot.turnDarts,
                    bust = bust,
                ),
            )
        }
    }

    /** Baut den [GameUiState.Playing] aus einem Match-Snapshot und der Eingabe. */
    private fun buildPlaying(
        snapshot: MatchSnapshot<S>,
        input: DartInputState,
    ): GameUiState.Playing {
        // Checkout-Vorschlag fuer den aktuellen Werfer aus dessen Zustand ueber
        // den Modus-Adapter. Laeuft bei jeder Zustandsaenderung neu und
        // aktualisiert sich damit live (bei X01 sinkt der Rest pro Dart).
        val currentState = snapshot.playerStates
            .getOrNull(snapshot.currentPlayerIndex)?.state
        val checkout = currentState?.let { uiAdapter.checkout(it, config) }
        return GameUiState.Playing(
            players = buildPlayers(snapshot),
            startScore = config.startScore,
            input = input,
            currentLegNumber = snapshot.currentLegNumber,
            currentSetNumber = snapshot.currentSetNumber,
            legsToWin = config.legsToWin,
            setsToWin = config.setsToWin,
            checkout = checkout,
            // Undo moeglich, sobald im laufenden Leg mindestens ein Dart gefallen
            // ist (auch nach Spielerwechsel, aber nicht ueber Leg-/Set-Grenzen).
            canUndo = matchEngine.dartsThrownInCurrentLeg > 0,
        )
    }

    /** Baut die Spieler-Zeilen des Scoreboards aus einem Match-Snapshot. */
    private fun buildPlayers(snapshot: MatchSnapshot<S>): List<PlayerScoreUi> =
        snapshot.playerStates.mapIndexed { index, ps ->
            val lastTurn = lastTurnByPlayer[ps.playerId]
            PlayerScoreUi(
                playerId = ps.playerId,
                name = playerNames[ps.playerId].orEmpty(),
                board = uiAdapter.board(ps.state),
                legsWon = ps.legsWonInSet,
                setsWon = ps.setsWon,
                isCurrent = index == snapshot.currentPlayerIndex,
                lastTurnDarts = lastTurn?.darts.orEmpty(),
                lastTurnBust = lastTurn?.bust ?: false,
            )
        }

    /**
     * Persistiert eine abgeschlossene Aufnahme inkl. aller geworfenen Darts
     * ASYNCHRON und liefert die neue Turn-ID als [Deferred]. Der Aufrufer kann
     * das Ergebnis in [completedTurns] ablegen und beim Undo per [Deferred.await]
     * abwarten, um die Insert-vs-Delete-Race sauber aufzuloesen.
     */
    private fun persistTurn(
        legId: Long,
        playerId: Long,
        turnIndex: Int,
        bust: Boolean,
        snapshot: LegEngineSnapshot<S>,
    ): Deferred<Long> = viewModelScope.async {
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
        turnId
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

        /**
         * Factory, die die Repositories aus dem [AppContainer] der [TomsDartsApp]
         * bezieht und den Spiel-Bildschirm fuer den ueber [modeKey] gewaehlten
         * Modus startet. Startscore, Double-Out sowie die Gewinnschwellen
         * legsToWin/setsToWin werden im Setup-Bildschirm gewaehlt und hier
         * durchgereicht.
         *
         * Die typisierte Aufloesung des Modus (konkreter [GameMode] samt
         * passendem [ModeUiAdapter]) lebt bewusst NUR hier im [modeKey]-`when`:
         * so bleibt der generische Typ [S] gekapselt und ein neuer Modus dockt
         * durch einen zusaetzlichen Zweig an.
         *
         * @param modeKey Kennung des Spielmodus (siehe [GameModeCatalog]).
         * @param playerIds Teilnehmer in Reihenfolge (>= 2 fuer ein Match).
         * @param startScore Gewaehlter Startpunktwert (z.B. 301/501/701).
         * @param doubleOut Ob zum Auschecken ein Double noetig ist.
         * @param legsToWin Anzahl zu gewinnender Legs je Set (first to N).
         * @param setsToWin Anzahl zu gewinnender Sets fuer den Matchsieg (first to N).
         * @throws IllegalArgumentException wenn [modeKey] keinem bekannten Modus
         *   entspricht.
         */
        fun provideFactory(
            modeKey: String,
            playerIds: List<Long>,
            startScore: Int,
            doubleOut: Boolean,
            legsToWin: Int,
            setsToWin: Int,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TomsDartsApp
                val config = GameConfig(
                    startScore = startScore,
                    doubleOut = doubleOut,
                    legsToWin = legsToWin,
                    setsToWin = setsToWin,
                )
                when (modeKey) {
                    GameModeCatalog.X01 -> GameViewModel(
                        matchRepository = app.container.matchRepository,
                        playerRepository = app.container.playerRepository,
                        playerIds = playerIds,
                        config = config,
                        mode = X01Mode(),
                        uiAdapter = X01UiAdapter(),
                    )
                    else -> throw IllegalArgumentException(
                        "Unbekannter Spielmodus: '$modeKey'",
                    )
                }
            }
        }
    }
}
