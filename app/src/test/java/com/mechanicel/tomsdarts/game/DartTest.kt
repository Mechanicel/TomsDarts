package com.mechanicel.tomsdarts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reine JUnit-Tests fuer das [Dart]-Value-Object. Keine Android-/Room-/
 * Robolectric-Abhaengigkeit - laeuft direkt auf der JVM.
 */
class DartTest {

    @Test
    fun value_wirdAusSegmentMalMultiplierBerechnet() {
        assertEquals(0, Dart.miss().value)
        assertEquals(25, Dart.bull().value)
        assertEquals(50, Dart.doubleBull().value)
        assertEquals(60, Dart.triple(20).value)
        assertEquals(20, Dart.single(20).value)
        assertEquals(40, Dart.double(20).value)
    }

    @Test
    fun isDoubleUndIsTriple_spiegelnDenMultiplier() {
        assertTrue(Dart.double(10).isDouble)
        assertFalse(Dart.double(10).isTriple)
        assertTrue(Dart.triple(10).isTriple)
        assertFalse(Dart.triple(10).isDouble)
        assertFalse(Dart.single(10).isDouble)
        assertFalse(Dart.single(10).isTriple)
    }

    @Test
    fun isValid_akzeptiertGueltigeWuerfe() {
        assertTrue(Dart.miss().isValid)
        assertTrue(Dart.single(1).isValid)
        assertTrue(Dart.single(20).isValid)
        assertTrue(Dart.double(20).isValid)
        assertTrue(Dart.triple(20).isValid)
        assertTrue(Dart.bull().isValid)
        assertTrue(Dart.doubleBull().isValid)
    }

    @Test
    fun isValid_lehntUngueltigeWuerfeAb() {
        // Triple-Bull existiert nicht.
        assertFalse(Dart(25, 3).isValid)
        // Miss nur als Single.
        assertFalse(Dart(0, 2).isValid)
        assertFalse(Dart(0, 3).isValid)
        // Segmente ausserhalb des Boards.
        assertFalse(Dart(21, 1).isValid)
        assertFalse(Dart(-1, 1).isValid)
        // Multiplier ausserhalb 1..3.
        assertFalse(Dart(20, 0).isValid)
        assertFalse(Dart(20, 4).isValid)
    }

    @Test
    fun factories_liefernErwarteteDarts() {
        assertEquals(Dart(20, 1), Dart.single(20))
        assertEquals(Dart(20, 2), Dart.double(20))
        assertEquals(Dart(20, 3), Dart.triple(20))
        assertEquals(Dart(25, 1), Dart.bull())
        assertEquals(Dart(25, 2), Dart.doubleBull())
        assertEquals(Dart(0, 1), Dart.miss())
    }
}
