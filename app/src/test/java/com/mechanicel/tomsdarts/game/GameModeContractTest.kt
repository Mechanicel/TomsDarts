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

        override fun applyDart(state: Int, dart: Dart, config: GameConfig): DartOutcome<Int> {
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
}
