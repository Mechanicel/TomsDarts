package com.mechanicel.tomsdarts.ui.setup

import androidx.compose.ui.unit.dp
import com.mechanicel.tomsdarts.game.GameModeCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reine JVM-Tests der Hilfsfunktionen hinter der Modus-Auswahl im Setup:
 * Spaltenzahl des Karten-Rasters ([modeGridColumns]) und Aufloesung der
 * gewaehlten Kennung inkl. Fallback ([resolveSelectedMode]).
 */
class ModeSelectionLayoutTest {

    @Test
    fun schmaleBreiteHatZweiSpalten() {
        assertEquals(2, modeGridColumns(280.dp))
        assertEquals(2, modeGridColumns(320.dp))
        assertEquals(2, modeGridColumns(479.dp))
    }

    @Test
    fun abBreakpointDreiSpalten() {
        // Grenzwert inklusive: genau 480 dp zeigt bereits drei Spalten.
        assertEquals(3, modeGridColumns(MODE_THREE_COLUMN_BREAKPOINT))
        assertEquals(3, modeGridColumns(600.dp))
        assertEquals(3, modeGridColumns(1200.dp))
    }

    @Test
    fun knapptUnterBreakpointBleibtZweispaltig() {
        assertEquals(2, modeGridColumns(479.9.dp))
    }

    @Test
    fun bekannteKennungWirdAufgeloest() {
        GameModeCatalog.entries.forEach { mode ->
            assertEquals(mode, resolveSelectedMode(mode.key))
        }
    }

    @Test
    fun unbekannteKennungFaelltAufErstenKatalogEintragZurueck() {
        assertEquals(GameModeCatalog.entries.first(), resolveSelectedMode("GIBT_ES_NICHT"))
    }

    @Test
    fun leereUndFalschGeschriebeneKennungFallenZurueck() {
        // Kennungen sind case-sensitiv; "x01" ist keine gueltige Kennung.
        assertEquals(GameModeCatalog.entries.first(), resolveSelectedMode(""))
        assertEquals(GameModeCatalog.entries.first(), resolveSelectedMode("x01"))
    }

    @Test
    fun fallbackIstDerStandardModus() {
        // Der Fallback soll mit dem Standard des Katalogs uebereinstimmen, sonst
        // zeigt das Setup bei kaputter Kennung einen anderen Modus als ein
        // frisch gestartetes Setup.
        assertEquals(GameModeCatalog.DEFAULT, resolveSelectedMode("GIBT_ES_NICHT").key)
    }
}
