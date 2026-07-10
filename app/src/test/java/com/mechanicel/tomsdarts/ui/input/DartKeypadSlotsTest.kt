package com.mechanicel.tomsdarts.ui.input

import com.mechanicel.tomsdarts.game.Dart
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reine JUnit-Tests fuer die [slotContents]-Zuordnung. Keine Android-/Compose-/
 * Robolectric-Abhaengigkeit - laeuft direkt auf der JVM (Happy-Path-Basis).
 *
 * Kern des Bugfixes: der Checkout-Vorschlag darf nur als Geist erscheinen, wenn
 * er vollstaendig in die noch freien Slots passt (`checkout.size <= freieSlots`).
 */
class DartKeypadSlotsTest {

    @Test
    fun keinCheckout_alleSlotsLeer() {
        val result = slotContents(thrownDarts = emptyList(), checkout = null)
        assertEquals(
            listOf(SlotContent.Empty, SlotContent.Empty, SlotContent.Empty),
            result,
        )
    }

    @Test
    fun nullGeworfen_170_dreiGeister() {
        val checkout = listOf(Dart.triple(20), Dart.triple(20), Dart.doubleBull())
        val result = slotContents(thrownDarts = emptyList(), checkout = checkout)
        assertEquals(
            listOf(
                SlotContent.Ghost(Dart.triple(20)),
                SlotContent.Ghost(Dart.triple(20)),
                SlotContent.Ghost(Dart.doubleBull()),
            ),
            result,
        )
    }

    @Test
    fun einGeworfen_restCheckout_realDannZweiGeister() {
        val checkout = listOf(Dart.triple(20), Dart.doubleBull())
        val result = slotContents(
            thrownDarts = listOf(Dart.triple(20)),
            checkout = checkout,
        )
        assertEquals(
            listOf(
                SlotContent.Thrown(Dart.triple(20)),
                SlotContent.Ghost(Dart.triple(20)),
                SlotContent.Ghost(Dart.doubleBull()),
            ),
            result,
        )
    }

    @Test
    fun nullGeworfen_40_einGeistRestLeer() {
        val checkout = listOf(Dart.double(20))
        val result = slotContents(thrownDarts = emptyList(), checkout = checkout)
        assertEquals(
            listOf(
                SlotContent.Ghost(Dart.double(20)),
                SlotContent.Empty,
                SlotContent.Empty,
            ),
            result,
        )
    }

    @Test
    fun zweiGeworfen_zweiDartCheckout_passtNicht_keinGeist() {
        // freeSlots == 1, checkout.size == 2 -> passt nicht -> kein Geist.
        val checkout = listOf(Dart.triple(20), Dart.doubleBull())
        val result = slotContents(
            thrownDarts = listOf(Dart.single(20), Dart.single(20)),
            checkout = checkout,
        )
        assertEquals(
            listOf(
                SlotContent.Thrown(Dart.single(20)),
                SlotContent.Thrown(Dart.single(20)),
                SlotContent.Empty,
            ),
            result,
        )
    }

    @Test
    fun zweiGeworfen_einDartCheckout_passt_einGeist() {
        // freeSlots == 1, checkout.size == 1 -> passt genau.
        val checkout = listOf(Dart.double(20))
        val result = slotContents(
            thrownDarts = listOf(Dart.single(20), Dart.single(20)),
            checkout = checkout,
        )
        assertEquals(
            listOf(
                SlotContent.Thrown(Dart.single(20)),
                SlotContent.Thrown(Dart.single(20)),
                SlotContent.Ghost(Dart.double(20)),
            ),
            result,
        )
    }

    @Test
    fun vollerZug_dreiGeworfen_keinGeist() {
        val checkout = listOf(Dart.double(20))
        val result = slotContents(
            thrownDarts = listOf(Dart.single(20), Dart.single(20), Dart.single(20)),
            checkout = checkout,
        )
        assertEquals(
            listOf(
                SlotContent.Thrown(Dart.single(20)),
                SlotContent.Thrown(Dart.single(20)),
                SlotContent.Thrown(Dart.single(20)),
            ),
            result,
        )
    }

    @Test
    fun leererCheckout_wirdBehandeltAlsPasstImmer_keineGeister() {
        // Ein leerer Vorschlag (size 0) erzeugt keine Geister, bleibt aber ohne
        // Fehler: alle freien Slots leer.
        val result = slotContents(
            thrownDarts = listOf(Dart.single(20)),
            checkout = emptyList(),
        )
        assertEquals(
            listOf(
                SlotContent.Thrown(Dart.single(20)),
                SlotContent.Empty,
                SlotContent.Empty,
            ),
            result,
        )
    }
}
