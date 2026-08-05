package com.mechanicel.tomsdarts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test-Gate-Haertung fuer [CountUpMode], ergaenzt die Happy-Path-Basistests in
 * [CountUpModeTest] um:
 * - Punkte-Vollstaendigkeit (gemischte Wurfarten in einer Aufnahme, ein voller
 *   Acht-Runden-Verlauf mit handgerechneter Summe, kein Sofort-Sieg trotz
 *   fuehrender/bereits fertiger Gegner),
 * - `legEnded`-Kanten bei mehrfacher Verlaengerung (Gleichstand-Ketten ueber
 *   mehrere Sudden-Death-Runden), 3+ Spielern mit einem Fuehrenden ungleich dem
 *   Werfer, einem Gleichstand nur unter Nicht-Fuehrenden sowie einem riesigen
 *   Vorsprung vor Runde 8,
 * - die Flag-Invariante (`bust`/`legWon` immer false) ueber eine deterministische,
 *   ueber viele Runden und Wurfarten hinweg gestreute Sequenz.
 *
 * Reines JUnit, kein Robolectric, deterministisch (keine echten Zufallszahlen -
 * im Projekt nicht ueblich, siehe [flagInvariante_ueberVieleUnterschiedlicheWurfarten_bustUndLegWonBleibenImmerFalse]).
 */
class CountUpModeEdgeCasesTest {

    private val mode = CountUpMode()
    private val config = GameConfig()

    /** Kurzschreibweise fuer einen Spielerzustand mitten im Leg. */
    private fun state(dartsThrown: Int, points: Int): CountUpState =
        CountUpState(dartsThrown = dartsThrown, points = points)

    // === 1. Punkte-Vollstaendigkeit ==========================================

    @Test
    fun gemischteAufnahme_akkumuliertExakteSummeAusAllenWurfarten() {
        // Aufnahme 1: Single(18), Double(9)=18, Triple(4)=12 -> 48.
        // Aufnahme 2: Bull=25, Doppel-Bull=50, Miss=0 -> 75.
        var current = mode.initialState(config)
        val plan = listOf(
            Dart.single(18) to 18,
            Dart.double(9) to 18,
            Dart.triple(4) to 12,
            Dart.bull() to 25,
            Dart.doubleBull() to 50,
            Dart.miss() to 0,
        )
        var expected = 0
        plan.forEachIndexed { index, (dart, expectedScore) ->
            val outcome = mode.applyDart(current, dart, config)
            assertEquals("Dart ${index + 1}: $dart", expectedScore, outcome.scored)
            expected += expectedScore
            assertEquals(expected, outcome.newState.points)
            assertEquals(index + 1, outcome.newState.dartsThrown)
            current = outcome.newState
        }
        assertEquals(123, current.points) // 18 + 18 + 12 + 25 + 50 + 0
        assertEquals(3, current.round) // 6 Darts = 2 volle Aufnahmen -> Runde 3
    }

    @Test
    fun einhundertachtzigerAufnahme_bleibtOhneLegWon_auchWennEinGegnerBereitsWeitFuehrtUndFertigIst() {
        // Ein Gegner hat die 8 Runden bereits fertig und fuehrt klar (600 Punkte);
        // der Werfer steht noch in Runde 1. Auch eine Traum-Aufnahme (3xT20=180)
        // darf hier NICHT sofort gewinnen - Count Up kennt keinen Sofort-Sieg,
        // unabhaengig vom Punktestand der Gegner.
        val opponent = state(dartsThrown = 24, points = 600)
        var current = mode.initialState(config)
        repeat(CountUpState.DARTS_PER_ROUND) {
            val outcome = mode.applyDart(current, Dart.triple(20), config, listOf(opponent))
            assertFalse(outcome.legWon)
            assertFalse(outcome.bust)
            assertFalse("Der Werfer hat Runde 8 noch nicht erreicht", outcome.legEnded)
            current = outcome.newState
        }
        assertEquals(180, current.points)
    }

    @Test
    fun akkumulation_exaktUeberAchtRundenMitUnterschiedlichenAufnahmenProRunde_handgerechnet() {
        // Acht UNTERSCHIEDLICHE Aufnahmen (kein gleichfoermiges Muster ueber alle
        // Wurfarten), Summe von Hand nachgerechnet. Ein zurueckhaengender Gegner
        // verhindert jedes Rundenende, damit dieser Test rein die Akkumulation
        // (nicht legEnded) prueft - Muster wie CountUpModeTest.rundenFortschritt_folgtDenGeworfenenDarts.
        val laggingOpponent = listOf(CountUpState.initial())
        val rounds = listOf(
            listOf(Dart.triple(20), Dart.triple(20), Dart.triple(20)) to 180,
            listOf(Dart.double(19), Dart.double(19), Dart.double(19)) to 114,
            listOf(Dart.single(18), Dart.single(18), Dart.single(18)) to 54,
            listOf(Dart.bull(), Dart.bull(), Dart.bull()) to 75,
            listOf(Dart.doubleBull(), Dart.doubleBull(), Dart.doubleBull()) to 150,
            listOf(Dart.miss(), Dart.miss(), Dart.miss()) to 0,
            listOf(Dart.triple(20), Dart.single(1), Dart.miss()) to 61,
            listOf(Dart.double(5), Dart.triple(5), Dart.single(5)) to 30,
        )
        var current = mode.initialState(config)
        var expectedTotal = 0
        rounds.forEachIndexed { index, (dartsInRound, expectedRoundSum) ->
            dartsInRound.forEach { dart ->
                val outcome = mode.applyDart(current, dart, config, laggingOpponent)
                current = outcome.newState
            }
            expectedTotal += expectedRoundSum
            assertEquals("Nach Runde ${index + 1}", expectedTotal, current.points)
        }
        assertEquals(664, current.points) // 180+114+54+75+150+0+61+30
        assertEquals(24, current.dartsThrown)
        assertEquals(8, current.completedRounds)
        assertEquals(9, current.round)
    }

    // === 2. legEnded-Kanten ===================================================

    @Test
    fun suddenDeath_gleichstandKette_ueberMehrereVerlaengerungsrunden_bisZurEntscheidung() {
        // Runde 8: 300:300 Gleichstand -> kein legEnded, Runde 9 laeuft.
        val opponentRound8 = state(dartsThrown = 24, points = 300)
        val round8 = mode.applyDart(
            state(dartsThrown = 23, points = 280),
            Dart.single(20),
            config,
            opponents = listOf(opponentRound8),
        )
        assertFalse(round8.legEnded)
        assertEquals(300, round8.newState.points)
        assertEquals(9, round8.newState.round)

        // Runde 9: erneuter Gleichstand bei 320:320 - die Verlaengerung haelt.
        val opponentRound9 = state(dartsThrown = 27, points = 320)
        val round9 = mode.applyDart(
            state(dartsThrown = 26, points = 300),
            Dart.single(20),
            config,
            opponents = listOf(opponentRound9),
        )
        assertFalse("Gleichstand haelt auch in Runde 9", round9.legEnded)
        assertEquals(320, round9.newState.points)
        assertEquals(10, round9.newState.round)

        // Runde 10: der Werfer zieht davon -> nach zwei Verlaengerungsrunden
        // faellt endlich die Entscheidung.
        val opponentRound10 = state(dartsThrown = 30, points = 320)
        val round10 = mode.applyDart(
            state(dartsThrown = 29, points = 320),
            Dart.triple(20),
            config,
            opponents = listOf(opponentRound10),
        )
        assertTrue("Nach zwei Verlaengerungsrunden faellt die Entscheidung", round10.legEnded)
        assertFalse(round10.legWon)
        assertEquals(380, round10.newState.points) // 320 + 60
    }

    @Test
    fun legEnded_dreiSpielerAlsGegner_derFuehrendeMussNichtDerWerferSein() {
        // Der Werfer selbst hat wenig Punkte; ein Gegner fuehrt klar, ein
        // anderer liegt dazwischen. Der Werfer beendet trotzdem als Letzter die
        // Runde 8 -> legEnded, obwohl er selbst NICHT der Fuehrende ist.
        val leadingOpponent = state(dartsThrown = 24, points = 500)
        val middleOpponent = state(dartsThrown = 24, points = 250)
        val outcome = mode.applyDart(
            state(dartsThrown = 23, points = 200),
            Dart.single(20),
            config,
            opponents = listOf(leadingOpponent, middleOpponent),
        )
        assertTrue(outcome.legEnded)
        assertFalse(outcome.legWon)
        assertEquals(220, outcome.newState.points)
        assertTrue(
            "Der fuehrende Gegner hat den hoechsten Rangwert",
            mode.legScore(leadingOpponent) > mode.legScore(outcome.newState),
        )
    }

    @Test
    fun legEnded_gleichstandNurUnterNichtFuehrendenGegnern_eindeutigeSpitzeEntscheidetTrotzdem() {
        // Zwei Gegner mit exakt gleichem (niedrigerem) Punktestand - der Werfer
        // fuehrt klar. Der Gleichstand der beiden Verlierer darf legEnded nicht
        // verhindern: es gibt trotzdem einen eindeutigen Fuehrenden (den Werfer).
        val opponentA = state(dartsThrown = 24, points = 150)
        val opponentB = state(dartsThrown = 24, points = 150)
        val outcome = mode.applyDart(
            state(dartsThrown = 23, points = 380),
            Dart.single(20),
            config,
            opponents = listOf(opponentA, opponentB),
        )
        assertTrue(outcome.legEnded)
        assertEquals(400, outcome.newState.points)
    }

    @Test
    fun keinLegEnded_vorDerAchtenRunde_auchBeiRiesigemVorsprung() {
        // Runde 7: der Werfer fuehrt bereits massiv (7 volle Runden a 180 =
        // theoretisches Maximum), der Gegner steht bei 0. Trotzdem entscheidet
        // der Punktvergleich erst ab Runde 8 - unabhaengig von der Groesse des
        // Vorsprungs.
        val opponent = state(dartsThrown = 21, points = 0)
        val outcome = mode.applyDart(
            state(dartsThrown = 20, points = 1260), // 7 x 180
            Dart.triple(20),
            config,
            opponents = listOf(opponent),
        )
        assertEquals(7, outcome.newState.completedRounds)
        assertFalse("Vor Runde 8 entscheidet der Punktvergleich noch nicht", outcome.legEnded)
        assertEquals(1320, outcome.newState.points)
    }

    // === 3. Flag-Invariante ====================================================

    @Test
    fun flagInvariante_ueberVieleUnterschiedlicheWurfarten_bustUndLegWonBleibenImmerFalse() {
        // Der Werfer nutzt in jeder Runde eine ANDERE Wurfart (Single/Double/
        // Triple auf wechselnden Segmenten, Bull, Doppel-Bull, Miss gemischt);
        // der Gegner verfehlt durchgehend komplett. Deterministische Sequenz
        // (keine echten Zufallszahlen - im Projekt nicht ueblich) ueber 15
        // Runden, deutlich ueber die 8 regulaeren Runden hinaus in die
        // Verlaengerung. Da der Werfer ab Runde 1 stets mehr als 0 Punkte hat
        // und der Gegner immer bei 0 bleibt, ist der Werfer ab Runde 8
        // garantiert eindeutiger Fuehrender - legEnded muss also spaetestens
        // dann (aus Sicht des Gegners, der jede Runde zuletzt wirft) auftreten.
        val visitPlans: List<List<Dart>> = listOf(
            listOf(Dart.single(1), Dart.single(2), Dart.single(3)),
            listOf(Dart.double(4), Dart.double(5), Dart.double(6)),
            listOf(Dart.triple(7), Dart.triple(8), Dart.triple(9)),
            listOf(Dart.bull(), Dart.doubleBull(), Dart.miss()),
            listOf(Dart.single(20), Dart.double(20), Dart.triple(20)),
            listOf(Dart.miss(), Dart.miss(), Dart.single(10)),
            listOf(Dart.triple(11), Dart.single(12), Dart.double(13)),
            listOf(Dart.bull(), Dart.bull(), Dart.doubleBull()),
            listOf(Dart.single(14), Dart.triple(15), Dart.miss()),
            listOf(Dart.double(16), Dart.single(17), Dart.triple(18)),
            listOf(Dart.miss(), Dart.doubleBull(), Dart.single(19)),
            listOf(Dart.triple(20), Dart.triple(19), Dart.triple(18)),
            listOf(Dart.single(1), Dart.double(2), Dart.triple(3)),
            listOf(Dart.bull(), Dart.miss(), Dart.single(20)),
            listOf(Dart.double(20), Dart.triple(20), Dart.bull()),
        )
        assertEquals(15, visitPlans.size)

        var thrower = mode.initialState(config)
        var opponent = mode.initialState(config)
        var legEndedSeen = false
        visitPlans.forEachIndexed { roundIndex, plan ->
            plan.forEach { dart ->
                val outcome = mode.applyDart(thrower, dart, config, listOf(opponent))
                assertFalse("bust bei Count Up immer false (Runde ${roundIndex + 1})", outcome.bust)
                assertFalse("legWon bei Count Up immer false (Runde ${roundIndex + 1})", outcome.legWon)
                assertFalse(outcome.bust && outcome.legEnded)
                assertFalse(outcome.legWon && outcome.legEnded)
                if (outcome.legEnded) legEndedSeen = true
                thrower = outcome.newState
            }
            // Gegner wirft synchron eine harmlose Aufnahme (nur Fehlwuerfe),
            // damit die Rundenzahlen zwischen Werfer und Gegner gleich bleiben.
            repeat(CountUpState.DARTS_PER_ROUND) {
                val opponentOutcome = mode.applyDart(opponent, Dart.miss(), config, listOf(thrower))
                assertFalse(opponentOutcome.bust)
                assertFalse(opponentOutcome.legWon)
                assertFalse(opponentOutcome.bust && opponentOutcome.legEnded)
                assertFalse(opponentOutcome.legWon && opponentOutcome.legEnded)
                if (opponentOutcome.legEnded) legEndedSeen = true
                opponent = opponentOutcome.newState
            }
        }
        assertTrue(
            "Der Werfer fuehrt spaetestens ab Runde 8 eindeutig (Gegner verfehlt durchgehend) -> legEnded muss auftreten",
            legEndedSeen,
        )
        assertTrue(
            "Der Werfer hat durchgehend mehr Punkte als der ausschliesslich verfehlende Gegner",
            thrower.points > opponent.points,
        )
        assertEquals(0, opponent.points)
    }
}
