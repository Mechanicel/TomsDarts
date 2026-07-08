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

    /**
     * Randroute mit dem kleinsten sinnvollen Rest, der zwei Darts braucht (1 ist
     * kein gueltiger erster Dart-Wert, aber Rest 3 laesst sich per "1, D-1" in
     * zwei Darts loesen). Belegt, dass auch kleine, wenig "runde" Reste eine
     * valide Route bekommen.
     */
    @Test
    fun rest3_liefertGueltigeZweiDartRoute() {
        val route = checkoutSuggestion(3, true)
        assertNotNull(route)
        assertEquals(3, route!!.sumOf { it.value })
        assertTrue(route.last().isDouble)
        assertTrue(route.size in 1..3)
    }

    /** Rest 50 laesst sich mit einem Dart per Doppel-Bull auschecken. */
    @Test
    fun rest50_endetAufDoppelBull() {
        assertEquals(listOf(Dart.doubleBull()), checkoutSuggestion(50, true))
    }

    /**
     * 170 ist der hoechste per Double-Out auscheckbare Rest (3x T-20 waeren 180,
     * aber der letzte Dart muss ein Doppel sein). 171 liegt bereits ausserhalb.
     */
    @Test
    fun rest170_istDerGroessteAuscheckbareRest() {
        assertNotNull(checkoutSuggestion(170, true))
        assertNull(checkoutSuggestion(171, true))
    }

    /**
     * Exhaustive Vollstaendigkeits- und Null-Mengen-Pruefung ueber einen weiten
     * Bereich (inkl. negativer Reste und Reste weit ueber 170): Genau die
     * definierten Ausschluesse (r < 2, r > 170, Rest 1, die sieben Bogey-Reste)
     * liefern `null` -- jeder andere Rest 2..170 liefert eine Route. Ein
     * abweichendes Verhalten (ein checkbarer Rest liefert faelschlich `null` oder
     * ein Bogey-/Grenzrest liefert einen Vorschlag) waere ein Korrektheitsbug in
     * [buildCheckoutTable]/[bestRoute].
     */
    @Test
    fun vollstaendigkeitUndNullmenge_ueberDenGesamtenBereich() {
        for (remaining in -10..200) {
            val expectNull = remaining < 2 || remaining > 170 || remaining == 1 ||
                remaining in bogeyRemainders
            val route = checkoutSuggestion(remaining, true)
            if (expectNull) {
                assertNull("Rest $remaining sollte keinen Vorschlag liefern, war $route", route)
            } else {
                assertNotNull("Rest $remaining sollte einen Vorschlag liefern", route)
            }
        }
    }

    /**
     * Jeder Dart jeder Route ist auf einem echten Dartboard werfbar (siehe
     * [Dart.isValid]): keine erfundenen Wuerfe wie Triple-Bull oder Multiplier
     * ausserhalb 1..3. Deckt insbesondere ab, dass Doppel-Bull als Finish nie mit
     * `multiplier == 3` verwechselt wird.
     */
    @Test
    fun jederDartInJederRoute_istPhysikalischGueltig() {
        for (remaining in 2..170) {
            val route = checkoutSuggestion(remaining, true) ?: continue
            route.forEachIndexed { index, dart ->
                assertTrue(
                    "Dart $index ($dart) in Route fuer Rest $remaining ist physikalisch ungueltig",
                    dart.isValid,
                )
            }
        }
    }

    /**
     * Die Tabelle wird einmalig gebaut und zwischengespeichert: wiederholte
     * Aufrufe fuer denselben Rest liefern nicht nur inhaltlich gleiche, sondern
     * dieselbe (referenzgleiche) Liste -- ein Beleg dafuer, dass hier keine
     * Neuberechnung pro Aufruf passiert.
     */
    @Test
    fun wiederholteAufrufe_liefernDieselbeGecachteRoute() {
        listOf(2, 40, 50, 100, 170, 3, 32).forEach { remaining ->
            val first = checkoutSuggestion(remaining, true)
            val second = checkoutSuggestion(remaining, true)
            assertEquals("Rest $remaining", first, second)
            assertTrue(
                "Rest $remaining sollte dieselbe (gecachte) Listeninstanz liefern",
                first === second,
            )
        }
    }
}
