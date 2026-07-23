package com.mechanicel.tomsdarts.game

import org.junit.Assert.assertEquals
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
    fun entries_hatAktuellGenauEinenEintrag_ModusAuswahlBleibtVerborgen() {
        // Solange es genau einen Modus gibt, blendet das Setup die Modus-Auswahl
        // aus (Bedingung entries.size > 1 ist false) - der Bildschirm bleibt
        // optisch unveraendert. Dieser Test dokumentiert diesen Zustand; ein
        // zweiter Modus laesst ihn bewusst rot werden und erinnert daran, dass
        // die Auswahl dann sichtbar wird.
        assertEquals(1, GameModeCatalog.entries.size)
    }
}
