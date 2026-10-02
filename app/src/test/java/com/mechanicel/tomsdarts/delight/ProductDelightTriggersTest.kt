package com.mechanicel.tomsdarts.delight

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameModeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JUnit-Tests der Produkt-Trigger ([ProductDelightTriggers], ADR-0041,
 * ADR-0042): 180, Waschmaschine, Rentnerdreieck, Madhouse, Bull-Finish und Ton
 * mit Positiv-/Negativfaellen, Prioritaet und Kollisionen,
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
        scored: Int = if (bust) 0 else darts.sumOf { it.value },
    ) = DelightVisit(
        darts = darts.toList(),
        bust = bust,
        modeKey = modeKey,
        scored = scored,
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
            // Zwei verschiedene Segmente genuegen.
            visit(s(1), s(1), s(5)),
            visit(d(5), t(1), s(20)),
            // Checkout mit drei Darts zaehlt als vollstaendige Aufnahme (Doppel 5,
            // damit nicht Madhouse gewinnt - siehe madhouse_schlaegtWaschmaschine).
            visit(s(20), s(5), d(5), checkout = true),
        )
        positives.forEach { v ->
            assertTrue(v.darts.toString(), ProductDelightTriggers.isWashingMachine(v))
            assertEquals(v.darts.toString(), ProductDelightTriggers.ID_WASHING_MACHINE, winner(v))
        }
    }

    @Test
    fun waschmaschine_negativfaelle() {
        val negatives = listOf(
            // Alle drei auf derselben Zahl (beliebiger Multiplier) ist kein Muster (ADR-0042).
            visit(s(20), s(20), s(20)),
            visit(t(20), d(20), s(20)),
            visit(s(1), s(1), s(1)),
            visit(s(5), s(5), s(5)),
            visit(t(5), d(5), s(5)),
            // Fehlwurf dabei.
            visit(s(20), s(5), Dart.miss()),
            // Nur zwei Darts (Checkout/Bust-Abbruch).
            visit(s(20), s(5)),
            visit(s(5), d(5), checkout = true),
            // Bust.
            visit(s(20), s(5), s(1), bust = true),
            // Fremdes Segment / Mischung mit Rentnerdreieck.
            visit(s(20), s(19), s(1)),
            visit(s(20), s(5), Dart.bull()),
        )
        negatives.forEach { v ->
            assertFalse(v.darts.toString(), ProductDelightTriggers.isWashingMachine(v))
            // Hohe Aufnahmen (z.B. T20/D20/S20) duerfen als Ton feiern - nur nicht als Waschmaschine.
            assertNotEquals(v.darts.toString(), ProductDelightTriggers.ID_WASHING_MACHINE, winner(v))
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
            visit(s(7), s(7), s(3)),
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
            // Alle drei auf derselben Zahl ist kein Dreieck (ADR-0041, ADR-0042).
            visit(s(19), s(19), s(19)),
            visit(t(19), t(19), t(19)),
            visit(s(7), s(7), s(7)),
            visit(s(3), s(3), s(3)),
            visit(d(3), t(3), s(3)),
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
            // Hohe Aufnahmen (z.B. T19/T19/T19) duerfen als Ton feiern - nur nicht als Rentnerdreieck.
            assertNotEquals(v.darts.toString(), ProductDelightTriggers.ID_RENTNERDREIECK, winner(v))
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
        listOf(
            ProductDelightTriggers.MADHOUSE to DelightTextKeys.MADHOUSE,
            ProductDelightTriggers.BULL_FINISH to DelightTextKeys.BULL_FINISH,
            ProductDelightTriggers.TON to DelightTextKeys.TON,
        ).forEach { (trigger, key) ->
            assertEquals(DelightPresentation(DelightAnimation.GENERIC, key), trigger.presentation)
        }
        // Jeder genutzte Text-Schluessel ist bekannt (sonst zeigte die UI den Fallback).
        ProductDelightTriggers.ALL.forEach { assertTrue(it.presentation.textKey in DelightTextKeys.ALL) }
    }

    @Test
    fun all_enthaeltGenauDieProduktTriggerMitEindeutigenIds() {
        assertEquals(
            listOf(
                ProductDelightTriggers.ID_ONE_EIGHTY,
                ProductDelightTriggers.ID_WASHING_MACHINE,
                ProductDelightTriggers.ID_RENTNERDREIECK,
                ProductDelightTriggers.ID_MADHOUSE,
                ProductDelightTriggers.ID_BULL_FINISH,
                ProductDelightTriggers.ID_TON,
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
        // 99 - knapp unter der Ton.
        assertNull(winner(visit(t(20), s(20), s(19))))
        assertNull(winner(visit(Dart.miss(), Dart.miss(), Dart.miss())))
        // Ab 100 waere es in X01 eine Ton - ausserhalb von X01 loest das nichts aus.
        assertNull(winner(visit(t(20), t(20), t(19), modeKey = GameModeCatalog.COUNT_UP)))
        assertNull(winner(visit(Dart.bull(), Dart.doubleBull(), Dart.bull(), modeKey = GameModeCatalog.COUNT_UP)))
    }

    // --- Madhouse -------------------------------------------------------------

    @Test
    fun madhouse_checkoutAufDoppel1() {
        val positives = listOf(
            visit(d(1), checkout = true),
            visit(s(1), d(1), checkout = true),
            visit(Dart.miss(), s(1), d(1), checkout = true),
        )
        positives.forEach { v ->
            assertTrue(v.darts.toString(), ProductDelightTriggers.isMadhouse(v))
            assertEquals(v.darts.toString(), ProductDelightTriggers.ID_MADHOUSE, winner(v))
        }
    }

    @Test
    fun madhouse_negativfaelle() {
        val negatives = listOf(
            // Doppel 1 ohne Checkout (Rest danach noch offen).
            visit(d(1)),
            visit(s(20), d(1)),
            // Checkout auf ein anderes Doppel.
            visit(d(2), checkout = true),
            visit(s(1), d(16), checkout = true),
            // Doppel 1 nicht als letzter Dart.
            visit(d(1), d(2), checkout = true),
            // Single/Triple 1 statt Doppel 1.
            visit(s(1), checkout = true),
            visit(t(1), checkout = true),
            // Bust auf Doppel 1 (auch bei inkonsistent gesetztem Checkout-Flag).
            visit(d(1), bust = true),
            visit(d(1), bust = true, checkout = true),
        )
        negatives.forEach { v ->
            assertFalse(v.darts.toString(), ProductDelightTriggers.isMadhouse(v))
            assertNull(v.darts.toString(), winner(v))
        }
    }

    @Test
    fun madhouse_schlaegtWaschmaschine() {
        // S20/S5/D1 als Checkout ist zugleich ein Waschmaschinen-Muster: das
        // seltenere Finish gewinnt (Prioritaet 80 > 50, ADR-0042).
        val v = visit(s(20), s(5), d(1), checkout = true)
        assertTrue(ProductDelightTriggers.isWashingMachine(v))
        assertEquals(ProductDelightTriggers.ID_MADHOUSE, winner(v))
    }

    @Test
    fun madhouse_schlaegtTon() {
        // 122er-Checkout T20/T20/D1 ist zugleich eine Ton: Madhouse gewinnt (80 > 10).
        val v = visit(t(20), t(20), d(1), checkout = true)
        assertTrue(ProductDelightTriggers.isTon(v))
        assertEquals(ProductDelightTriggers.ID_MADHOUSE, winner(v))
    }

    // --- Bull-Finish ----------------------------------------------------------

    @Test
    fun bullFinish_checkoutAufDoppelBull() {
        val positives = listOf(
            visit(Dart.doubleBull(), checkout = true),
            visit(s(10), Dart.doubleBull(), checkout = true),
            // 170er-Finish: Bull-Finish schlaegt Ton.
            visit(t(20), t(20), Dart.doubleBull(), checkout = true),
            // Gilt in jedem Modus, in dem ein Checkout auf Doppel-Bull endet.
            visit(Dart.doubleBull(), checkout = true, modeKey = GameModeCatalog.CRICKET),
        )
        positives.forEach { v ->
            assertTrue(v.darts.toString(), ProductDelightTriggers.isBullFinish(v))
            assertEquals(v.darts.toString(), ProductDelightTriggers.ID_BULL_FINISH, winner(v))
        }
    }

    @Test
    fun bullFinish_negativfaelle() {
        val negatives = listOf(
            // Single-Bull-Checkout (X01 ohne Double-Out) zaehlt bewusst nicht (ADR-0042).
            visit(Dart.bull(), checkout = true),
            visit(s(20), Dart.bull(), checkout = true),
            // Doppel-Bull ohne Checkout.
            visit(Dart.doubleBull()),
            // Doppel-Bull nicht als letzter Dart.
            visit(Dart.doubleBull(), d(4), checkout = true),
            // Bust.
            visit(Dart.doubleBull(), bust = true),
        )
        negatives.forEach { v ->
            assertFalse(v.darts.toString(), ProductDelightTriggers.isBullFinish(v))
            assertNull(v.darts.toString(), winner(v))
        }
    }

    // --- Ton ------------------------------------------------------------------

    @Test
    fun ton_abHundertInX01() {
        val positives = listOf(
            // Genau 100.
            visit(t(20), s(20), s(20)),
            // 140.
            visit(t(20), t(20), s(20)),
            visit(t(20), t(20), t(19)),
            // Anzahl der Darts egal: zwei Darts, auch als Checkout auf ein gewoehnliches Doppel.
            visit(t(20), t(20)),
            visit(t(20), t(20), d(10), checkout = true),
        )
        positives.forEach { v ->
            assertTrue(v.darts.toString(), ProductDelightTriggers.isTon(v))
            assertEquals(v.darts.toString(), ProductDelightTriggers.ID_TON, winner(v))
        }
    }

    @Test
    fun ton_negativfaelle() {
        // 99 Punkte.
        assertFalse(ProductDelightTriggers.isTon(visit(t(20), s(20), s(19))))
        // Bust trotz roher 100+ (gewertet 0).
        assertFalse(ProductDelightTriggers.isTon(visit(t(20), t(20), s(20), bust = true)))
        assertNull(winner(visit(t(20), t(20), s(20), bust = true)))
        // Massgeblich ist die GEWERTETE Summe, nicht die rohe.
        assertFalse(ProductDelightTriggers.isTon(visit(t(20), t(20), s(20), scored = 99)))
        // Nicht-X01-Modi feiern keine Ton.
        listOf(
            GameModeCatalog.COUNT_UP,
            GameModeCatalog.CRICKET,
            GameModeCatalog.SHANGHAI,
            GameModeCatalog.AROUND_THE_CLOCK,
            GameModeCatalog.KILLER,
        ).forEach { mode ->
            val v = visit(t(20), t(20), s(20), modeKey = mode)
            assertFalse(mode, ProductDelightTriggers.isTon(v))
            assertNull(mode, winner(v))
        }
    }

    @Test
    fun ton_180Gewinnt() {
        val v = visit(t(20), t(20), t(20))
        assertTrue(ProductDelightTriggers.isTon(v))
        assertEquals(ProductDelightTriggers.ID_ONE_EIGHTY, winner(v))
    }

    @Test
    fun ton_musterSchlaegtTon() {
        // T20/T20/S5 = 125: Waschmaschine (50) schlaegt Ton (10) - gewollt (ADR-0042).
        val washingMachine = visit(t(20), t(20), s(5))
        assertTrue(ProductDelightTriggers.isTon(washingMachine))
        assertEquals(ProductDelightTriggers.ID_WASHING_MACHINE, winner(washingMachine))
        // T19/T19/S7 = 121: Rentnerdreieck schlaegt Ton.
        val rentnerdreieck = visit(t(19), t(19), s(7))
        assertTrue(ProductDelightTriggers.isTon(rentnerdreieck))
        assertEquals(ProductDelightTriggers.ID_RENTNERDREIECK, winner(rentnerdreieck))
    }

    // --- Prioritaetsschema ----------------------------------------------------

    @Test
    fun prioritaetsschema_vollstaendigGeordnet() {
        val p = ProductDelightTriggers
        assertEquals(p.PRIORITY_MAX_SCORE, p.ONE_EIGHTY.priority)
        assertEquals(p.PRIORITY_MADHOUSE, p.MADHOUSE.priority)
        assertEquals(p.PRIORITY_BULL_FINISH, p.BULL_FINISH.priority)
        assertEquals(p.PRIORITY_PATTERN, p.RENTNERDREIECK.priority)
        assertEquals(p.PRIORITY_TON, p.TON.priority)
        assertTrue(p.PRIORITY_MAX_SCORE > p.PRIORITY_MADHOUSE)
        assertTrue(p.PRIORITY_MADHOUSE > p.PRIORITY_BULL_FINISH)
        assertTrue(p.PRIORITY_BULL_FINISH > p.PRIORITY_PATTERN)
        assertTrue(p.PRIORITY_PATTERN > p.PRIORITY_TON)
    }
}
