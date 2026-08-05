package com.mechanicel.tomsdarts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test-Gate-Haertung fuer [ShanghaiMode], ergaenzt die Happy-Path-Basistests in
 * [ShanghaiModeTest] um:
 * - scharfe Kanten der Shanghai-Erkennung (alle Reihenfolgen, Fremdsegmente,
 *   Aufnahme-Grenzen, Sudden-Death-Runden),
 * - `legEnded`-Kanten bei mehrfacher Verlaengerung (Sudden Death ueber mehrere
 *   Runden) sowie Gleichstand-Sonderfaelle,
 * - Punktelogik (Multiplikatoren, Bull, `scored` pro Dart) ueber mehrere Runden,
 * - den State-Vertrag "visitHits nach jeder vollen Aufnahme leer" auch fuer die
 *   `legWon`-/`legEnded`-Ausgaenge (dokumentiertes IST-Verhalten).
 *
 * Reines JUnit, kein Robolectric, deterministisch.
 */
class ShanghaiModeEdgeCasesTest {

    private val mode = ShanghaiMode()
    private val config = GameConfig()

    private fun state(dartsThrown: Int, points: Int, hits: Set<Int> = emptySet()): ShanghaiState =
        ShanghaiState(dartsThrown = dartsThrown, points = points, visitHits = hits)

    /** Wirft [darts] nacheinander ab [start], liefert das Outcome des letzten Darts. */
    private fun playVisit(
        darts: List<Dart>,
        start: ShanghaiState = mode.initialState(config),
        opponents: List<ShanghaiState> = emptyList(),
    ): DartOutcome<ShanghaiState> {
        var current = start
        var last: DartOutcome<ShanghaiState>? = null
        darts.forEach { dart ->
            val outcome = mode.applyDart(current, dart, config, opponents)
            current = outcome.newState
            last = outcome
        }
        return requireNotNull(last)
    }

    // === 1. Shanghai-Erkennung scharf ========================================

    @Test
    fun shanghai_alleSechsReihenfolgenVonSingleDoubleTriple_gewinnenSofort() {
        val s = Dart.single(1)
        val d = Dart.double(1)
        val t = Dart.triple(1)
        val orders = listOf(
            listOf(s, d, t), listOf(s, t, d),
            listOf(d, s, t), listOf(d, t, s),
            listOf(t, s, d), listOf(t, d, s),
        )
        orders.forEach { order ->
            val outcome = playVisit(order)
            assertTrue("Reihenfolge $order sollte ein Shanghai sein", outcome.legWon)
            assertEquals(6, outcome.newState.points)
        }
    }

    @Test
    fun keinShanghai_zweiSinglesUndEinDouble_ohneTriple() {
        // S + S + D auf der Zielzahl: alle drei Darts treffen, aber ohne Triple
        // fehlt einer der drei noetigen Multiplikatoren - kein Shanghai (und mit
        // nur 3 Darts pro Aufnahme ist ein "S+S+D+T"-Muster ohnehin unmoeglich).
        val outcome = playVisit(listOf(Dart.single(1), Dart.single(1), Dart.double(1)))
        assertFalse(outcome.legWon)
        assertEquals(4, outcome.newState.points) // 1 + 1 + 2
    }

    @Test
    fun keinShanghai_dreiDoublesDerZielzahl() {
        val outcome = playVisit(listOf(Dart.double(1), Dart.double(1), Dart.double(1)))
        assertFalse(outcome.legWon)
        assertEquals(6, outcome.newState.points)
    }

    @Test
    fun keinShanghai_wennDerDritteDartEinFremdsegmentIst() {
        // Single + Double der Zielzahl, aber der entscheidende 3. Dart daneben.
        val outcome = playVisit(listOf(Dart.single(1), Dart.double(1), Dart.single(20)))
        assertFalse(outcome.legWon)
        assertEquals(3, outcome.newState.points) // 1 + 2 + 0
    }

    @Test
    fun keinShanghai_wennDerErsteDartEinFremdsegmentIst() {
        val outcome = playVisit(listOf(Dart.single(20), Dart.double(1), Dart.triple(1)))
        assertFalse(outcome.legWon)
        assertEquals(5, outcome.newState.points) // 0 + 2 + 3
    }

    @Test
    fun keinShanghai_wennDerMittlereDartMissIst() {
        val outcome = playVisit(listOf(Dart.single(1), Dart.miss(), Dart.triple(1)))
        assertFalse(outcome.legWon)
    }

    @Test
    fun visitHitsResetGreiftUeberAufnahmeGrenzenHinweg_keinShanghaiDurchAltenTreffer() {
        // Aufnahme 1 (Runde 1, Ziel 1): Double der 1, Rest daneben - volle
        // Aufnahme, danach ist die Trefferspur laut Vertrag geleert.
        var current = mode.initialState(config)
        current = mode.applyDart(current, Dart.double(1), config).newState
        current = mode.applyDart(current, Dart.single(20), config).newState
        current = mode.applyDart(current, Dart.single(20), config).newState
        assertTrue(current.visitHits.isEmpty())
        assertEquals(2, current.round)
        assertEquals(2, current.target)

        // Aufnahme 2 (Runde 2, Ziel 2): Single + Triple der 2 - OHNE das Double
        // aus Aufnahme 1 waere das (faelschlich) ein Shanghai; es darf aber nicht
        // nachwirken, da es ausserdem auf einem anderen Ziel lag.
        val outcome = playVisit(listOf(Dart.single(2), Dart.triple(2), Dart.miss()), start = current)
        assertFalse("Das Double aus der vorigen Aufnahme darf nicht nachwirken", outcome.legWon)
    }

    @Test
    fun shanghai_inEinerSuddenDeathRunde_gewinntEbenfalls() {
        // 21 Darts = 7 volle Runden -> Runde 8 (Sudden Death), Ziel zyklisch 1.
        val start = state(dartsThrown = 21, points = 84)
        assertEquals(8, start.round)
        assertEquals(1, start.target)

        val outcome = playVisit(listOf(Dart.triple(1), Dart.single(1), Dart.double(1)), start = start)
        assertTrue(outcome.legWon)
        assertEquals(90, outcome.newState.points) // 84 + 3 + 1 + 2
    }

    // === 2. legEnded-Kanten ===================================================

    @Test
    fun suddenDeath_gleichstandInRundeAcht_bleibtOffen_rundeNeunLaeuft() {
        // Runde 8 (Ziel 1): 37 + Triple(1)=3 -> 40, der Gegner ist bereits mit
        // 40 Punkten durch Runde 8 -> Gleichstand an der Spitze, kein legEnded.
        val opponent = state(dartsThrown = 24, points = 40)
        val outcome = mode.applyDart(
            state(dartsThrown = 23, points = 37),
            Dart.triple(1),
            config,
            opponents = listOf(opponent),
        )
        assertFalse("Gleichstand in Runde 8 entscheidet nicht", outcome.legEnded)
        assertEquals(40, outcome.newState.points)
        assertEquals(9, outcome.newState.round)
        assertEquals(2, outcome.newState.target)
    }

    @Test
    fun suddenDeath_zweiAufeinanderfolgendeGleichstandsRunden_bleibenBeideOffen() {
        // Runde 8 (Ziel 1): erneuter Gleichstand bei 40:40.
        val opponentRound8 = state(dartsThrown = 24, points = 40)
        val round8 = mode.applyDart(
            state(dartsThrown = 23, points = 37),
            Dart.triple(1),
            config,
            opponents = listOf(opponentRound8),
        )
        assertFalse(round8.legEnded)
        assertEquals(40, round8.newState.points)

        // Runde 9 (Ziel 2): 37 + Triple(2)=6 -> 43, Gegner ebenfalls bei 43 ->
        // die Verlaengerung geht in eine weitere Runde.
        val opponentRound9 = state(dartsThrown = 27, points = 43)
        val round9 = mode.applyDart(
            state(dartsThrown = 26, points = 37),
            Dart.triple(2),
            config,
            opponents = listOf(opponentRound9),
        )
        assertFalse("Gleichstand haelt auch in Runde 9", round9.legEnded)
        assertEquals(43, round9.newState.points)
        assertEquals(10, round9.newState.round)
        assertEquals(3, round9.newState.target)
    }

    @Test
    fun suddenDeath_entscheidungFaelltErstNachMehrerenVerlaengerungsrunden() {
        // Fortsetzung des obigen Szenarios: Runde 10 (Ziel 3) - der Gegner bleibt
        // bei 43 (verfehlt alles), der Werfer zieht mit Triple(3)=9 auf 52 davon
        // -> nach zwei Verlaengerungsrunden faellt endlich die Entscheidung.
        val opponentRound10 = state(dartsThrown = 30, points = 43)
        val outcome = mode.applyDart(
            state(dartsThrown = 29, points = 43),
            Dart.triple(3),
            config,
            opponents = listOf(opponentRound10),
        )
        assertTrue("Nach mehreren Verlaengerungsrunden faellt die Entscheidung", outcome.legEnded)
        assertFalse(outcome.legWon)
        assertEquals(52, outcome.newState.points)
    }

    @Test
    fun legEnded_gleichstandNurZwischenNichtFuehrenden_eindeutigeSpitzeEntscheidetTrotzdem() {
        // Zwei Gegner mit exakt gleichem (niedrigerem) Punktestand - der Werfer
        // fuehrt klar. Der Gleichstand der beiden Verlierer darf legEnded nicht
        // verhindern: es gibt trotzdem einen eindeutigen Fuehrenden (den Werfer).
        val opponentA = state(dartsThrown = 21, points = 50)
        val opponentB = state(dartsThrown = 21, points = 50)
        val outcome = mode.applyDart(
            state(dartsThrown = 20, points = 90),
            Dart.single(7),
            config,
            opponents = listOf(opponentA, opponentB),
        )
        assertTrue(outcome.legEnded)
        assertEquals(97, outcome.newState.points)
    }

    @Test
    fun legWon_hatVorrangVorLegEnded_auchWennDerWerferSelbstDerEindeutigeFuehrendeWaere() {
        // Letzter Werfer der 7. Runde, fuehrt bereits klar vor dem letzten Dart -
        // ohne Shanghai waere das ein glasklares legEnded fuer den Werfer selbst
        // (der genau HIER, am kritischsten moeglichen Dart, entscheiden wuerde).
        // Stattdessen vollendet er ein Shanghai -> legWon (Sofort-Sieg), NICHT
        // legEnded.
        val opponent = state(dartsThrown = 21, points = 10)
        val outcome = mode.applyDart(
            state(dartsThrown = 20, points = 50, hits = setOf(1, 2)),
            Dart.triple(7),
            config,
            opponents = listOf(opponent),
        )
        assertTrue(outcome.legWon)
        assertFalse(outcome.legEnded)
        assertEquals(71, outcome.newState.points)
    }

    // === 3. Punktelogik =======================================================

    @Test
    fun zieltreffer_alleMultiplikatoren_akkumulierenUeberMehrereRunden() {
        var current = mode.initialState(config)
        var expected = 0
        // Runde 1 (Ziel 1): Single, Runde 2 (Ziel 2): Double, Runde 3 (Ziel 3):
        // Triple - je restliche Darts der Aufnahme daneben.
        val plan = listOf(
            Dart.single(1) to 1, Dart.miss() to 0, Dart.miss() to 0,
            Dart.double(2) to 4, Dart.miss() to 0, Dart.miss() to 0,
            Dart.triple(3) to 9, Dart.miss() to 0, Dart.miss() to 0,
        )
        plan.forEach { (dart, expectedScore) ->
            val outcome = mode.applyDart(current, dart, config)
            assertEquals("$dart", expectedScore, outcome.scored)
            expected += expectedScore
            assertEquals(expected, outcome.newState.points)
            current = outcome.newState
        }
        assertEquals(14, current.points) // 1 + 4 + 9
        assertEquals(4, current.round)
    }

    @Test
    fun bull_punktetNieAlsZiel_wederSingleNochDoppelBull_inKeinerRegulaerenRunde() {
        // Ueber alle regulaeren Runden (1..7) hinweg: Bull/Doppel-Bull treffen nie
        // die Zielzahl (1..7 != 25) und werten daher immer 0.
        for (round in 1..ShanghaiState.ROUNDS) {
            val dartsThrown = (round - 1) * ShanghaiState.DARTS_PER_ROUND
            val current = state(dartsThrown = dartsThrown, points = 0)
            assertEquals("Runde $round", round, current.round)
            assertEquals("Runde $round: Bull", 0, mode.applyDart(current, Dart.bull(), config).scored)
            assertEquals(
                "Runde $round: Doppel-Bull",
                0,
                mode.applyDart(current, Dart.doubleBull(), config).scored,
            )
        }
    }

    @Test
    fun scored_entsprichtExaktZielMalMultiplier_proDart() {
        val start = state(dartsThrown = 15, points = 100) // Runde 6, Ziel 6
        assertEquals(6, start.round)
        assertEquals(6, start.target)
        assertEquals(6, mode.applyDart(start, Dart.single(6), config).scored)
        assertEquals(12, mode.applyDart(start, Dart.double(6), config).scored)
        assertEquals(18, mode.applyDart(start, Dart.triple(6), config).scored)
        assertEquals("Fremdsegment", 0, mode.applyDart(start, Dart.single(5), config).scored)
        assertEquals("Fremdsegment", 0, mode.applyDart(start, Dart.triple(20), config).scored)
        assertEquals("Miss", 0, mode.applyDart(start, Dart.miss(), config).scored)
    }

    // === 4. State-Vertrag: visitHits-Reset auch bei legWon/legEnded ==========

    @Test
    fun legWon_raeumtDieTrefferspurTrotzdemAuf_undZeigtBereitsDieNaechsteRunde() {
        // Dokumentiertes IST-Verhalten: obwohl das Leg mit diesem Dart gewonnen
        // ist, beschreibt der resultierende Zustand bereits "Runde 2" mit leerer
        // Trefferspur - der State-Vertrag (nach jeder vollen Aufnahme leer) gilt
        // ausnahmslos, auch beim Sieg-Dart selbst.
        var current = mode.initialState(config)
        current = mode.applyDart(current, Dart.single(1), config).newState
        current = mode.applyDart(current, Dart.double(1), config).newState
        val outcome = mode.applyDart(current, Dart.triple(1), config)

        assertTrue(outcome.legWon)
        assertTrue("visitHits trotz Sieg geleert", outcome.newState.visitHits.isEmpty())
        assertEquals(2, outcome.newState.round)
    }

    @Test
    fun legEnded_raeumtDieTrefferspurEbenfallsAuf() {
        val opponent = state(dartsThrown = 21, points = 10)
        val outcome = mode.applyDart(
            state(dartsThrown = 20, points = 50, hits = setOf(3)),
            Dart.single(7),
            config,
            opponents = listOf(opponent),
        )
        assertTrue(outcome.legEnded)
        assertTrue("visitHits trotz Leg-Ende geleert", outcome.newState.visitHits.isEmpty())
        assertEquals(8, outcome.newState.round)
    }
}
