package com.mechanicel.tomsdarts.delight

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameModeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JUnit-Tests der Produkt-Trigger ([ProductDelightTriggers], ADR-0041):
 * 180, Waschmaschine und Rentnerdreieck mit Positiv-/Negativfaellen, Prioritaet,
 * Darstellung (Animation + Text-Schluessel) und Zusammensetzung von
 * [ProductDelightTriggers.ALL].
 */
class ProductDelightTriggersTest {

    // --- Helfer ---------------------------------------------------------------

    private fun visit(
        vararg darts: Dart,
        bust: Boolean = false,
        checkout: Boolean = false,
        modeKey: String = GameModeCatalog.X01,
    ) = DelightVisit(
        darts = darts.toList(),
        bust = bust,
        modeKey = modeKey,
        scored = if (bust) 0 else darts.sumOf { it.value },
        checkout = checkout,
        legEnded = checkout,
        playerId = 1L,
    )

    private fun s(n: Int) = Dart.single(n)
    private fun d(n: Int) = Dart.double(n)
    private fun t(n: Int) = Dart.triple(n)

    /** Gewinnende Trigger-ID der Produkt-Registry oder `null`. */
    private fun winner(v: DelightVisit): String? = DelightRegistry.DEFAULT.evaluate(v)?.triggerId

    // --- 180 ------------------------------------------------------------------

    @Test
    fun oneEighty_dreiMalTriple20() {
        assertTrue(ProductDelightTriggers.isOneEighty(visit(t(20), t(20), t(20))))
        assertEquals(ProductDelightTriggers.ID_ONE_EIGHTY, winner(visit(t(20), t(20), t(20))))
    }

    @Test
    fun oneEighty_negativfaelle() {
        // Nur zwei Triple 20 (z.B. Abbruch vor dem dritten Dart).
        assertFalse(ProductDelightTriggers.isOneEighty(visit(t(20), t(20))))
        // Bust trotz roher 180.
        assertFalse(ProductDelightTriggers.isOneEighty(visit(t(20), t(20), t(20), bust = true)))
        // Ein Dart daneben.
        assertFalse(ProductDelightTriggers.isOneEighty(visit(t(20), t(20), d(20))))
        assertFalse(ProductDelightTriggers.isOneEighty(visit(t(20), t(20), Dart.miss())))
        // Rohe Summe 180 ist ohne drei Triple 20 nicht erreichbar, aber andere Triple zaehlen nicht.
        assertFalse(ProductDelightTriggers.isOneEighty(visit(t(19), t(20), t(20))))
    }

    @Test
    fun oneEighty_giltInAllenModi() {
        listOf(GameModeCatalog.X01, GameModeCatalog.COUNT_UP, GameModeCatalog.CRICKET).forEach { mode ->
            assertEquals(
                mode,
                ProductDelightTriggers.ID_ONE_EIGHTY,
                winner(visit(t(20), t(20), t(20), modeKey = mode)),
            )
        }
    }

    // --- Waschmaschine --------------------------------------------------------

    @Test
    fun waschmaschine_positivfaelle() {
        val positives = listOf(
            visit(s(20), s(5), s(1)),
            visit(t(20), t(20), s(5)),
            visit(s(5), s(5), s(1)),
            visit(s(1), s(1), s(1)),
            visit(d(5), t(1), s(20)),
            // Checkout mit drei Darts zaehlt als vollstaendige Aufnahme.
            visit(s(20), s(5), d(1), checkout = true),
        )
        positives.forEach { v ->
            assertTrue(v.darts.toString(), ProductDelightTriggers.isWashingMachine(v))
            assertEquals(v.darts.toString(), ProductDelightTriggers.ID_WASHING_MACHINE, winner(v))
        }
    }

    @Test
    fun waschmaschine_negativfaelle() {
        val negatives = listOf(
            // Alle drei auf 20 (beliebiger Multiplier) ist keine Waschmaschine.
            visit(s(20), s(20), s(20)),
            visit(t(20), d(20), s(20)),
            // Fehlwurf dabei.
            visit(s(20), s(5), Dart.miss()),
            // Nur zwei Darts (Checkout/Bust-Abbruch).
            visit(s(20), s(5)),
            visit(s(5), d(1), checkout = true),
            // Bust.
            visit(s(20), s(5), s(1), bust = true),
            // Fremdes Segment / Mischung mit Rentnerdreieck.
            visit(s(20), s(19), s(1)),
            visit(s(20), s(5), Dart.bull()),
        )
        negatives.forEach { v ->
            assertFalse(v.darts.toString(), ProductDelightTriggers.isWashingMachine(v))
            assertNull(v.darts.toString(), winner(v))
        }
    }

    @Test
    fun waschmaschine_inNichtX01Modus() {
        assertEquals(
            ProductDelightTriggers.ID_WASHING_MACHINE,
            winner(visit(s(20), s(5), s(1), modeKey = GameModeCatalog.COUNT_UP)),
        )
    }

    // --- Rentnerdreieck -------------------------------------------------------

    @Test
    fun rentnerdreieck_positivfaelle() {
        val positives = listOf(
            visit(s(19), s(7), s(3)),
            visit(t(19), s(7), d(3)),
            visit(s(7), s(7), s(7)),
            visit(t(19), t(19), s(3)),
        )
        positives.forEach { v ->
            assertTrue(v.darts.toString(), ProductDelightTriggers.isRentnerdreieck(v))
            assertEquals(v.darts.toString(), ProductDelightTriggers.ID_RENTNERDREIECK, winner(v))
        }
    }

    @Test
    fun rentnerdreieck_negativfaelle() {
        val negatives = listOf(
            // Alle drei auf 19 ist kein Dreieck (ADR-0041, analog Waschmaschine).
            visit(s(19), s(19), s(19)),
            visit(t(19), t(19), t(19)),
            // Fehlwurf, zwei Darts, Bust.
            visit(s(19), s(7), Dart.miss()),
            visit(s(19), s(7)),
            visit(s(19), s(7), s(3), bust = true),
            // Mischung mit Waschmaschinen-Segmenten.
            visit(s(19), s(7), s(20)),
            visit(s(19), s(20), s(1)),
        )
        negatives.forEach { v ->
            assertFalse(v.darts.toString(), ProductDelightTriggers.isRentnerdreieck(v))
            assertNull(v.darts.toString(), winner(v))
        }
    }

    // --- Prioritaet / Zuordnung -----------------------------------------------

    @Test
    fun prioritaet_180VorMustern() {
        assertTrue(ProductDelightTriggers.ONE_EIGHTY.priority > ProductDelightTriggers.WASHING_MACHINE.priority)
        assertTrue(ProductDelightTriggers.ONE_EIGHTY.priority > ProductDelightTriggers.RENTNERDREIECK.priority)
        assertEquals(ProductDelightTriggers.WASHING_MACHINE.priority, ProductDelightTriggers.RENTNERDREIECK.priority)
        assertEquals(ProductDelightTriggers.PRIORITY_MAX_SCORE, ProductDelightTriggers.ONE_EIGHTY.priority)
        assertEquals(ProductDelightTriggers.PRIORITY_PATTERN, ProductDelightTriggers.WASHING_MACHINE.priority)
    }

    @Test
    fun prioritaet_180GewinntAuchGegenPassendeMusterBedingung() {
        // Kuenstlich: Muster-Trigger, der auch bei 180 zutrifft -> 180 gewinnt trotzdem.
        val greedyPattern = ProductDelightTriggers.WASHING_MACHINE.copy(id = "greedy", condition = { true })
        val event = evaluateDelight(
            visit(t(20), t(20), t(20)),
            listOf(greedyPattern, ProductDelightTriggers.ONE_EIGHTY),
        )
        assertEquals(ProductDelightTriggers.ID_ONE_EIGHTY, event?.triggerId)
    }

    @Test
    fun darstellung_animationUndTextSchluessel() {
        assertEquals(
            DelightPresentation(DelightAnimation.CONFETTI, DelightTextKeys.ONE_EIGHTY),
            ProductDelightTriggers.ONE_EIGHTY.presentation,
        )
        assertEquals(
            DelightPresentation(DelightAnimation.SPIN, DelightTextKeys.WASHING_MACHINE),
            ProductDelightTriggers.WASHING_MACHINE.presentation,
        )
        assertEquals(
            DelightPresentation(DelightAnimation.TRIANGLE, DelightTextKeys.RENTNERDREIECK),
            ProductDelightTriggers.RENTNERDREIECK.presentation,
        )
        // Jeder genutzte Text-Schluessel ist bekannt (sonst zeigte die UI den Fallback).
        ProductDelightTriggers.ALL.forEach { assertTrue(it.presentation.textKey in DelightTextKeys.ALL) }
    }

    @Test
    fun all_enthaeltGenauDieDreiTriggerMitEindeutigenIds() {
        assertEquals(
            listOf(
                ProductDelightTriggers.ID_ONE_EIGHTY,
                ProductDelightTriggers.ID_WASHING_MACHINE,
                ProductDelightTriggers.ID_RENTNERDREIECK,
            ),
            ProductDelightTriggers.ALL.map { it.id },
        )
        val ids = ProductDelightTriggers.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(ProductDelightTriggers.ALL, DelightRegistry.DEFAULT.triggers)
    }

    @Test
    fun gewoehnlicheAufnahmen_loesenNichtsAus() {
        assertNull(winner(visit(s(20), s(20), s(19))))
        assertNull(winner(visit(t(20), t(20), t(19))))
        assertNull(winner(visit(Dart.miss(), Dart.miss(), Dart.miss())))
        assertNull(winner(visit(Dart.bull(), Dart.doubleBull(), Dart.bull())))
    }
}
