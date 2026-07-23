package com.mechanicel.tomsdarts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Happy-Path-Basistests fuer [AroundTheClockMode]. Reines JUnit, kein Robolectric.
 *
 * Deckt initialState, das Vorruecken bei Zieltreffer (Single/Double/Triple gleich),
 * die No-Ops bei falscher Zahl / Miss / Bull, das sequentielle Fortschreiten von 1
 * bis 20, den Leg-Gewinn beim Treffer der 20, das Ignorieren der Gegner und den
 * fehlenden Bust ab. Das systematische Abhaerten uebernimmt der tester-Workflow.
 */
class AroundTheClockModeTest {

    private val mode = AroundTheClockMode()
    private val config = GameConfig()

    @Test
    fun keyUndDisplayName_sindGesetzt() {
        assertEquals("AROUND_THE_CLOCK", mode.key)
        assertEquals("Around the Clock", mode.displayName)
    }

    @Test
    fun initialState_startetBeiEins() {
        assertEquals(AroundTheClockState(1), mode.initialState(config))
    }

    @Test
    fun zieltreffer_ruecktZielVor_undWertetEins() {
        val o = mode.applyDart(AroundTheClockState(1), Dart.single(1), config)
        assertEquals(AroundTheClockState(2), o.newState)
        assertEquals(1, o.scored)
        assertFalse(o.bust)
        assertFalse(o.legWon)
    }

    @Test
    fun multiplierEgal_singleDoubleTripleRueckenGleichVor() {
        // Single, Double und Triple der Zielzahl 5 rueecken alle genau um 1 vor.
        val single = mode.applyDart(AroundTheClockState(5), Dart.single(5), config)
        val double = mode.applyDart(AroundTheClockState(5), Dart.double(5), config)
        val triple = mode.applyDart(AroundTheClockState(5), Dart.triple(5), config)
        assertEquals(AroundTheClockState(6), single.newState)
        assertEquals(AroundTheClockState(6), double.newState)
        assertEquals(AroundTheClockState(6), triple.newState)
        assertEquals(1, single.scored)
        assertEquals(1, double.scored)
        assertEquals(1, triple.scored)
    }

    @Test
    fun falscheZahl_istNoOp() {
        val start = AroundTheClockState(7)
        val o = mode.applyDart(start, Dart.single(12), config)
        assertEquals(start, o.newState)
        assertEquals(0, o.scored)
        assertFalse(o.bust)
        assertFalse(o.legWon)
    }

    @Test
    fun miss_istNoOp() {
        val start = AroundTheClockState(3)
        val o = mode.applyDart(start, Dart.miss(), config)
        assertEquals(start, o.newState)
        assertEquals(0, o.scored)
        assertFalse(o.bust)
        assertFalse(o.legWon)
    }

    @Test
    fun bull_istNoOp() {
        // Bull (25) ist nie ein Ziel in 1..20 -> No-Op.
        val start = AroundTheClockState(20)
        val single = mode.applyDart(start, Dart.bull(), config)
        val doubleBull = mode.applyDart(start, Dart.doubleBull(), config)
        assertEquals(start, single.newState)
        assertEquals(start, doubleBull.newState)
        assertEquals(0, single.scored)
        assertEquals(0, doubleBull.scored)
        assertFalse(single.legWon)
        assertFalse(doubleBull.legWon)
    }

    @Test
    fun sequentiellesFortschreiten_vonEinsBisZwanzig() {
        var state = mode.initialState(config)
        for (n in 1..19) {
            val o = mode.applyDart(state, Dart.single(n), config)
            assertFalse("Zwischenschritt $n darf noch nicht gewinnen", o.legWon)
            assertFalse(o.bust)
            assertEquals(1, o.scored)
            assertEquals(AroundTheClockState(n + 1), o.newState)
            state = o.newState
        }
        assertEquals(AroundTheClockState(20), state)
    }

    @Test
    fun trefferDerZwanzig_gewinntDasLeg() {
        val o = mode.applyDart(AroundTheClockState(20), Dart.single(20), config)
        assertTrue(o.legWon)
        assertFalse(o.bust)
        assertEquals(1, o.scored)
        // Ziel rueckt ueber 20 hinaus.
        assertEquals(AroundTheClockState(21), o.newState)
    }

    @Test
    fun gegnerWerdenIgnoriert() {
        // Ein weit fortgeschrittener Gegner aendert nichts am eigenen Ergebnis.
        val opponent = AroundTheClockState(20)
        val o = mode.applyDart(
            AroundTheClockState(1),
            Dart.single(1),
            config,
            opponents = listOf(opponent),
        )
        assertEquals(AroundTheClockState(2), o.newState)
        assertEquals(1, o.scored)
        assertFalse(o.legWon)
    }

    @Test
    fun keinBust_ueberDenGesamtenDurchlauf() {
        // Weder Zieltreffer noch Fehlwuerfe loesen jemals einen Bust aus.
        assertFalse(mode.applyDart(AroundTheClockState(1), Dart.single(1), config).bust)
        assertFalse(mode.applyDart(AroundTheClockState(1), Dart.single(9), config).bust)
        assertFalse(mode.applyDart(AroundTheClockState(20), Dart.single(20), config).bust)
    }
}
