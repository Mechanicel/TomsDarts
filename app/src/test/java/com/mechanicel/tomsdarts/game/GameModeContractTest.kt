package com.mechanicel.tomsdarts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vertrags-Test fuer das [GameMode]-Interface mittels eines minimalen
 * Fake-Modus. Beweist, dass das Interface implementierbar und der
 * [DartOutcome]-Vertrag nutzbar ist. Reines JUnit, kein Robolectric.
 *
 * Der [CountUpFake] ist AUSSCHLIESSLICH Test-Code (kein Produktions-Modus):
 * ein einfacher Akkumulator (S = Int Punktestand), der `dart.value` aufaddiert,
 * nie bustet und das Leg bei Erreichen von [GameConfig.startScore] als
 * Schwellwert gewinnt.
 */
class GameModeContractTest {

    /** Minimaler Count-Up-artiger Fake-Modus nur fuer diesen Vertrags-Test. */
    private class CountUpFake : GameMode<Int> {
        override val key: String = "FAKE_COUNTUP"
        override val displayName: String = "Count Up (Fake)"

        override fun initialState(config: GameConfig): Int = 0

        override fun applyDart(
            state: Int,
            dart: Dart,
            config: GameConfig,
            opponents: List<Int>,
        ): DartOutcome<Int> {
            val newState = state + dart.value
            val legWon = newState >= config.startScore
            return DartOutcome(
                newState = newState,
                bust = false,
                legWon = legWon,
                scored = dart.value,
            )
        }
    }

    private val mode = CountUpFake()
    private val config = GameConfig(startScore = 100, doubleOut = false)

    @Test
    fun initialState_startetBeiNull() {
        assertEquals(0, mode.initialState(config))
    }

    @Test
    fun applyDart_akkumuliertPunkteOhneBust() {
        var state = mode.initialState(config)

        val o1 = mode.applyDart(state, Dart.triple(20), config)
        assertEquals(60, o1.newState)
        assertEquals(60, o1.scored)
        assertFalse(o1.bust)
        assertFalse(o1.legWon)
        state = o1.newState

        val o2 = mode.applyDart(state, Dart.single(20), config)
        assertEquals(80, o2.newState)
        assertEquals(20, o2.scored)
        assertFalse(o2.legWon)
    }

    @Test
    fun applyDart_gewinntLegBeimErreichenDesSchwellwerts() {
        // 60 + 60 = 120 >= 100 -> legWon.
        val afterFirst = mode.applyDart(mode.initialState(config), Dart.triple(20), config)
        val outcome = mode.applyDart(afterFirst.newState, Dart.triple(20), config)

        assertEquals(120, outcome.newState)
        assertTrue(outcome.legWon)
        assertFalse(outcome.bust)
    }

    @Test
    fun keyUndDisplayName_sindGesetzt() {
        assertEquals("FAKE_COUNTUP", mode.key)
        assertEquals("Count Up (Fake)", mode.displayName)
    }

    @Test
    fun applyDart_scoredEntsprichtImmerDartValueBeimAkkumulator() {
        // Der Count-Up-Fake bustet nie -> scored == dart.value fuer jeden Wurf.
        val state = mode.initialState(config)
        assertEquals(0, mode.applyDart(state, Dart.miss(), config).scored)
        assertEquals(25, mode.applyDart(state, Dart.bull(), config).scored)
        assertEquals(50, mode.applyDart(state, Dart.doubleBull(), config).scored)
        assertEquals(60, mode.applyDart(state, Dart.triple(20), config).scored)
    }

    @Test
    fun applyDart_countUp_nieBustUndNieLegWonNochBeideGleichzeitig() {
        // Ueber eine Wurfreihe bleibt der "nie beide Flags zugleich"-Vertrag erfuellt.
        var state = mode.initialState(config)
        val darts = listOf(
            Dart.triple(20), Dart.triple(20), Dart.single(20),
            Dart.bull(), Dart.doubleBull(), Dart.miss(),
        )
        for (dart in darts) {
            val o = mode.applyDart(state, dart, config)
            assertFalse("Count-Up bustet nie", o.bust)
            assertFalse("bust und legWon nie gleichzeitig", o.bust && o.legWon)
            state = o.newState
        }
    }

    // --- Zweiter Fake: X01-artig, demonstriert Bust UND LegWon ------------

    /**
     * Minimaler X01-artiger Fake-Modus (S = Restpunktzahl) ausschliesslich fuer
     * diesen Vertrags-Test. Setzt die im [GameMode.applyDart]-Doc skizzierte
     * Flag-Semantik um, um Bust- und LegWon-Pfade testbar zu machen:
     * - Rest < 0          -> bust
     * - Rest == 1 bei doubleOut -> bust (kein Double-Finish mehr moeglich)
     * - Rest == 0 ohne Double bei doubleOut -> bust
     * - Rest == 0 (Double-Out erfuellt oder doubleOut == false) -> legWon
     * - sonst regulaer.
     */
    private class X01Fake : GameMode<Int> {
        override val key: String = "FAKE_X01"
        override val displayName: String = "X01 (Fake)"

        override fun initialState(config: GameConfig): Int = config.startScore

        override fun applyDart(
            state: Int,
            dart: Dart,
            config: GameConfig,
            opponents: List<Int>,
        ): DartOutcome<Int> {
            val rest = state - dart.value
            return when {
                rest < 0 -> bust(state)
                rest == 1 && config.doubleOut -> bust(state)
                rest == 0 && config.doubleOut && !dart.isDouble -> bust(state)
                rest == 0 -> DartOutcome(0, bust = false, legWon = true, scored = dart.value)
                else -> DartOutcome(rest, bust = false, legWon = false, scored = dart.value)
            }
        }

        // Bei Bust: newState bleibt unveraendert (Engine verwirft ohnehin), scored 0.
        private fun bust(state: Int): DartOutcome<Int> =
            DartOutcome(state, bust = true, legWon = false, scored = 0)
    }

    private val x01 = X01Fake()
    private val x01Config = GameConfig(startScore = 40, doubleOut = true)

    @Test
    fun x01_initialState_entsprichtStartScore() {
        assertEquals(40, x01.initialState(x01Config))
        assertEquals(170, x01.initialState(GameConfig(startScore = 170)))
    }

    @Test
    fun x01_regulaererWurf_reduziertRestUndWertet() {
        val o = x01.applyDart(40, Dart.single(20), x01Config)
        assertEquals(20, o.newState)
        assertEquals(20, o.scored)
        assertFalse(o.bust)
        assertFalse(o.legWon)
    }

    @Test
    fun x01_legWon_beiDoubleFinish_dannBustFalse() {
        // Rest 40 -> Double 20 -> 0 mit Double -> legWon.
        val o = x01.applyDart(40, Dart.double(20), x01Config)
        assertEquals(0, o.newState)
        assertTrue(o.legWon)
        assertFalse(o.bust)
        // Vertrag: legWon == true -> bust == false.
        assertFalse(o.legWon && o.bust)
    }

    @Test
    fun x01_legWon_ohneDoubleOut_auchOhneDoubleErlaubt() {
        val noDouble = GameConfig(startScore = 40, doubleOut = false)
        // Rest 40 -> Single 20 -> 20; dann Single 20 -> 0 ohne Double -> legWon (doubleOut aus).
        val o = x01.applyDart(20, Dart.single(20), noDouble)
        assertEquals(0, o.newState)
        assertTrue(o.legWon)
        assertFalse(o.bust)
    }

    @Test
    fun x01_bust_beiUeberwerfen_dannLegWonFalse() {
        // Rest 40 -> Triple 20 (60) -> -20 -> bust.
        val o = x01.applyDart(40, Dart.triple(20), x01Config)
        assertTrue(o.bust)
        assertFalse(o.legWon)
        assertEquals(0, o.scored)
        // Vertrag: bust == true -> legWon == false.
        assertFalse(o.bust && o.legWon)
    }

    @Test
    fun x01_bust_beiRestEinsTrotzDoubleOut() {
        // Rest 40 -> Single 39 gibt es nicht; nimm Rest 40, Wurf 39 via S19+? Direkt: Rest 3 -> Single 2 -> 1 -> bust.
        val o = x01.applyDart(3, Dart.double(1), x01Config) // 3 - 2 = 1 -> bust
        assertTrue(o.bust)
        assertFalse(o.legWon)
    }

    @Test
    fun x01_bust_beiRestNullOhneDoubleTrotzDoubleOut() {
        // Rest 20 -> Single 20 -> 0, aber kein Double -> bust (doubleOut).
        val o = x01.applyDart(20, Dart.single(20), x01Config)
        assertTrue(o.bust)
        assertFalse(o.legWon)
        assertEquals(0, o.scored)
    }

    @Test
    fun x01_mehrschrittSequenz_zumDoubleFinish() {
        var state = x01.initialState(GameConfig(startScore = 100, doubleOut = true))
        val o1 = x01.applyDart(state, Dart.triple(20), GameConfig(startScore = 100)) // 100-60=40
        assertEquals(40, o1.newState)
        assertFalse(o1.bust)
        assertFalse(o1.legWon)
        state = o1.newState

        val o2 = x01.applyDart(state, Dart.double(20), GameConfig(startScore = 100)) // 40-40=0 Double
        assertEquals(0, o2.newState)
        assertTrue(o2.legWon)
        assertFalse(o2.bust)
    }
}
