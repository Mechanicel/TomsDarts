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
}
