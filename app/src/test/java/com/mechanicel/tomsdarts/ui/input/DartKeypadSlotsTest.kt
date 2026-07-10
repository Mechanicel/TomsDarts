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

    // --- Fit-Grenzen (Kern des Bugfixes): checkout.size == freeSlots vs. freeSlots + 1 ---

    @Test
    fun nullGeworfen_checkoutGenauFreieSlots_dreiGeister() {
        // thrown=0, freeSlots=3, checkout.size=3 -> exakte Grenze: passt, alle drei
        // Slots werden zu Geistern.
        val checkout = listOf(Dart.triple(20), Dart.triple(19), Dart.doubleBull())
        val result = slotContents(thrownDarts = emptyList(), checkout = checkout)
        assertEquals(
            listOf(
                SlotContent.Ghost(Dart.triple(20)),
                SlotContent.Ghost(Dart.triple(19)),
                SlotContent.Ghost(Dart.doubleBull()),
            ),
            result,
        )
    }

    @Test
    fun nullGeworfen_checkoutEinsUeberFreieSlots_keinGeist() {
        // thrown=0, freeSlots=3, checkout.size=4 -> ueberschreitet die Grenze um
        // genau eins: kein Geist, alle drei Slots bleiben leer.
        val checkout = listOf(
            Dart.triple(20),
            Dart.triple(19),
            Dart.triple(18),
            Dart.doubleBull(),
        )
        val result = slotContents(thrownDarts = emptyList(), checkout = checkout)
        assertEquals(
            listOf(SlotContent.Empty, SlotContent.Empty, SlotContent.Empty),
            result,
        )
    }

    @Test
    fun einGeworfen_checkoutGenauFreieSlots_zweiGeister() {
        // thrown=1, freeSlots=2, checkout.size=2 -> exakte Grenze: passt.
        // (Regressionsvariante zu einGeworfen_restCheckout_realDannZweiGeister,
        // hier explizit als Grenzwert-Fall benannt.)
        val checkout = listOf(Dart.triple(20), Dart.doubleBull())
        val result = slotContents(
            thrownDarts = listOf(Dart.single(20)),
            checkout = checkout,
        )
        assertEquals(
            listOf(
                SlotContent.Thrown(Dart.single(20)),
                SlotContent.Ghost(Dart.triple(20)),
                SlotContent.Ghost(Dart.doubleBull()),
            ),
            result,
        )
    }

    @Test
    fun einGeworfen_checkoutEinsUeberFreieSlots_keinGeist() {
        // thrown=1, freeSlots=2, checkout.size=3 -> ueberschreitet die Grenze um
        // genau eins: kein Geist, die beiden freien Slots bleiben leer.
        val checkout = listOf(Dart.triple(20), Dart.triple(19), Dart.doubleBull())
        val result = slotContents(
            thrownDarts = listOf(Dart.single(20)),
            checkout = checkout,
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

    @Test
    fun zweiGeworfen_checkoutGenauFreieSlots_einGeist() {
        // thrown=2, freeSlots=1, checkout.size=1 -> exakte Grenze: passt.
        // (Regressionsvariante zu zweiGeworfen_einDartCheckout_passt_einGeist,
        // hier explizit als Grenzwert-Fall benannt.)
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
    fun zweiGeworfen_checkoutEinsUeberFreieSlots_keinGeist() {
        // thrown=2, freeSlots=1, checkout.size=2 -> ueberschreitet die Grenze um
        // genau eins (identisch zu zweiGeworfen_zweiDartCheckout_passtNicht_keinGeist,
        // hier explizit als "freeSlots+1"-Grenzfall benannt).
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

    // --- Ghost-Platzierung: Geister beginnen exakt am ersten leeren Slot ---

    @Test
    fun ghostPlatzierung_beginntGenauAmErstenLeerenSlot_groessererSlotCount() {
        // slotCount=5, thrown=2, freeSlots=3, checkout.size=2 -> Geister muessen
        // bei Index 2 und 3 stehen (direkt nach den geworfenen Darts), Index 4
        // bleibt leer (Geist ist kuerzer als die freien Slots).
        val thrownDarts = listOf(Dart.single(1), Dart.single(2))
        val checkout = listOf(Dart.triple(20), Dart.doubleBull())
        val result = slotContents(thrownDarts = thrownDarts, checkout = checkout, slotCount = 5)
        assertEquals(
            listOf(
                SlotContent.Thrown(Dart.single(1)),
                SlotContent.Thrown(Dart.single(2)),
                SlotContent.Ghost(Dart.triple(20)),
                SlotContent.Ghost(Dart.doubleBull()),
                SlotContent.Empty,
            ),
            result,
        )
    }

    @Test
    fun ghostKuerzerAlsFreieSlots_nichtRechtsbuendigNichtVerteilt() {
        // thrown=0, slotCount=3, 1-Dart-Checkout -> Geist steht an Index 0
        // (direkt am Anfang), nicht am Ende und nicht verteilt.
        val checkout = listOf(Dart.double(20))
        val result = slotContents(thrownDarts = emptyList(), checkout = checkout, slotCount = 3)
        assertEquals(SlotContent.Ghost(Dart.double(20)), result[0])
        assertEquals(SlotContent.Empty, result[1])
        assertEquals(SlotContent.Empty, result[2])
    }

    // --- slotCount-Parametrisierung ---

    @Test
    fun slotCountEins_checkoutPasstGenau_einGeist() {
        val result = slotContents(
            thrownDarts = emptyList(),
            checkout = listOf(Dart.double(20)),
            slotCount = 1,
        )
        assertEquals(listOf(SlotContent.Ghost(Dart.double(20))), result)
    }

    @Test
    fun slotCountEins_checkoutZuGross_keinGeist() {
        val result = slotContents(
            thrownDarts = emptyList(),
            checkout = listOf(Dart.triple(20), Dart.doubleBull()),
            slotCount = 1,
        )
        assertEquals(listOf(SlotContent.Empty), result)
    }

    @Test
    fun defaultSlotCount_entsprichtDreiMaxDarts() {
        // Ohne expliziten slotCount-Parameter muss slotContents exakt drei Slots
        // liefern (DartInputState.MAX_DARTS).
        val result = slotContents(thrownDarts = emptyList(), checkout = null)
        assertEquals(DartInputState.MAX_DARTS, result.size)
    }

    // --- Randfaelle ---

    @Test
    fun thrownGroesserAlsSlotCount_keinCrash_alleThrownAusDenErstenSlots() {
        // Sollte in der App nicht vorkommen (mehr geworfene Darts als Slots),
        // aber die Funktion muss robust bleiben: kein Crash, kein Geist, und die
        // ersten slotCount Eintraege werden aus thrownDarts befuellt (Rest wird
        // stillschweigend nicht dargestellt).
        val thrownDarts = listOf(
            Dart.single(1),
            Dart.single(2),
            Dart.single(3),
            Dart.single(4),
        )
        val result = slotContents(thrownDarts = thrownDarts, checkout = listOf(Dart.double(20)), slotCount = 3)
        assertEquals(
            listOf(
                SlotContent.Thrown(Dart.single(1)),
                SlotContent.Thrown(Dart.single(2)),
                SlotContent.Thrown(Dart.single(3)),
            ),
            result,
        )
    }

    // --- Ergebnis-Invarianten ---

    @Test
    fun ergebnisInvarianten_groesseUndReihenfolgeStimmenUeberMehrereSzenarien() {
        data class Szenario(
            val thrown: List<Dart>,
            val checkout: List<Dart>?,
            val slotCount: Int,
        )

        val szenarien = listOf(
            Szenario(emptyList(), null, 3),
            Szenario(listOf(Dart.single(5)), listOf(Dart.double(20), Dart.doubleBull()), 3),
            Szenario(
                listOf(Dart.single(5), Dart.single(6)),
                listOf(Dart.double(20)),
                3,
            ),
            Szenario(emptyList(), listOf(Dart.triple(20)), 5),
            Szenario(listOf(Dart.single(1), Dart.single(2), Dart.single(3)), emptyList(), 3),
        )

        szenarien.forEach { szenario ->
            val result = slotContents(
                thrownDarts = szenario.thrown,
                checkout = szenario.checkout,
                slotCount = szenario.slotCount,
            )

            // Groesse entspricht immer slotCount.
            assertEquals(szenario.slotCount, result.size)

            // Die Thrown-Eintraege entsprechen exakt thrownDarts in Reihenfolge
            // und Anzahl (soweit sie in slotCount passen).
            val thrownFromResult = result.filterIsInstance<SlotContent.Thrown>().map { it.dart }
            assertEquals(
                szenario.thrown.take(szenario.slotCount),
                thrownFromResult,
            )

            // Die Ghost-Eintraege entsprechen exakt dem checkout ab Index 0, in
            // Reihenfolge (nur relevant, wenn ueberhaupt Geister erscheinen).
            val ghostsFromResult = result.filterIsInstance<SlotContent.Ghost>().map { it.dart }
            if (ghostsFromResult.isNotEmpty()) {
                assertEquals(szenario.checkout, ghostsFromResult)
            }
        }
    }
}
