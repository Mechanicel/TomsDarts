package com.mechanicel.tomsdarts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reine JUnit-Tests fuer die Modus-Registry [GameModeCatalog]. Sichern die
 * Invarianten ab, auf die sich Setup-UI (Sichtbarkeit der Optionen) und
 * ViewModel-Factory (Auflösung ueber den Schluessel) verlassen. Keine Android-/
 * Compose-Laufzeit noetig.
 */
class GameModeCatalogTest {

    @Test
    fun x01_istImKatalogEnthalten() {
        val x01 = GameModeCatalog.entries.firstOrNull { it.key == GameModeCatalog.X01 }
        assertNotNull("X01 muss im Katalog vorhanden sein", x01)
    }

    @Test
    fun x01_nutztStartpunktUndDoubleOut() {
        // Regression: X01 blendet im Setup weiterhin Startpunkt-Auswahl UND
        // Double-Out-Schalter ein (heutiges Verhalten).
        val x01 = GameModeCatalog.entries.first { it.key == GameModeCatalog.X01 }
        assertTrue("X01 nutzt Startpunkt", x01.usesStartScore)
        assertTrue("X01 nutzt Double-Out", x01.usesDoubleOut)
    }

    @Test
    fun default_istX01() {
        assertEquals(GameModeCatalog.X01, GameModeCatalog.DEFAULT)
    }

    @Test
    fun default_istEinGueltigerKatalogEintrag() {
        // Die vorbelegte Auswahl muss stets ein tatsaechlich angebotener Modus
        // sein, sonst zeigt das Setup initial keinen Modus als ausgewaehlt an.
        assertTrue(GameModeCatalog.entries.any { it.key == GameModeCatalog.DEFAULT })
    }

    @Test
    fun entries_hatKeineDuplikateSchluessel() {
        val keys = GameModeCatalog.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun entries_enthaeltX01UndCricket_ModusAuswahlWirdSichtbar() {
        // Mit Cricket als zweitem Modus hat der Katalog zwei Eintraege; das Setup
        // blendet die Modus-Auswahl nun ein (Bedingung entries.size > 1 == true).
        // X01 bleibt der erste Eintrag (== DEFAULT).
        assertEquals(2, GameModeCatalog.entries.size)
        assertEquals(GameModeCatalog.X01, GameModeCatalog.entries.first().key)
    }

    @Test
    fun cricket_istImKatalogEnthalten_ohneStartpunktUndOhneDoubleOut() {
        // Cricket kennt weder konfigurierbaren Startpunkt noch Double-Out; die
        // entsprechenden Setup-Abschnitte blenden sich ueber diese Flags aus.
        val cricket = GameModeCatalog.entries.firstOrNull { it.key == GameModeCatalog.CRICKET }
        assertNotNull("Cricket muss im Katalog vorhanden sein", cricket)
        assertFalse("Cricket nutzt keinen Startpunkt", cricket!!.usesStartScore)
        assertFalse("Cricket nutzt kein Double-Out", cricket.usesDoubleOut)
    }
}
