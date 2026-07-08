package com.mechanicel.tomsdarts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reine JUnit-Tests fuer [checkoutSuggestion] (analog [X01ModeTest]).
 *
 * Kern sind die Invarianten ueber die gesamte Vorschlagstabelle: Jede gelieferte
 * Route summiert exakt auf den Rest, besteht aus 1-3 Darts und endet auf ein
 * Doppel. Dazu die Ausschluesse (Bogey-Reste, Rest 1, ausserhalb 2..170,
 * `doubleOut == false`) und ein paar konkrete Referenz-Routen.
 */
class CheckoutTest {

    /** Bogey-Reste, die mit 3 Darts nicht per Double-Out ausgecheckt werden koennen. */
    private val bogeyRemainders = setOf(169, 168, 166, 165, 163, 162, 159)

    @Test
    fun ohneDoubleOut_immerKeinVorschlag() {
        for (remaining in 0..200) {
            assertNull("Rest $remaining ohne Double-Out", checkoutSuggestion(remaining, false))
        }
    }

    @Test
    fun invarianten_ueberDieGesamteTabelle() {
        for (remaining in 2..170) {
            val route = checkoutSuggestion(remaining, true) ?: continue
            assertEquals("Summe fuer Rest $remaining", remaining, route.sumOf { it.value })
            assertTrue("Groesse 1..3 fuer Rest $remaining", route.size in 1..3)
            assertTrue("Doppel-Ende fuer Rest $remaining", route.last().isDouble)
        }
    }

    @Test
    fun alleNichtBogeyReste_habenEinenVorschlag() {
        for (remaining in 2..170) {
            if (remaining == 1 || remaining in bogeyRemainders) continue
            assertNotNull("Rest $remaining sollte auscheckbar sein", checkoutSuggestion(remaining, true))
        }
    }

    @Test
    fun bogeyResteUndRest1_gebenKeinenVorschlag() {
        assertNull("Rest 1", checkoutSuggestion(1, true))
        bogeyRemainders.forEach { remaining ->
            assertNull("Bogey-Rest $remaining", checkoutSuggestion(remaining, true))
        }
    }

    @Test
    fun ausserhalbDesBereichs_gibtKeinenVorschlag() {
        assertNull(checkoutSuggestion(171, true))
        assertNull(checkoutSuggestion(180, true))
        assertNull(checkoutSuggestion(0, true))
        assertNull(checkoutSuggestion(-5, true))
    }

    @Test
    fun konkreteReferenzRouten() {
        assertEquals(listOf(Dart.double(1)), checkoutSuggestion(2, true))
        assertEquals(listOf(Dart.double(20)), checkoutSuggestion(40, true))
        assertEquals(
            listOf(Dart.triple(20), Dart.double(20)),
            checkoutSuggestion(100, true),
        )
        assertEquals(
            listOf(Dart.triple(20), Dart.triple(20), Dart.doubleBull()),
            checkoutSuggestion(170, true),
        )
    }
}
