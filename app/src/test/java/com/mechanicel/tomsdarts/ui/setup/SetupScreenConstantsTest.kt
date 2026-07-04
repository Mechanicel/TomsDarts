package com.mechanicel.tomsdarts.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reine JUnit-Tests fuer die im Setup-Bildschirm verwendeten Konstanten
 * [START_SCORES], [DEFAULT_START_SCORE] und [DEFAULT_DOUBLE_OUT]. Keine
 * Android-/Compose-Laufzeit noetig - die Werte sind reine
 * Kotlin-Top-Level-Deklarationen.
 *
 * Reine Compose-UI-Interaktion (Karten-Tap, visueller Selected-State) ist
 * mangels Instrumentation/Geraet nicht host-testbar (siehe Testklassen-Doku im
 * Test-Gate-Auftrag); diese Tests decken ausschliesslich die zugrunde liegenden
 * Werte ab, die [SetupScreenContent] rendert bzw. auf die [SetupScreen]
 * vorbelegt.
 */
class SetupScreenConstantsTest {

    @Test
    fun startScores_enthaeltGenauDieDreiErwartetenWerteInAnzeigeReihenfolge() {
        // Reihenfolge ist UI-relevant (Karten werden in dieser Reihenfolge
        // nebeneinander gerendert) - bewusst mit assertEquals auf die Liste
        // statt nur auf den Inhalt (Set) geprueft.
        assertEquals(listOf(301, 501, 701), START_SCORES)
    }

    @Test
    fun startScores_hatKeineDuplikate() {
        assertEquals(START_SCORES.size, START_SCORES.toSet().size)
    }

    @Test
    fun startScores_enthaeltNurPositiveWerte() {
        assertTrue(START_SCORES.all { it > 0 })
    }

    @Test
    fun defaultStartScore_ist501() {
        assertEquals(501, DEFAULT_START_SCORE)
    }

    @Test
    fun defaultStartScore_istTeilDerAuswaehlbarenStartScores() {
        // Regression: die vorbelegte Auswahl muss stets eine der angebotenen
        // Karten sein, sonst zeigt SetupScreen initial keine Karte als
        // ausgewaehlt an.
        assertTrue(DEFAULT_START_SCORE in START_SCORES)
    }

    @Test
    fun defaultDoubleOut_istAn() {
        // Der Double-Out-Toggle ist im Setup initial an (Standardregel im X01).
        assertTrue(DEFAULT_DOUBLE_OUT)
    }

    @Test
    fun legsBestOfOptions_enthaeltGenauDieDreiErwartetenWerteInAnzeigeReihenfolge() {
        assertEquals(listOf(1, 3, 5), LEGS_BEST_OF_OPTIONS)
    }

    @Test
    fun setsBestOfOptions_enthaeltGenauDieDreiErwartetenWerteInAnzeigeReihenfolge() {
        assertEquals(listOf(1, 3, 5), SETS_BEST_OF_OPTIONS)
    }

    @Test
    fun defaultLegsBestOf_istTeilDerAuswaehlbarenLegsOptionen() {
        // Regression: die vorbelegte Auswahl muss stets eine der angebotenen
        // Karten sein, sonst zeigt SetupScreen initial keine Karte als
        // ausgewaehlt an.
        assertTrue(DEFAULT_LEGS_BEST_OF in LEGS_BEST_OF_OPTIONS)
    }

    @Test
    fun defaultSetsBestOf_istTeilDerAuswaehlbarenSetsOptionen() {
        assertTrue(DEFAULT_SETS_BEST_OF in SETS_BEST_OF_OPTIONS)
    }

    @Test
    fun bestOfToWin_bildetBestOfXAufFirstToNAb() {
        // Einzige Umrechnungsstelle Best-of-X -> first-to-N: (bestOf + 1) / 2.
        assertEquals(1, bestOfToWin(1))
        assertEquals(2, bestOfToWin(3))
        assertEquals(3, bestOfToWin(5))
    }
}
