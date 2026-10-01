package com.mechanicel.tomsdarts.delight

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameModeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JUnit-Tests des Delight-Trigger-Systems ([evaluateDelight],
 * [DelightRegistry]): Prioritaet, Gleichstand per Registrierungsreihenfolge, kein
 * Treffer, Bedingungen auf Bust/Checkout sowie die in ADR-0006 genannten
 * Trigger-Formen als Machbarkeitsnachweis des Modells.
 */
class DelightRegistryTest {

    // --- Helfer ---------------------------------------------------------------

    private fun visit(
        vararg darts: Dart,
        bust: Boolean = false,
        checkout: Boolean = false,
        legEnded: Boolean = checkout,
        modeKey: String = GameModeCatalog.X01,
        scored: Int = if (bust) 0 else darts.sumOf { it.value },
    ) = DelightVisit(
        darts = darts.toList(),
        bust = bust,
        modeKey = modeKey,
        scored = scored,
        checkout = checkout,
        legEnded = legEnded,
        playerId = 7L,
    )

    private fun trigger(
        id: String,
        priority: Int = 0,
        animation: DelightAnimation = DelightAnimation.GENERIC,
        condition: (DelightVisit) -> Boolean,
    ) = DelightTrigger(
        id = id,
        priority = priority,
        condition = condition,
        presentation = DelightPresentation(animation, "text_$id"),
    )

    private val always: (DelightVisit) -> Boolean = { true }

    // --- Grundverhalten -------------------------------------------------------

    @Test
    fun keinTrigger_liefertNull() {
        assertNull(evaluateDelight(visit(Dart.triple(20)), emptyList()))
    }

    @Test
    fun keineBedingungTrifft_liefertNull() {
        val triggers = listOf(trigger("a") { false }, trigger("b") { it.dartSum > 100 })
        assertNull(evaluateDelight(visit(Dart.single(1), Dart.single(1), Dart.single(1)), triggers))
    }

    @Test
    fun treffer_liefertEventMitPresentationUndVisit() {
        val t = trigger("180", animation = DelightAnimation.CONFETTI) { it.dartSum == 180 }
        val v = visit(Dart.triple(20), Dart.triple(20), Dart.triple(20))

        val event = evaluateDelight(v, listOf(t))!!

        assertEquals("180", event.triggerId)
        assertEquals(DelightPresentation(DelightAnimation.CONFETTI, "text_180"), event.presentation)
        assertSame(v, event.visit)
        assertEquals(0L, event.id)
        assertEquals(7L, event.playerId)
    }

    @Test
    fun idWirdDurchgereicht_playerIdKannNullSein() {
        val t = trigger("any", condition = always)
        val v = visit(Dart.miss()).copy(playerId = null)

        val event = DelightRegistry(listOf(t)).evaluate(v, id = 42L)!!

        assertEquals(42L, event.id)
        assertNull(event.playerId)
    }

    // --- Prioritaet / Gleichstand --------------------------------------------

    @Test
    fun hoechstePrioritaetGewinnt_unabhaengigVonReihenfolge() {
        val low = trigger("low", priority = 1, condition = always)
        val high = trigger("high", priority = 10, condition = always)
        val mid = trigger("mid", priority = 5, condition = always)

        assertEquals("high", evaluateDelight(visit(Dart.miss()), listOf(low, high, mid))!!.triggerId)
        assertEquals("high", evaluateDelight(visit(Dart.miss()), listOf(high, mid, low))!!.triggerId)
    }

    @Test
    fun gleichstand_ersterRegistrierterGewinnt() {
        val first = trigger("first", priority = 3, condition = always)
        val second = trigger("second", priority = 3, condition = always)

        assertEquals("first", evaluateDelight(visit(Dart.miss()), listOf(first, second))!!.triggerId)
        assertEquals("second", evaluateDelight(visit(Dart.miss()), listOf(second, first))!!.triggerId)
    }

    @Test
    fun hoeherPriorisierterOhneTreffer_niedrigererMitTrefferGewinnt() {
        val high = trigger("high", priority = 10) { false }
        val low = trigger("low", priority = -5, condition = always)

        assertEquals("low", evaluateDelight(visit(Dart.miss()), listOf(high, low))!!.triggerId)
    }

    @Test
    fun nachErstemTrefferWerdenNiedrigerPriorisierteNichtAusgewertet() {
        var lowEvaluated = false
        val high = trigger("high", priority = 2, condition = always)
        val low = trigger("low", priority = 1) { lowEvaluated = true; true }

        evaluateDelight(visit(Dart.miss()), listOf(low, high))

        assertEquals(false, lowEvaluated)
    }

    @Test
    fun deterministisch_mehrfacheAuswertungLiefertDasselbe() {
        val triggers = listOf(
            trigger("a", priority = 1, condition = always),
            trigger("b", priority = 1, condition = always),
        )
        val v = visit(Dart.single(5))
        val results = (1..5).map { evaluateDelight(v, triggers)!!.triggerId }
        assertEquals(List(5) { "a" }, results)
    }

    // --- Bedingungen auf Bust / Checkout --------------------------------------

    @Test
    fun bustBedingung_trifftNurBeiBust() {
        val t = trigger("bust") { it.bust }
        assertEquals("bust", evaluateDelight(visit(Dart.single(20), bust = true), listOf(t))!!.triggerId)
        assertNull(evaluateDelight(visit(Dart.single(20)), listOf(t)))
    }

    @Test
    fun nichtBustBedingung_unterdruecktBustAufnahme() {
        // 180 roh geworfen, aber als Bust gewertet -> ein "kein Bust"-Trigger feiert nicht.
        val t = trigger("180") { !it.bust && it.dartSum == 180 }
        val bustVisit = visit(Dart.triple(20), Dart.triple(20), Dart.triple(20), bust = true)
        assertEquals(0, bustVisit.scored)
        assertEquals(180, bustVisit.dartSum)
        assertNull(evaluateDelight(bustVisit, listOf(t)))
    }

    @Test
    fun checkoutBedingung_madhausAufD1() {
        val madhaus = trigger("madhaus") { it.checkout && it.darts.lastOrNull() == Dart.double(1) }
        assertEquals(
            "madhaus",
            evaluateDelight(visit(Dart.single(1), Dart.double(1), checkout = true), listOf(madhaus))!!.triggerId,
        )
        // D1 ohne Checkout (z.B. Bust auf Rest 1) feiert nicht.
        assertNull(evaluateDelight(visit(Dart.double(1), bust = true), listOf(madhaus)))
        // Checkout auf anderem Double feiert nicht.
        assertNull(evaluateDelight(visit(Dart.double(20), checkout = true), listOf(madhaus)))
    }

    // --- Machbarkeit der ADR-0006-Trigger ohne Modell-Aenderung ---------------

    @Test
    fun adr0006Trigger_sindAufDemModellFormulierbar() {
        val waschmaschine = trigger("waschmaschine", animation = DelightAnimation.SPIN) { v ->
            v.darts.size == 3 && v.darts.all { it.segment in setOf(20, 5, 1) } &&
                !v.darts.all { it.segment == 20 }
        }
        val rentnerdreieck = trigger("rentnerdreieck", animation = DelightAnimation.TRIANGLE) { v ->
            v.darts.size == 3 && v.darts.all { it.segment in setOf(19, 7, 3) }
        }
        val bull = trigger("bull") { v -> v.darts.any { it.segment == 25 } }
        val ton = trigger("ton") { v -> !v.bust && v.dartSum >= 100 }
        val triggers = listOf(waschmaschine, rentnerdreieck, bull, ton)

        assertEquals(
            "waschmaschine",
            evaluateDelight(visit(Dart.single(20), Dart.single(5), Dart.single(1)), triggers)!!.triggerId,
        )
        assertEquals(
            "rentnerdreieck",
            evaluateDelight(visit(Dart.single(19), Dart.single(7), Dart.single(3)), triggers)!!.triggerId,
        )
        assertEquals(
            "bull",
            evaluateDelight(visit(Dart.bull(), Dart.miss(), Dart.miss()), triggers)!!.triggerId,
        )
        assertEquals(
            "ton",
            evaluateDelight(visit(Dart.triple(20), Dart.single(20), Dart.single(20)), triggers)!!.triggerId,
        )
        // Drei Mal 20 ist keine Waschmaschine.
        assertNull(evaluateDelight(visit(Dart.single(20), Dart.single(20), Dart.single(20)), triggers))
    }

    @Test
    fun dartSum_istRoheSummeUnabhaengigVonScored() {
        val v = visit(Dart.triple(20), Dart.double(25), modeKey = GameModeCatalog.CRICKET, scored = 3)
        assertEquals(110, v.dartSum)
        assertEquals(3, v.scored)
    }

    // --- DelightRegistry ------------------------------------------------------

    @Test
    fun registry_evaluateDelegiertMitRegistrierungsreihenfolge() {
        val registry = DelightRegistry(
            listOf(
                trigger("first", priority = 1, condition = always),
                trigger("second", priority = 1, condition = always),
            ),
        )
        assertEquals("first", registry.evaluate(visit(Dart.miss()))!!.triggerId)
    }

    @Test
    fun registry_kopiertListe() {
        val source = mutableListOf(trigger("a", condition = always))
        val registry = DelightRegistry(source)
        source.clear()
        assertEquals(1, registry.triggers.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun registry_doppelteIdWirft() {
        DelightRegistry(listOf(trigger("x", condition = always), trigger("x", condition = always)))
    }

    @Test
    fun emptyRegistry_loestNieAus() {
        assertNull(DelightRegistry.EMPTY.evaluate(visit(Dart.triple(20), Dart.triple(20), Dart.triple(20))))
    }

    @Test
    fun defaultRegistry_entsprichtProduktTriggern() {
        assertEquals(ProductDelightTriggers.ALL, DelightRegistry.DEFAULT.triggers)
        // Produkt-Trigger-IDs muessen eindeutig sein (sonst wirft DEFAULT beim Laden).
        val ids = ProductDelightTriggers.ALL.map { it.id }
        assertTrue(ids.size == ids.toSet().size)
    }
}
