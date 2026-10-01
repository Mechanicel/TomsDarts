package com.mechanicel.tomsdarts.ui.delight

import com.mechanicel.tomsdarts.delight.DelightAnimation
import com.mechanicel.tomsdarts.ui.game.GameViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests der puren Zeitlogik der Feier-Overlays ([DelightTiming]). */
class DelightTimingTest {

    @Test
    fun grunddauerJeTyp() {
        assertEquals(2500L, DelightTiming.displayMillis(DelightAnimation.CONFETTI, reducedMotion = false))
        assertEquals(2200L, DelightTiming.displayMillis(DelightAnimation.SPIN, reducedMotion = false))
        assertEquals(2200L, DelightTiming.displayMillis(DelightAnimation.TRIANGLE, reducedMotion = false))
        assertEquals(1800L, DelightTiming.displayMillis(DelightAnimation.GENERIC, reducedMotion = false))
    }

    @Test
    fun reduzierteBewegungLiefertEinheitlich2000() {
        DelightAnimation.entries.forEach { animation ->
            assertEquals(2000L, DelightTiming.displayMillis(animation, reducedMotion = true))
        }
    }

    @Test
    fun jedeDauerLiegtUnterDemSicherheitsnetzDesViewModels() {
        // Sonst liefe die Kontrollpause unter einer noch sichtbaren Feier an.
        assertTrue(DelightTiming.MAX_DISPLAY_MILLIS < GameViewModel.DELIGHT_MAX_HOLD_MILLIS)
        DelightAnimation.entries.forEach { animation ->
            listOf(false, true).forEach { reduced ->
                assertTrue(DelightTiming.displayMillis(animation, reduced) <= DelightTiming.MAX_DISPLAY_MILLIS)
            }
        }
    }

    @Test
    fun ohneEmpfehlungGiltDieGrunddauer() {
        assertEquals(2500L, DelightTiming.totalMillis(DelightAnimation.CONFETTI, reducedMotion = false))
    }

    @Test
    fun empfehlungVerlaengertBisZurObergrenze() {
        assertEquals(
            4000L,
            DelightTiming.totalMillis(DelightAnimation.GENERIC, reducedMotion = false) { 4000L },
        )
        // TalkBack-"Zeit zum Reagieren" kann Minuten betragen -> gedeckelt.
        assertEquals(
            DelightTiming.MAX_DISPLAY_MILLIS,
            DelightTiming.totalMillis(DelightAnimation.CONFETTI, reducedMotion = false) { 120_000L },
        )
    }

    @Test
    fun empfehlungUnterDerGrunddauerVerkuerztNicht() {
        assertEquals(
            1800L,
            DelightTiming.totalMillis(DelightAnimation.GENERIC, reducedMotion = false) { 10L },
        )
    }

    @Test
    fun restzeitWirdAusStartUndJetztBerechnet() {
        assertEquals(2500L, DelightTiming.remainingMillis(startedAt = 1000L, now = 1000L, total = 2500L))
        assertEquals(1500L, DelightTiming.remainingMillis(startedAt = 1000L, now = 2000L, total = 2500L))
    }

    @Test
    fun abgelaufeneFeierHatKeineRestzeit() {
        assertEquals(0L, DelightTiming.remainingMillis(startedAt = 1000L, now = 3500L, total = 2500L))
        assertTrue(DelightTiming.remainingMillis(startedAt = 1000L, now = 10_000L, total = 2500L) <= 0L)
    }

    @Test
    fun zurueckgesetzteUhrGiltAlsAbgelaufen() {
        // Nach Geraete-Neustart ist elapsedRealtime kleiner als die gespeicherte Startzeit.
        assertEquals(0L, DelightTiming.remainingMillis(startedAt = 50_000L, now = 100L, total = 2500L))
    }
}
