package com.mechanicel.tomsdarts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    // --- isValid: vollstaendige Matrix ------------------------------------

    @Test
    fun isValid_alleSegmente1bis20MitSingleDoubleTriple_sindGueltig() {
        for (segment in 1..20) {
            for (multiplier in 1..3) {
                val dart = Dart(segment, multiplier)
                assertTrue("Dart($segment, $multiplier) sollte gueltig sein", dart.isValid)
            }
        }
    }

    @Test
    fun isValid_missNurAlsSingle() {
        assertTrue(Dart(0, 1).isValid)
        assertFalse(Dart(0, 2).isValid)
        assertFalse(Dart(0, 3).isValid)
    }

    @Test
    fun isValid_bullNurAlsSingleOderDouble_keinTripleBull() {
        assertTrue(Dart(25, 1).isValid)
        assertTrue(Dart(25, 2).isValid)
        // Triple-Bull existiert auf dem Board nicht.
        assertFalse(Dart(25, 3).isValid)
    }

    @Test
    fun isValid_segmenteAusserhalbDesBoards_sindUngueltig() {
        assertFalse(Dart(21, 1).isValid)
        assertFalse(Dart(24, 1).isValid)
        assertFalse(Dart(26, 1).isValid)
        assertFalse(Dart(-1, 1).isValid)
        // Auch mit gueltigem Multiplier bleiben Off-Board-Segmente ungueltig.
        assertFalse(Dart(24, 2).isValid)
        assertFalse(Dart(26, 3).isValid)
    }

    @Test
    fun isValid_multiplierAusserhalb1bis3_sindUngueltig() {
        assertFalse(Dart(20, 0).isValid)
        assertFalse(Dart(20, 4).isValid)
        assertFalse(Dart(20, -1).isValid)
        // Ungueltiger Multiplier schlaegt auch fuer ansonsten gueltige Felder durch.
        assertFalse(Dart(0, 0).isValid)
        assertFalse(Dart(25, 0).isValid)
    }

    // --- value: Randfaelle -----------------------------------------------

    @Test
    fun value_randfaelle() {
        assertEquals(0, Dart.miss().value)
        assertEquals(1, Dart.single(1).value)
        assertEquals(25, Dart.bull().value)
        assertEquals(50, Dart.doubleBull().value)
        assertEquals(60, Dart.triple(20).value)
    }

    // --- Factories: konkrete (segment, multiplier)-Paare & Gueltigkeit ----

    @Test
    fun factories_erzeugenKorrektePaareUndSindAlleGueltig() {
        assertEquals(1, Dart.single(5).multiplier)
        assertEquals(5, Dart.single(5).segment)
        assertEquals(2, Dart.double(5).multiplier)
        assertEquals(3, Dart.triple(5).multiplier)
        assertEquals(25, Dart.bull().segment)
        assertEquals(1, Dart.bull().multiplier)
        assertEquals(25, Dart.doubleBull().segment)
        assertEquals(2, Dart.doubleBull().multiplier)
        assertEquals(0, Dart.miss().segment)
        assertEquals(1, Dart.miss().multiplier)

        // Saemtliche Factory-Erzeugnisse muessen gueltig sein.
        assertTrue(Dart.single(7).isValid)
        assertTrue(Dart.double(7).isValid)
        assertTrue(Dart.triple(7).isValid)
        assertTrue(Dart.bull().isValid)
        assertTrue(Dart.doubleBull().isValid)
        assertTrue(Dart.miss().isValid)
    }

    // --- data class: equals/hashCode -------------------------------------

    @Test
    fun equalsUndHashCode_gleicherDartIstGleich() {
        val a = Dart(20, 3)
        val b = Dart(20, 3)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        // Auch ueber Factory erzeugt identisch.
        assertEquals(Dart.triple(20), a)
    }

    @Test
    fun equals_unterschiedlicheDartsSindUngleich() {
        assertNotEquals(Dart(20, 2), Dart(20, 3))
        assertNotEquals(Dart(19, 3), Dart(20, 3))
    }
}
