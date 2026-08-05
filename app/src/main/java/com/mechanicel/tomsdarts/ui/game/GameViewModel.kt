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
import com.mechanicel.tomsdarts.game.AroundTheClockMode
import com.mechanicel.tomsdarts.game.CricketMode
import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.GameMode
import com.mechanicel.tomsdarts.game.GameModeCatalog
import com.mechanicel.tomsdarts.game.ShanghaiMode
import com.mechanicel.tomsdarts.game.X01Mode
import com.mechanicel.tomsdarts.game.engine.LegEngineSnapshot
import com.mechanicel.tomsdarts.game.engine.MatchEngine
import com.mechanicel.tomsdarts.game.engine.MatchSnapshot
import com.mechanicel.tomsdarts.ui.input.DartInputState
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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

    /**
     * Post-Wechsel-Snapshot, der waehrend der Kontroll-Pause zurueckgehalten wird.
     * Nicht-null genau dann, wenn gerade eine Pause laeuft (siehe [onDart]-Regulaer-
     * Zweig). In [onContinue] angewendet (Wechsel zum naechsten Spieler), in
     * [onUndo] verworfen (die abgeschlossene Aufnahme wird wieder geoeffnet).
     */
    private var pendingSnapshot: MatchSnapshot<S>? = null

    /**
     * Timer-Job der laufenden Kontroll-Pause. Loest nach [TURN_REVIEW_MILLIS]
     * automatisch [onContinue] aus. Wird bei "Weiter"/"Korrigieren" gecancelt.
     */
    private var turnReviewJob: Job? = null

    /**
     * Job, der den Leg-/Match-Abschluss nach einem Sieg-Dart persistiert
     * (Aufnahme schreiben, danach Leg bzw. Leg+Match abschliessen). [onUndoWin]
     * wartet ihn ab, bevor es den Abschluss wieder zurueckdreht - sonst koennte
     * das Zuruecknehmen die noch laufenden Schreibvorgaenge ueberholen.
     */
    private var winFinalizeJob: Job? = null

    /**
     * Synchrones Re-Entrancy-Flag fuer [onUndoWin]: wird VOR dem
     * `viewModelScope.launch` gesetzt (also noch bevor der erste Suspendierungspunkt
     * erreicht wird) und erst im `finally` der Coroutine zurueckgesetzt. Ohne dieses
     * Flag koennte ein zweiter Tap waehrend einer Suspendierung (z.B. beim
     * [winFinalizeJob]-Join oder den Repository-Aufrufen) den `stillWon`-Re-Check
     * passieren, weil `_uiState` erst am Ende der Coroutine auf [GameUiState.Playing]
     * wechselt. [onNewLeg] respektiert dasselbe Flag, damit waehrend eines laufenden
     * Rueckzugs kein neues Leg begonnen wird.
     */
    private var undoWinInProgress = false

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
     * Set-Grenzen: ein bereits begonnenes neues Leg spult nicht ins vorige
     * zurueck). Jeder Aufruf spult genau einen Dart zurueck. Den Sonderfall
     * "versehentlicher Sieg-Dart" deckt [onUndoWin] ab.
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
        // Laeuft gerade die Kontroll-Pause ("Korrigieren")? Dann zuerst die Pause
        // aufloesen (Timer stoppen, zurueckgehaltenen Wechsel VERWERFEN, ohne ihn
        // anzuwenden). Anschliessend greift das regulaere Cross-Turn-Undo unten und
        // oeffnet die soeben abgeschlossene Aufnahme wieder (Werfer erneut am Zug).
        if (pendingSnapshot != null) {
            turnReviewJob?.cancel()
            turnReviewJob = null
            pendingSnapshot = null
        }
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
     * Nimmt einen versehentlichen Leg-/Match-Sieg zurueck: der zuletzt geworfene
     * (Sieg-)Dart wird entfernt, der bereits persistierte Leg- bzw. Match-Abschluss
     * rueckgaengig gemacht und die Aufnahme des Werfers wieder geoeffnet
     * ([GameUiState.Playing]).
     *
     * Nur aus [GameUiState.LegWon]/[GameUiState.MatchWon] wirksam - im laufenden
     * Spiel (und in allen anderen Zustaenden) ein No-op; dort nimmt [onUndo]
     * zurueck.
     *
     * Gewinner-agnostisch: zurueckgenommen wird stets der zuletzt geworfene Dart
     * samt der Aufnahme des WERFERS (Engine und Undo-Stapel sind die
     * Wahrheitsquelle). Das gilt auch, wenn das Leg per Rangvergleich an einen
     * anderen Spieler ging (rundenbasierte Modi) - dessen Leg-Gewinn faellt mit
     * dem Engine-Undo automatisch weg.
     *
     * Ablauf (in dieser Reihenfolge): laufenden Abschluss-Job abwarten
     * ([winFinalizeJob]) -> Engine-Undo -> Leg (und beim Match-Sieg auch das
     * Match) wieder oeffnen (`endedAt`/`winnerId` zurueck auf `null`) -> die
     * siegreiche Aufnahme aus der Persistenz und dem Undo-Stapel entfernen ->
     * Eingabe aus dem Engine-Snapshot ableiten. Eine Kontroll-Pause wird dabei
     * bewusst NICHT ausgeloest.
     *
     * Re-Entrancy-Schutz: [undoWinInProgress] wird SYNCHRON (noch vor dem ersten
     * Suspendierungspunkt) gesetzt und erst im `finally` der Coroutine wieder
     * zurueckgesetzt. So laeuft ein zweiter Tap waehrend einer laufenden
     * Rueckdrehung (egal an welcher Suspendierungsstelle) sofort ins No-op - der
     * spaetere `stillWon`-Re-Check auf `_uiState` allein wuerde das nicht
     * abdecken, da dieser Zustand erst am Ende der Coroutine wechselt.
     */
    fun onUndoWin() {
        val state = _uiState.value
        if (state !is GameUiState.LegWon && state !is GameUiState.MatchWon) return
        if (undoWinInProgress) return
        undoWinInProgress = true
        val wasMatchWon = state is GameUiState.MatchWon
        viewModelScope.launch {
            try {
                // Erst den Abschluss zu Ende schreiben lassen, dann zurueckdrehen.
                winFinalizeJob?.join()
                winFinalizeJob = null
                // Zwischenzeitlich weitergespielt (z.B. "Naechstes Leg" gleichzeitig
                // getippt)? Dann ist der Sieg nicht mehr zuruecknehmbar.
                val stillWon = _uiState.value.let {
                    it is GameUiState.LegWon || it is GameUiState.MatchWon
                }
                if (!stillWon) return@launch
                if (!matchEngine.undoLastDart()) return@launch

                // Leg wieder oeffnen; beim Match-Sieg zusaetzlich das Match.
                currentLeg?.let { leg ->
                    val reopened = leg.copy(endedAt = null, winnerId = null)
                    matchRepository.updateLeg(reopened)
                    currentLeg = reopened
                }
                if (wasMatchWon) {
                    match?.let { m ->
                        val reopened = m.copy(endedAt = null, winnerId = null)
                        matchRepository.updateMatch(reopened)
                        match = reopened
                    }
                }

                // Siegreiche Aufnahme zurueckbauen (wie beim Cross-Turn-Undo).
                turnIndex--
                completedTurns.removeLastOrNull()?.let { entry ->
                    // Race Insert-vs-Delete ueber await aufloesen.
                    matchRepository.deleteTurn(entry.turnIdDeferred.await())
                    val prev = completedTurns.lastOrNull { it.playerId == entry.playerId }
                    if (prev != null) {
                        lastTurnByPlayer[entry.playerId] = LastTurn(prev.darts, prev.bust)
                    } else {
                        lastTurnByPlayer.remove(entry.playerId)
                    }
                }

                // Der Sieg-Dart wurde beim Wurf mitgezaehlt -> wieder abziehen.
                val after = matchEngine.snapshot()
                val thrower = after.currentPlayerId
                legDartsByPlayer[thrower]?.let { if (it > 0) legDartsByPlayer[thrower] = it - 1 }

                // Eingabe aus der wieder geoeffneten Aufnahme ableiten.
                val turnDarts = after.playerStates
                    .getOrNull(after.currentPlayerIndex)?.legSnapshot?.turnDarts.orEmpty()
                input = DartInputState(darts = turnDarts)
                _uiState.value = buildPlaying(after, input)
            } finally {
                undoWinInProgress = false
            }
        }
    }

    /**
     * Startet aus dem [GameUiState.LegWon]-Zustand das naechste Leg. Die Engine
     * hat Zaehler und Rotation bereits fortgeschrieben; hier wird der
     * aufgeschobene Leg-Wechsel vollzogen (frische LegEngines, geleerte
     * Undo-Historie - ab jetzt ist der Sieg nicht mehr zuruecknehmbar), das neue
     * [Leg] persistiert und die Eingabe/Indizes zurueckgesetzt.
     *
     * No-op, solange [undoWinInProgress] gesetzt ist: waehrend ein "Sieg
     * zuruecknehmen" noch laeuft (auch ueber Suspendierungspunkte hinweg dank
     * synchron gesetztem Flag), darf kein neues Leg begonnen werden.
     */
    fun onNewLeg() {
        if (_uiState.value !is GameUiState.LegWon) return
        if (undoWinInProgress) return
        val currentMatch = match ?: return
        viewModelScope.launch {
            matchEngine.commitLegTransition()
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
     * Beendet die Kontroll-Pause ("Weiter") und vollzieht den zurueckgehaltenen
     * Spielerwechsel: der Pausen-Timer wird gestoppt, der [pendingSnapshot]
     * angewendet (Eingabe-Reset + neuer Werfer aktiv) und der Pausen-Zustand
     * zurueckgesetzt.
     *
     * Idempotent: laeuft keine Pause ([pendingSnapshot] == null) - etwa weil der
     * Timer und ein "Weiter"-Tap kollidieren oder nach "Korrigieren" -, ist der
     * Aufruf wirkungslos.
     */
    fun onContinue() {
        val snapshot = pendingSnapshot ?: return
        turnReviewJob?.cancel()
        turnReviewJob = null
        pendingSnapshot = null
        input = DartInputState()
        _uiState.value = buildPlaying(snapshot, input)
    }

    /**
     * Wendet eine Eingabe-Transition an. Entstand dabei ein neuer Dart, wird er
     * an die Engine durchgereicht ([onDart]); sonst (Modifier-Toggle oder No-op
     * bei voller Aufnahme) wird nur der Eingabe-Zustand reflektiert.
     */
    private fun onInput(transition: (DartInputState) -> DartInputState) {
        val playing = _uiState.value as? GameUiState.Playing ?: return
        // Waehrend der Kontroll-Pause ist das Keypad ausgeblendet; etwaige
        // Zahl-/Modifier-Eingaben werden ignoriert (kein Dart an den bereits
        // gewechselten naechsten Spieler).
        if (playing.turnReview != null) return
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
     *
     * Ein Leg gilt als entschieden, sobald die Engine einen
     * [com.mechanicel.tomsdarts.game.engine.MatchDartResult.legWinnerId] meldet -
     * bei klassischen Modi ist das der Werfer (Checkout), bei rundenbasierten
     * Modi kann es ein ANDERER Spieler sein (Rangvergleich). Anzeige, Darts-Zahl
     * und Persistenz des Leg-/Match-Abschlusses folgen diesem Gewinner; die
     * abgeschlossene Aufnahme selbst wird immer dem WERFER zugeschrieben.
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
        // Gewinner eines Leg-Endes: bei klassischem Werfer-Sieg (legWon) der
        // Werfer selbst, bei rundenbasiertem Leg-Ende (legEnded) der von der
        // Engine per Rangvergleich ermittelte Spieler. Die Engine liefert ihn in
        // beiden Faellen als legWinnerId; `null` bedeutet "Leg laeuft weiter".
        val legWinnerId = result.legWinnerId
        val winnerId = legWinnerId ?: throwerId
        val winnerDarts = legDartsByPlayer[winnerId]

        if (result.matchWon) {
            val matchWinnerId = result.matchWinnerId ?: winnerId
            _uiState.value = GameUiState.MatchWon(
                players = buildPlayers(result.snapshot),
                matchWinnerName = playerNames[matchWinnerId].orEmpty(),
                dartsUsed = winnerDarts,
            )
            // Die Aufnahme gehoert immer dem WERFER - auch wenn ein anderer
            // Spieler das Leg per Rangvergleich fuer sich entscheidet.
            val deferred = pushWinTurn(legId, throwerId, endedTurnIndex, bust, legSnapshot)
            winFinalizeJob = viewModelScope.launch {
                deferred?.await()
                finishLegAndMatch(matchWinnerId)
            }
            return
        }

        if (legWinnerId != null) {
            // Engine hat die Zaehler/Rotation bereits fortgeschrieben; der Reset
            // der LegEngines folgt erst in [onNewLeg] (commitLegTransition).
            val snapshot = result.snapshot
            _uiState.value = GameUiState.LegWon(
                players = buildPlayers(snapshot),
                legWinnerName = playerNames[legWinnerId].orEmpty(),
                nextStarterName = playerNames[snapshot.currentPlayerId].orEmpty(),
                nextLegNumber = snapshot.currentLegNumber,
                dartsUsed = winnerDarts,
            )
            val deferred = pushWinTurn(legId, throwerId, endedTurnIndex, bust, legSnapshot)
            winFinalizeJob = viewModelScope.launch {
                deferred?.await()
                finishLeg(legWinnerId)
            }
            return
        }

        // Regulaeres oder Bust-Ende: Engine hat den Spieler bereits gewechselt.
        // Die gerade beendete Aufnahme wird als "letzte Aufnahme" des werfenden
        // Spielers (throwerId, vor dem Wechsel ermittelt) gemerkt; das Bust-Flag
        // stammt aus diesem regulaer/Bust-Zweig (result.bust).
        turnIndex++
        lastTurnByPlayer[throwerId] = LastTurn(darts = legSnapshot.turnDarts, bust = bust)

        // Persistenz + Undo-Stapel laufen SOFORT (throw-level, wie bisher) - fuer
        // beide Ausgaenge (Bust und regulaer), unabhaengig von der Pause.
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

        if (bust) {
            // Bust behaelt das bisherige Verhalten: sofortiger Wechsel + Bust-Banner,
            // KEINE Kontroll-Pause.
            input = DartInputState()
            _uiState.value = buildPlaying(result.snapshot, input)
            _bustEvents.update { it + 1 }
            return
        }

        // Regulaeres 3-Dart-Ende: Kontroll-Pause statt sofortigem Wechsel. Der
        // Post-Wechsel-Snapshot wird zurueckgehalten ([pendingSnapshot]) und erst
        // in [onContinue] (bzw. per Timer-Ablauf) angewendet. Waehrend der Pause
        // bleibt die Scoreboard-Darstellung im Kontext des Werfers (kein visueller
        // Sprung), daher wird fuer die Anzeige die Hervorhebung auf den Werfer
        // zurueckgesetzt; das Keypad ist durch den Pausen-Block ersetzt.
        pendingSnapshot = result.snapshot
        val throwerIndex = result.snapshot.playerStates.indexOfFirst { it.playerId == throwerId }
        val throwerContext = result.snapshot.copy(
            currentPlayerIndex = throwerIndex,
            currentPlayerId = throwerId,
        )
        val review = TurnReviewUi(
            throwerName = playerNames[throwerId].orEmpty(),
            darts = legSnapshot.turnDarts,
            turnSum = legSnapshot.turnScored,
            nextPlayerName = playerNames[result.snapshot.currentPlayerId].orEmpty(),
        )
        _uiState.value = buildPlaying(throwerContext, DartInputState(), turnReview = review)
        turnReviewJob?.cancel()
        turnReviewJob = viewModelScope.launch {
            delay(TURN_REVIEW_MILLIS)
            onContinue()
        }
    }

    /**
     * Baut den [GameUiState.Playing] aus einem Match-Snapshot und der Eingabe.
     * [turnReview] ist waehrend der Kontroll-Pause gesetzt (sonst `null`).
     */
    private fun buildPlaying(
        snapshot: MatchSnapshot<S>,
        input: DartInputState,
        turnReview: TurnReviewUi? = null,
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
            turnReview = turnReview,
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

    /**
     * Verbucht die SIEGREICHE Aufnahme (Leg- oder Match-Gewinn) wie eine regulaer
     * beendete: Zug-Index weiterzaehlen, Aufnahme persistieren und auf den
     * Undo-Stapel legen. Nur so loescht [onUndoWin] beim Zuruecknehmen des Sieges
     * exakt den richtigen [Turn].
     *
     * Bewusst OHNE Nachfuehren von [lastTurnByPlayer]: der Sieg-Dart beendet das
     * Leg, die Karten sollen weiterhin die letzte regulaere Aufnahme zeigen.
     *
     * @return Das [Deferred] des Turn-Inserts oder `null`, wenn kein Leg aktiv ist.
     */
    private fun pushWinTurn(
        legId: Long?,
        throwerId: Long,
        endedTurnIndex: Int,
        bust: Boolean,
        legSnapshot: LegEngineSnapshot<S>,
    ): Deferred<Long>? {
        turnIndex++
        if (legId == null) return null
        val deferred = persistTurn(legId, throwerId, endedTurnIndex, bust, legSnapshot)
        completedTurns.addLast(
            CompletedTurn(
                turnIdDeferred = deferred,
                playerId = throwerId,
                darts = legSnapshot.turnDarts,
                bust = bust,
            ),
        )
        return deferred
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
         * Dauer der Kontroll-Pause nach einem regulaeren 3-Dart-Aufnahmeende
         * (Millisekunden). Nach Ablauf wechselt das ViewModel automatisch zum
         * naechsten Spieler ([onContinue]); die UI nutzt denselben Wert fuer die
         * ablaufende Fortschrittsanzeige.
         */
        const val TURN_REVIEW_MILLIS: Long = 1500L

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
                    GameModeCatalog.CRICKET -> GameViewModel(
                        matchRepository = app.container.matchRepository,
                        playerRepository = app.container.playerRepository,
                        playerIds = playerIds,
                        config = config,
                        mode = CricketMode(),
                        uiAdapter = CricketUiAdapter(),
                    )
                    GameModeCatalog.AROUND_THE_CLOCK -> GameViewModel(
                        matchRepository = app.container.matchRepository,
                        playerRepository = app.container.playerRepository,
                        playerIds = playerIds,
                        config = config,
                        mode = AroundTheClockMode(),
                        uiAdapter = AroundTheClockUiAdapter(),
                    )
                    GameModeCatalog.SHANGHAI -> GameViewModel(
                        matchRepository = app.container.matchRepository,
                        playerRepository = app.container.playerRepository,
                        playerIds = playerIds,
                        config = config,
                        mode = ShanghaiMode(),
                        uiAdapter = ShanghaiUiAdapter(),
                    )
                    else -> throw IllegalArgumentException(
                        "Unbekannter Spielmodus: '$modeKey'",
                    )
                }
            }
        }
    }
}
