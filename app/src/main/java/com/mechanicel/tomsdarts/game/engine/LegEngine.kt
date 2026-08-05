package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.GameMode

/**
 * Verarbeitet Dart-Wuerfe zu Spiel-Logik fuer EINEN Spieler in EINEM Leg auf
 * Basis einer austauschbaren [GameMode]-Strategie.
 *
 * Verantwortlichkeiten (bewusst eng geschnitten):
 * - Aufnahme-Verwaltung: buendelt Darts zu Aufnahmen von maximal 3 Darts.
 * - Bust-Revert: bei `bust` werden die in der Aufnahme geworfenen Darts fuer die
 *   Wertung verworfen und der Modus-Zustand auf den Aufnahme-Startzustand
 *   zurueckgesetzt; die Aufnahme endet.
 * - Sofort-Checkout: bei `legWon` endet die Aufnahme sofort, auch bei < 3 Darts.
 * - Sofort-Leg-Ende: bei `legEnded` (Leg entschieden, Gewinner erst per
 *   Rangvergleich der MatchEngine) endet die Aufnahme genauso sofort und weitere
 *   Darts sind gesperrt - nur ohne Aussage darueber, wer gewonnen hat.
 *
 * NICHT Aufgabe dieser Engine (folgt in spaeteren Aufgaben): Spielerwechsel /
 * Mehrspieler, Aggregation von Legs/Sets, UI und Persistenz. Die Engine ist
 * modus-agnostisch ueber [GameMode] und reine Domaenenlogik: kein Android-/
 * Room-/Compose-Bezug, mit reinem JUnit testbar.
 *
 * Lebenszyklus einer Aufnahme: Der Aufrufer ruft [applyDart] fuer jeden Dart.
 * Sobald [DartResult.turnEnded] meldet, dass die Aufnahme abgeschlossen ist,
 * beginnt der Aufrufer mit [startNewTurn] die naechste Aufnahme. Vor diesem
 * Aufruf weitere [applyDart] sind No-ops (siehe dort).
 *
 * @param S Modus-spezifischer Spielerzustand (z.B. [com.mechanicel.tomsdarts.game.X01State]).
 * @param mode Die Modus-Strategie, die genau einen Dart wertet.
 * @param config Die Regel-Konfiguration des Matches.
 * @param opponents Provider fuer die Zustaende der MITSPIELER (ohne diesen
 *   Spieler), die an [GameMode.applyDart] durchgereicht werden. Bewusst ein
 *   Lambda statt einer festen Liste, weil sich die Gegner-Zustaende im
 *   Leg-Verlauf aendern und bei jedem Wurf frisch (live) gelesen werden muessen.
 *   Der Default liefert die leere Liste (Modi ohne Gegnerbezug wie X01, sowie
 *   Direkt-Tests der Einzel-Engine).
 * @param playerIndex 0-basierter Sitzplatz des Spielers, fuer den diese Engine
 *   laeuft. Wird an [GameMode.initialState] durchgereicht, damit Modi mit
 *   per-Spieler-Identitaet (z.B. Killer: eigene Zielzahl je Spieler) ihren
 *   Startzustand vom Sitzplatz ableiten koennen. Der Default `0` haelt alle
 *   bestehenden Aufrufer (und Direkt-Tests) quellcode-kompatibel; Modi ohne
 *   Identitaet ignorieren den Index ueber den Interface-Default.
 */
class LegEngine<S : Any>(
    private val mode: GameMode<S>,
    private val config: GameConfig,
    private val opponents: () -> List<S> = { emptyList() },
    private val playerIndex: Int = 0,
) {

    private var currentState: S = mode.initialState(config, playerIndex)
    private var turnStart: S = currentState
    private val currentTurnDarts: MutableList<Dart> = mutableListOf()
    private var turnScored: Int = 0
    private var turnBust: Boolean = false
    private var turnEnded: Boolean = false
    private var legWon: Boolean = false
    private var legEnded: Boolean = false

    /** Aktueller, fuer die Wertung gueltiger Modus-Zustand (bei Bust zurueckgesetzt). */
    val state: S get() = currentState

    /** Anzahl der bisher in der aktuellen Aufnahme geworfenen Darts (0..3). */
    val dartsInTurn: Int get() = currentTurnDarts.size

    /** True, wenn das Leg von DIESEM Spieler gewonnen wurde. */
    val isLegWon: Boolean get() = legWon

    /**
     * True, wenn das Leg mit einem Dart DIESES Spielers entschieden wurde, ohne
     * dass er es zwingend gewinnt (Rangvergleich in der MatchEngine).
     */
    val isLegEnded: Boolean get() = legEnded

    /**
     * True, wenn das Leg fuer diese Engine abgeschlossen ist - egal ob durch
     * Werfer-Sieg ([isLegWon]) oder Rangvergleich-Ende ([isLegEnded]). Sperrt
     * weitere Darts und neue Aufnahmen.
     */
    private val legClosed: Boolean get() = legWon || legEnded

    /**
     * True, wenn die aktuelle Aufnahme abgeschlossen ist (3 Darts, Bust,
     * Leg-Gewinn oder Leg-Ende) und vor weiteren Wuerfen [startNewTurn] noetig
     * ist.
     */
    val isTurnEnded: Boolean get() = turnEnded

    /** Liefert den aktuellen Engine-Zustand als unveraenderliche Momentaufnahme. */
    fun snapshot(): LegEngineSnapshot<S> = LegEngineSnapshot(
        state = currentState,
        turnStartState = turnStart,
        turnDarts = currentTurnDarts.toList(),
        dartsInTurn = currentTurnDarts.size,
        turnScored = turnScored,
        turnBust = turnBust,
        isTurnEnded = turnEnded,
        isLegWon = legWon,
    )

    /**
     * Verarbeitet GENAU EINEN Dart.
     *
     * No-op (kein Crash): Ist das Leg bereits abgeschlossen (Werfer-Sieg ODER
     * Rangvergleich-Ende) oder die aktuelle Aufnahme bereits beendet (3 Darts,
     * ohne vorheriges [startNewTurn]), bleibt der Zustand unveraendert und es wird
     * ein [DartResult] mit `accepted == false`, `outcome == null`,
     * `dartIndex == -1` zurueckgegeben.
     *
     * Andernfalls wird `mode.applyDart` ausgewertet und:
     * - bei `bust`: Modus-Zustand auf [turnStart] zurueckgesetzt (Wertung der
     *   Aufnahme verworfen), die Aufnahme endet; der ueberwerfende Dart bleibt in
     *   [snapshot]`.turnDarts` fuer die Persistenz erhalten.
     * - bei `legWon`: Modus-Zustand = `outcome.newState`, Leg gewonnen, Aufnahme
     *   endet sofort (auch bei < 3 Darts).
     * - bei `legEnded`: Modus-Zustand = `outcome.newState`, Leg entschieden
     *   (Gewinner offen, siehe [isLegEnded]), Aufnahme endet ebenfalls sofort.
     * - regulaer: Modus-Zustand = `outcome.newState`, Dart der Aufnahme
     *   hinzugefuegt; bei Erreichen von [MAX_DARTS_PER_TURN] endet die Aufnahme.
     */
    fun applyDart(dart: Dart): DartResult<S> {
        if (legClosed || turnEnded) {
            return DartResult(
                accepted = false,
                outcome = null,
                dartIndex = -1,
                scored = 0,
                totalScored = turnScored,
                turnEnded = turnEnded,
                bust = turnBust,
                legWon = legWon,
                legEnded = legEnded,
                snapshot = snapshot(),
            )
        }

        val outcome = mode.applyDart(currentState, dart, config, opponents())
        val dartIndex = currentTurnDarts.size
        currentTurnDarts.add(dart)

        when {
            outcome.bust -> {
                currentState = turnStart
                turnScored = 0
                turnBust = true
                turnEnded = true
            }

            outcome.legWon -> {
                currentState = outcome.newState
                turnScored += outcome.scored
                legWon = true
                turnEnded = true
            }

            outcome.legEnded -> {
                // Leg entschieden, aber NICHT zwingend zugunsten dieses Spielers:
                // Wertung wie ein regulaerer Dart, danach ist das Leg fuer diese
                // Engine geschlossen (weitere Darts werden abgelehnt). Wer gewinnt,
                // entscheidet die MatchEngine per Rangvergleich.
                currentState = outcome.newState
                turnScored += outcome.scored
                legEnded = true
                turnEnded = true
            }

            else -> {
                currentState = outcome.newState
                turnScored += outcome.scored
                if (currentTurnDarts.size >= MAX_DARTS_PER_TURN) {
                    turnEnded = true
                }
            }
        }

        return DartResult(
            accepted = true,
            outcome = outcome,
            dartIndex = dartIndex,
            scored = if (outcome.bust) 0 else outcome.scored,
            totalScored = turnScored,
            turnEnded = turnEnded,
            bust = outcome.bust,
            legWon = outcome.legWon,
            legEnded = outcome.legEnded,
            snapshot = snapshot(),
        )
    }

    /**
     * Beginnt die naechste Aufnahme: der Aufnahme-Startzustand wird auf den
     * aktuellen Modus-Zustand gesetzt und die Aufnahme-Daten zurueckgesetzt.
     *
     * No-op (Rueckgabe `false`), wenn das Leg bereits abgeschlossen ist (Sieg des
     * Spielers ODER Rangvergleich-Ende); sonst `true`.
     */
    fun startNewTurn(): Boolean {
        if (legClosed) return false
        turnStart = currentState
        currentTurnDarts.clear()
        turnScored = 0
        turnBust = false
        turnEnded = false
        return true
    }

    /**
     * Macht den zuletzt geworfenen Dart der LAUFENDEN, noch nicht beendeten
     * Aufnahme rueckgaengig: der Dart wird aus der Aufnahme entfernt und der
     * Modus-Zustand durch Replay der verbleibenden Darts ab [turnStart] exakt
     * wiederhergestellt (Zustand zurueck, [dartsInTurn] - 1).
     *
     * No-op (Rueckgabe `false`), wenn die Aufnahme leer ist ODER bereits beendet
     * wurde ([isTurnEnded] bzw. [isLegWon]/[isLegEnded]). Da eine Aufnahme bei
     * Bust, Leg-Gewinn oder Leg-Ende sofort endet, sind alle verbleibenden Darts
     * zwangslaeufig regulaer; das Replay reproduziert daher denselben Zustand.
     */
    fun undoLastDart(): Boolean {
        if (turnEnded || legClosed) return false
        if (currentTurnDarts.isEmpty()) return false

        currentTurnDarts.removeAt(currentTurnDarts.size - 1)
        currentState = turnStart
        turnScored = 0
        for (dart in currentTurnDarts) {
            val outcome = mode.applyDart(currentState, dart, config, opponents())
            currentState = outcome.newState
            turnScored += outcome.scored
        }
        return true
    }

    companion object {
        /** Maximale Anzahl Darts pro Aufnahme. */
        const val MAX_DARTS_PER_TURN: Int = 3
    }
}
