package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.DartOutcome
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tester-Haertung (Modus-Infrastruktur, PR A) fuer den lesenden Gegner-Parameter
 * von [GameMode.applyDart] und dessen Verdrahtung durch [LegEngine] und
 * [MatchEngine]. Reines JUnit, kein Robolectric, deterministisch.
 *
 * Nutzt ausschliesslich Fake-Modi (kein Produktionscode wird hier verwendet, mit
 * Ausnahme der Engines selbst): [RecordingMode] zeichnet die pro Wurf uebergebene
 * Gegner-Liste auf, [OpponentSumMode] laesst die Wertung selbst von den
 * Gegner-Zustaenden abhaengen, um den kritischsten Pfad (Cross-Turn-Undo-Replay)
 * gegen ein unabhaengiges, engine-freies Referenzmodell ([manualSimulate]) zu
 * pruefen.
 *
 * Schwerpunkte:
 * - Reihenfolge/Anzahl der Gegner-Zustaende (nur die ANDEREN, in aufsteigender
 *   Index-Reihenfolge) bei 2 und 3 Spielern,
 * - Live-Aktualisierung ueber Spielerwechsel hinweg (kein an Konstruktionszeit
 *   eingefrorener Snapshot),
 * - frische Gegner-Zustaende nach einem Leg-Wechsel (neue LegEngines),
 * - Live-Korrektheit waehrend des Cross-Turn-Undo-Replays in [MatchEngine],
 * - Live-Korrektheit waehrend des Intra-Turn-Undo-Replays in [LegEngine].
 */
class MatchEngineOpponentProviderTest {

    private val playerA = 10L
    private val playerB = 20L
    private val playerC = 30L

    /**
     * Fake-Modus, der bei jedem Aufruf die uebergebene Gegner-Liste protokolliert
     * (fuer die Assertions dieser Testklasse) und ansonsten wie ein simpler,
     * nie-bustender Akkumulator wirkt (State = eigener Punktestand). `legWon`
     * greift nur, wenn der neue Stand [GameConfig.startScore] erreicht -
     * dadurch laesst sich ein Leg-Wechsel gezielt herbeifuehren (niedriger
     * Schwellwert), waehrend andere Tests ihn ueber einen hohen Schwellwert
     * bewusst vermeiden.
     */
    private class RecordingMode : GameMode<Int> {
        override val key: String = "RECORDING"
        override val displayName: String = "Recording (Fake)"

        /** Je Aufruf die dabei uebergebene Gegner-Liste, in Aufrufreihenfolge. */
        val callsOpponents: MutableList<List<Int>> = mutableListOf()

        override fun initialState(config: GameConfig): Int = 0

        override fun applyDart(
            state: Int,
            dart: Dart,
            config: GameConfig,
            opponents: List<Int>,
        ): DartOutcome<Int> {
            callsOpponents.add(opponents)
            val newState = state + dart.value
            return DartOutcome(
                newState = newState,
                bust = false,
                legWon = newState >= config.startScore,
                scored = dart.value,
            )
        }
    }

    /**
     * Fake-Modus, dessen Wertung selbst von den Gegner-Zustaenden abhaengt:
     * `newState = state + dart.value + Summe(opponents)`. Bustet und gewinnt nie
     * (hoher [GameConfig.startScore] in den Tests haelt `legWon` konstant false),
     * damit ausschliesslich die Aufnahme-Kadenz (3 Darts) den Spielerwechsel
     * steuert - exakt das, was [manualSimulate] nachbildet.
     */
    private class OpponentSumMode : GameMode<Int> {
        override val key: String = "OPPONENT_SUM"
        override val displayName: String = "Opponent Sum (Fake)"

        override fun initialState(config: GameConfig): Int = 0

        override fun applyDart(
            state: Int,
            dart: Dart,
            config: GameConfig,
            opponents: List<Int>,
        ): DartOutcome<Int> {
            val newState = state + dart.value + opponents.sum()
            return DartOutcome(newState, bust = false, legWon = false, scored = dart.value)
        }
    }

    private fun engine(
        mode: GameMode<Int>,
        startScore: Int = 1_000_000,
        players: List<Long> = listOf(playerA, playerB),
    ): MatchEngine<Int> = MatchEngine(
        mode = mode,
        config = GameConfig(startScore = startScore, legsToWin = 100, setsToWin = 100),
        playerIds = players,
    )

    /**
     * Engine-freies Referenzmodell fuer [OpponentSumMode] unter der exakt
     * gleichen Aufnahme-/Spielerwechsel-Kadenz wie [LegEngine]/[MatchEngine]
     * (3 Darts je Aufnahme, danach naechster Spieler reihum; hier NIE Bust/
     * LegWon). Dient als von der Engine-Implementierung unabhaengige
     * Erwartung, gegen die Undo-/Replay-Ergebnisse geprueft werden.
     */
    private fun manualSimulate(darts: List<Int>, playerCount: Int): List<Int> {
        val states = MutableList(playerCount) { 0 }
        var current = 0
        var dartsInTurn = 0
        for (v in darts) {
            val opponentsSum = states.filterIndexed { i, _ -> i != current }.sum()
            states[current] = states[current] + v + opponentsSum
            dartsInTurn++
            if (dartsInTurn == LegEngine.MAX_DARTS_PER_TURN) {
                dartsInTurn = 0
                current = (current + 1) % playerCount
            }
        }
        return states
    }

    // --- Reihenfolge/Anzahl: nur die ANDEREN Spieler, aufsteigende Index-Reihenfolge ---

    @Test
    fun zweiSpieler_opponentsEnthaeltGenauDenAnderenSpielerNichtSichSelbst() {
        val mode = RecordingMode()
        val e = engine(mode)

        e.applyDart(Dart.single(5))
        // A wirft: Gegner-Liste enthaelt genau B's (unveraenderten) Startzustand.
        assertEquals(listOf(0), mode.callsOpponents.last())
        assertEquals(1, mode.callsOpponents.last().size)

        e.applyDart(Dart.single(5))
        e.applyDart(Dart.single(5)) // A's Aufnahme endet (3 Darts) -> Wechsel zu B.
        mode.callsOpponents.clear()

        e.applyDart(Dart.single(10))
        // B wirft: Gegner-Liste enthaelt A's aktuellen Stand (15), nicht B's eigenen.
        assertEquals(listOf(15), mode.callsOpponents.last())
    }

    @Test
    fun dreiSpieler_opponentsInAufsteigenderIndexReihenfolgeOhneWerfer() {
        val mode = RecordingMode()
        val e = engine(mode, players = listOf(playerA, playerB, playerC))

        // A (Index 0) wirft zuerst: Gegner = [B(idx1), C(idx2)] in dieser Reihenfolge.
        e.applyDart(Dart.single(1))
        assertEquals(listOf(0, 0), mode.callsOpponents.last())
        e.applyDart(Dart.single(1))
        e.applyDart(Dart.single(1)) // A's Stand: 3, Wechsel zu B.
        mode.callsOpponents.clear()

        // B (Index 1) wirft: Gegner = [A(idx0)=3, C(idx2)=0].
        e.applyDart(Dart.single(2))
        assertEquals(listOf(3, 0), mode.callsOpponents.last())
        e.applyDart(Dart.single(2))
        e.applyDart(Dart.single(2)) // B's Stand: 6, Wechsel zu C.
        mode.callsOpponents.clear()

        // C (Index 2) wirft: Gegner = [A(idx0)=3, B(idx1)=6].
        e.applyDart(Dart.single(4))
        assertEquals(listOf(3, 6), mode.callsOpponents.last())
    }

    // --- Live-Aktualisierung ueber Spielerwechsel hinweg (kein eingefrorener Snapshot) ---

    @Test
    fun opponents_aktualisierenSichLiveNachSpielerwechsel_keinEingefrorenerSnapshot() {
        val mode = RecordingMode()
        val e = engine(mode)

        // A's erste Aufnahme: B ist noch bei 0.
        e.applyDart(Dart.single(1))
        e.applyDart(Dart.single(1))
        e.applyDart(Dart.single(1))
        mode.callsOpponents.clear()

        // B's einzige Aufnahme veraendert B's Stand auf 30.
        e.applyDart(Dart.single(10))
        e.applyDart(Dart.single(10))
        e.applyDart(Dart.single(10))
        mode.callsOpponents.clear()

        // A's zweite Aufnahme MUSS B's NEUEN Stand (30) sehen, nicht den Startwert (0).
        e.applyDart(Dart.single(1))
        assertEquals(listOf(30), mode.callsOpponents.last())
    }

    // --- Frische Gegner-Zustaende nach Leg-Wechsel (neue LegEngines) -------------

    @Test
    fun opponents_nachLegWechsel_sindWiederFrischeInitialZustaende() {
        val mode = RecordingMode()
        // Niedriger Schwellwert (5): A gewinnt das Leg direkt mit dem ersten Dart.
        val e = engine(mode, startScore = 5)
        mode.callsOpponents.clear()

        val r = e.applyDart(Dart.single(5))
        assertTrue(r.legWon)
        // Startspieler rotiert: B beginnt Leg 2.
        assertEquals(playerB, e.currentPlayerId)
        mode.callsOpponents.clear()

        e.applyDart(Dart.single(1))
        // B's Gegner-Liste zeigt A's FRISCHEN Zustand (0) der neuen LegEngine,
        // nicht den Endstand (5) aus dem gerade beendeten Leg.
        assertEquals(listOf(0), mode.callsOpponents.last())
    }

    // --- Live-Korrektheit waehrend des Cross-Turn-Undo-Replays (kritischster Pfad) ---

    @Test
    fun opponents_waehrendCrossTurnUndoReplay_liveWerteStattEingefrorenemSnapshot() {
        // 5 Aufnahmen zu je 3 Darts, alternierend A/B/A/B/A - die Wertung jedes
        // Darts haengt vom AKTUELLEN Gegner-Stand ab (OpponentSumMode).
        val darts = listOf(
            5, 5, 5,
            10, 10, 10,
            7, 7, 7,
            3, 3, 3,
            9, 9, 9,
        )
        val e = engine(OpponentSumMode())
        darts.forEach { e.applyDart(Dart.single(it)) }

        val expectedFull = manualSimulate(darts, 2)
        assertEquals(expectedFull[0], e.playerStates[0].state)
        assertEquals(expectedFull[1], e.playerStates[1].state)

        // Zwei volle Aufnahmen (6 Darts) zurueckspulen: erzwingt volle
        // LegEngine-Neuerzeugung + Replay der verbleibenden 9 Darts ueber
        // MatchEngine.applyDartCore.
        repeat(6) { assertTrue(e.undoLastDart()) }
        val remaining = darts.dropLast(6)
        val expectedAfterRewind = manualSimulate(remaining, 2)
        assertEquals(expectedAfterRewind[0], e.playerStates[0].state)
        assertEquals(expectedAfterRewind[1], e.playerStates[1].state)

        // Mit EINER ANDEREN Fortsetzung weiterspielen als der urspruenglich
        // zurueckgenommenen: nur ein Provider, der nach dem Replay wieder LIVE
        // aus den (neuen) LegEngines liest, liefert hier die korrekte Summe -
        // ein an der Undo-Zeitpunkt eingefrorener Snapshot wuerde abweichen.
        val continuation = listOf(2, 2, 2, 4, 4, 4)
        continuation.forEach { e.applyDart(Dart.single(it)) }
        val expectedFinal = manualSimulate(remaining + continuation, 2)
        assertEquals(expectedFinal[0], e.playerStates[0].state)
        assertEquals(expectedFinal[1], e.playerStates[1].state)
    }

    // --- LegEngine-Ebene: opponents()-Provider wird bei jedem Aufruf neu gelesen ---

    @Test
    fun legEngine_applyDart_liestOpponentsProviderBeiJedemWurfNeu() {
        val mode = RecordingMode()
        var opponentsSnapshot = listOf(100)
        val leg = LegEngine(mode, GameConfig(startScore = 1_000_000), opponents = { opponentsSnapshot })

        leg.applyDart(Dart.single(1))
        assertEquals(listOf(100), mode.callsOpponents.last())

        // Provider-Ergebnis aendert sich zwischen zwei Wuerfen - der naechste
        // Aufruf muss den NEUEN Wert sehen (Lambda, kein einmalig gelesener Wert).
        opponentsSnapshot = listOf(200)
        leg.applyDart(Dart.single(1))
        assertEquals(listOf(200), mode.callsOpponents.last())
    }

    @Test
    fun legEngine_undoLastDart_liestOpponentsProviderBeiJedemReplayDartErneut() {
        val mode = RecordingMode()
        var opponentsSnapshot = listOf(1)
        val leg = LegEngine(mode, GameConfig(startScore = 1_000_000), opponents = { opponentsSnapshot })

        leg.applyDart(Dart.single(10))
        leg.applyDart(Dart.single(20))
        mode.callsOpponents.clear()

        // Gegner-Zustand aendert sich NACH dem urspruenglichen Wurf, aber VOR dem
        // Undo-Replay: der Replay-Aufruf fuer den verbleibenden Dart muss den
        // NEUEN Wert lesen (live), nicht den zum urspruenglichen Wurfzeitpunkt.
        opponentsSnapshot = listOf(999)
        assertTrue(leg.undoLastDart())
        assertEquals(listOf(listOf(999)), mode.callsOpponents)
    }
}
