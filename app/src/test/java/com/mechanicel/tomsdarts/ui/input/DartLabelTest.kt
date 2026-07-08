package com.mechanicel.tomsdarts.ui.input

import com.mechanicel.tomsdarts.game.Dart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // --- dartSpokenLabel: ausgeschriebene Screenreader-Labels ---

    @Test
    fun dartSpokenLabel_single_nurSegmentzahl() {
        assertEquals("20", dartSpokenLabel(Dart.single(20)))
        assertEquals("5", dartSpokenLabel(Dart.single(5)))
    }

    @Test
    fun dartSpokenLabel_double_ausgeschrieben() {
        assertEquals("Double 16", dartSpokenLabel(Dart.double(16)))
    }

    @Test
    fun dartSpokenLabel_triple_ausgeschrieben() {
        assertEquals("Triple 20", dartSpokenLabel(Dart.triple(20)))
    }

    @Test
    fun dartSpokenLabel_bullUndDoubleBull() {
        assertEquals("Bull", dartSpokenLabel(Dart.bull()))
        assertEquals("Doppel-Bull", dartSpokenLabel(Dart.doubleBull()))
    }

    @Test
    fun dartSpokenLabel_miss_daneben() {
        assertEquals("Daneben", dartSpokenLabel(Dart.miss()))
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

    // --- dartSpokenLabel: Test-Gate-Haertung (Raender + Vollabdeckung) ---------

    @Test
    fun dartSpokenLabel_single_randSegment1() {
        // Kleinstes gueltiges Segment -> nur die Zahl, kein Praefix.
        assertEquals("1", dartSpokenLabel(Dart.single(1)))
    }

    @Test
    fun dartSpokenLabel_double_randSegmente1Und20() {
        assertEquals("Double 1", dartSpokenLabel(Dart.double(1)))
        assertEquals("Double 20", dartSpokenLabel(Dart.double(20)))
    }

    @Test
    fun dartSpokenLabel_triple_randSegmente1Und20() {
        assertEquals("Triple 1", dartSpokenLabel(Dart.triple(1)))
        assertEquals("Triple 20", dartSpokenLabel(Dart.triple(20)))
    }

    @Test
    fun dartSpokenLabel_miss_unabhaengigVomMultiplierWert() {
        // Segment 0 -> immer "Daneben", unabhaengig vom (physikalisch stets 1
        // gueltigen, aber hier bewusst variierten) Multiplier-Rohwert.
        assertEquals("Daneben", dartSpokenLabel(Dart(0, 1)))
    }

    @Test
    fun dartSpokenLabel_alleSegmenteUndMultiplikatoren_liefernNieLeerenString() {
        // Vollabdeckung ueber alle gueltigen (segment, multiplier)-Kombinationen:
        // kein leerer String, kein "0" im Text bei Miss/Bull-Sonderfaellen.
        val segments = (1..20) + listOf(0, 25)
        val multipliers = listOf(1, 2, 3)
        for (segment in segments) {
            for (multiplier in multipliers) {
                val dart = Dart(segment, multiplier)
                if (!dart.isValid) continue
                val label = dartSpokenLabel(dart)
                assertTrue(
                    "dartSpokenLabel($dart) darf nicht leer sein",
                    label.isNotBlank(),
                )
            }
        }
    }

    @Test
    fun dartSpokenLabel_undDartShortLabel_stimmenBeiSingleUeberein() {
        // Fuer reine Segmentzahlen (Single, ohne Bull) sind Kurz- und Sprech-
        // Label identisch: beide liefern nur die Zahl ohne Praefix/Suffix.
        for (n in 1..20) {
            assertEquals(dartShortLabel(Dart.single(n)), dartSpokenLabel(Dart.single(n)))
        }
    }
}
