package com.mechanicel.tomsdarts.ui.input

import com.mechanicel.tomsdarts.game.Dart
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reine JUnit-Tests fuer die pure Label-Formatierung. Keine Android-/Compose-
 * Abhaengigkeit (Happy-Path-Basis).
 */
class DartLabelTest {

    @Test
    fun dartShortLabel_out() {
        assertEquals("Out", dartShortLabel(Dart.miss()))
    }

    @Test
    fun dartShortLabel_bullUndDoubleBull() {
        assertEquals("Bull", dartShortLabel(Dart.bull()))
        assertEquals("D-Bull", dartShortLabel(Dart.doubleBull()))
    }

    @Test
    fun dartShortLabel_segmenteMitPraefix() {
        assertEquals("20", dartShortLabel(Dart.single(20)))
        assertEquals("D-19", dartShortLabel(Dart.double(19)))
        assertEquals("T-20", dartShortLabel(Dart.triple(20)))
    }

    @Test
    fun numberKeyLabel_proModifier() {
        assertEquals("7", numberKeyLabel(7, DartModifier.SINGLE))
        assertEquals("D-7", numberKeyLabel(7, DartModifier.DOUBLE))
        assertEquals("T-7", numberKeyLabel(7, DartModifier.TRIPLE))
    }

    @Test
    fun bullKeyLabel_proModifier() {
        assertEquals("Bull", bullKeyLabel(DartModifier.SINGLE))
        assertEquals("D-Bull", bullKeyLabel(DartModifier.DOUBLE))
        assertEquals("Bull", bullKeyLabel(DartModifier.TRIPLE))
    }

    // --- dartShortLabel: vollstaendige Faelle ---

    @Test
    fun dartShortLabel_out_segment0() {
        // Miss = Segment 0 -> "Out" (unabhaengig vom Multiplikator-Wert).
        assertEquals("Out", dartShortLabel(Dart(0, 1)))
    }

    @Test
    fun dartShortLabel_bull_25mal1() {
        assertEquals("Bull", dartShortLabel(Dart(25, 1)))
    }

    @Test
    fun dartShortLabel_doubleBull_25mal2() {
        assertEquals("D-Bull", dartShortLabel(Dart(25, 2)))
    }

    @Test
    fun dartShortLabel_triple20() {
        assertEquals("T-20", dartShortLabel(Dart.triple(20)))
    }

    @Test
    fun dartShortLabel_double19() {
        assertEquals("D-19", dartShortLabel(Dart.double(19)))
    }

    @Test
    fun dartShortLabel_single20_ohnePraefix() {
        // Single -> nur die Segmentzahl, kein Praefix.
        assertEquals("20", dartShortLabel(Dart.single(20)))
    }

    @Test
    fun dartShortLabel_single1_kleinstesSegment() {
        assertEquals("1", dartShortLabel(Dart.single(1)))
    }

    // --- numberKeyLabel: alle Modifier ueber mehrere Zahlen ---

    @Test
    fun numberKeyLabel_alleModifier_fuer1() {
        assertEquals("1", numberKeyLabel(1, DartModifier.SINGLE))
        assertEquals("D-1", numberKeyLabel(1, DartModifier.DOUBLE))
        assertEquals("T-1", numberKeyLabel(1, DartModifier.TRIPLE))
    }

    @Test
    fun numberKeyLabel_alleModifier_fuer20() {
        assertEquals("20", numberKeyLabel(20, DartModifier.SINGLE))
        assertEquals("D-20", numberKeyLabel(20, DartModifier.DOUBLE))
        assertEquals("T-20", numberKeyLabel(20, DartModifier.TRIPLE))
    }
}
