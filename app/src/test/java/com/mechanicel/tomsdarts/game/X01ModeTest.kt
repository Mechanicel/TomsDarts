package com.mechanicel.tomsdarts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Happy-Path-Basistests fuer [X01Mode]. Reines JUnit, kein Robolectric.
 *
 * Deckt initialState, regulaeren Wurf, Checkout (Double und Doppel-Bull),
 * die zentralen Bust-Pfade und den Modus ohne Double-Out ab. Das systematische
 * Abhaerten (Randfaelle, weitere Fehlerpfade) uebernimmt der tester-Workflow.
 */
class X01ModeTest {

    private val mode = X01Mode()
    private val config = GameConfig(startScore = 501, doubleOut = true)

    @Test
    fun keyUndDisplayName_sindGesetzt() {
        assertEquals("X01", mode.key)
        assertEquals("X01", mode.displayName)
    }

    @Test
    fun initialState_entsprichtStartScore() {
        assertEquals(X01State(501), mode.initialState(config))
        assertEquals(X01State(301), mode.initialState(config.copy(startScore = 301)))
        assertEquals(X01State(701), mode.initialState(config.copy(startScore = 701)))
    }

    @Test
    fun regulaererWurf_reduziertRestUndWertet() {
        // T20 auf 501 -> 441, scored 60.
        val o = mode.applyDart(X01State(501), Dart.triple(20), config)
        assertEquals(X01State(441), o.newState)
        assertEquals(60, o.scored)
        assertFalse(o.bust)
        assertFalse(o.legWon)
    }

    @Test
    fun checkout_mitDouble_gewinntLeg() {
        // Rest 40 -> Double 20 -> 0 mit Double -> legWon.
        val o = mode.applyDart(X01State(40), Dart.double(20), config)
        assertEquals(X01State(0), o.newState)
        assertEquals(40, o.scored)
        assertTrue(o.legWon)
        assertFalse(o.bust)
    }

    @Test
    fun checkout_mitDoppelBull_gewinntLeg() {
        // Rest 50 -> Doppel-Bull (50, isDouble) -> 0 -> legWon.
        val o = mode.applyDart(X01State(50), Dart.doubleBull(), config)
        assertEquals(X01State(0), o.newState)
        assertEquals(50, o.scored)
        assertTrue(o.legWon)
        assertFalse(o.bust)
    }

    @Test
    fun bust_beimUeberwerfen() {
        // Rest 20 -> Triple 20 (60) -> -40 -> bust.
        val o = mode.applyDart(X01State(20), Dart.triple(20), config)
        assertTrue(o.bust)
        assertFalse(o.legWon)
        assertEquals(0, o.scored)
        // Bust: unveraenderter Eingangszustand.
        assertEquals(X01State(20), o.newState)
    }

    @Test
    fun bust_beiRestEins() {
        // Rest 41 -> Double 20 (40) -> Rest 1 -> bust (nicht per Double ausspielbar).
        val o = mode.applyDart(X01State(41), Dart.double(20), config)
        assertTrue(o.bust)
        assertFalse(o.legWon)
        assertEquals(0, o.scored)
    }

    @Test
    fun bust_beiRestNullOhneDouble() {
        // Rest 20 -> Single 20 -> 0 ohne Double -> bust (Finish nur mit Double).
        val o = mode.applyDart(X01State(20), Dart.single(20), config)
        assertTrue(o.bust)
        assertFalse(o.legWon)
        assertEquals(0, o.scored)
    }

    @Test
    fun ohneDoubleOut_checkoutMitBeliebigemWurf() {
        val noDouble = config.copy(doubleOut = false)
        // Rest 20 -> Single 20 -> 0 ohne Double erlaubt -> legWon.
        val o = mode.applyDart(X01State(20), Dart.single(20), noDouble)
        assertEquals(X01State(0), o.newState)
        assertEquals(20, o.scored)
        assertTrue(o.legWon)
        assertFalse(o.bust)
    }
}
