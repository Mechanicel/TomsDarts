package com.mechanicel.tomsdarts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tester-Haertung fuer [AroundTheClockMode]: Edge-Cases, Fehlerpfade,
 * Grenzwerte und Regressionen zusaetzlich zu den Happy-Path-Basistests in
 * [AroundTheClockModeTest]. Reines JUnit, kein Robolectric, deterministisch.
 *
 * Schwerpunkte (siehe Klassendokumentation von [AroundTheClockMode]):
 * - Multiplier-Irrelevanz an mehreren Zielzahlen (nicht nur einer).
 * - Sequenz-Strenge: eine Nicht-Ziel-Zahl (auch eine bereits "spaetere") rueckt
 *   NICHT vor, ueberspringen ist nicht moeglich.
 * - No-Op-Identitaet: bei No-Ops liefert [AroundTheClockMode.applyDart] exakt
 *   dasselbe State-Objekt zurueck (assertSame), kein bloss gleichwertiges Kopie.
 * - Sieg-Kante bei target == 20 sowie das IST-Verhalten NACH dem Sieg
 *   (target > 20): jeder weitere Dart bleibt ein permanentes No-Op, weil kein
 *   Dart-Segment > 20 existieren kann.
 * - Voller Durchlauf 1..20 mit wechselnden Multipliern je Stufe.
 * - Gegner-Ignoranz auch fuer den Sieg-Pfad und fuer No-Op-Pfade.
 * - scored-Semantik ueber alle Pfade hinweg.
 */
class AroundTheClockModeEdgeCasesTest {

    private val mode = AroundTheClockMode()
    private val config = GameConfig()

    // --- Multiplier-Irrelevanz an mehreren Zielzahlen -------------------------

    @Test
    fun multiplierEgal_anMehrerenZielzahlen_rueecktJeweilsGenauEinsVor() {
        listOf(1, 10, 19, 20).forEach { target ->
            val start = AroundTheClockState(target)
            val single = mode.applyDart(start, Dart.single(target), config)
            val double = mode.applyDart(start, Dart.double(target), config)
            val triple = mode.applyDart(start, Dart.triple(target), config)

            assertEquals("Single an Ziel $target", AroundTheClockState(target + 1), single.newState)
            assertEquals("Double an Ziel $target", AroundTheClockState(target + 1), double.newState)
            assertEquals("Triple an Ziel $target", AroundTheClockState(target + 1), triple.newState)
            assertEquals(1, single.scored)
            assertEquals(1, double.scored)
            assertEquals(1, triple.scored)
        }
    }

    // --- Sequenz-Strenge: kein Ueberspringen -----------------------------------

    @Test
    fun hoehereNichtZielZahl_rueecktNichtVor_keinUeberspringen() {
        // target=5, Triple 20 (weit hoehere Zahl) darf nicht auf 6 vorruecken.
        val start = AroundTheClockState(5)
        val o = mode.applyDart(start, Dart.triple(20), config)
        assertSame(start, o.newState)
        assertEquals(0, o.scored)
        assertFalse(o.bust)
        assertFalse(o.legWon)
    }

    @Test
    fun niedrigereNichtZielZahl_rueecktNichtVor() {
        // target=10, Single 3 (bereits "erledigte" Zahl) ist ebenfalls ein No-Op.
        val start = AroundTheClockState(10)
        val o = mode.applyDart(start, Dart.single(3), config)
        assertSame(start, o.newState)
        assertEquals(0, o.scored)
    }

    @Test
    fun benachbarteZahlEinsHoeherAlsZiel_rueecktNichtVor() {
        // target=7, Wurf auf 8 (direkt danach) ist trotzdem kein Treffer.
        val start = AroundTheClockState(7)
        val o = mode.applyDart(start, Dart.single(8), config)
        assertSame(start, o.newState)
        assertEquals(0, o.scored)
        assertFalse(o.legWon)
    }

    // --- No-Op-Identitaet: exakt dasselbe State-Objekt -------------------------

    @Test
    fun noOps_liefernExaktDasselbeStateObjekt() {
        val start = AroundTheClockState(11)

        val miss = mode.applyDart(start, Dart.miss(), config)
        val bullSingle = mode.applyDart(start, Dart.bull(), config)
        val bullDouble = mode.applyDart(start, Dart.doubleBull(), config)
        val falscheZahl = mode.applyDart(start, Dart.single(12), config)

        assertSame(start, miss.newState)
        assertSame(start, bullSingle.newState)
        assertSame(start, bullDouble.newState)
        assertSame(start, falscheZahl.newState)

        listOf(miss, bullSingle, bullDouble, falscheZahl).forEach {
            assertEquals(0, it.scored)
            assertFalse(it.bust)
            assertFalse(it.legWon)
        }
    }

    // --- Sieg-Kante: target == 20, nicht schon bei 19 --------------------------

    @Test
    fun trefferDerNeunzehn_gewinntNochNicht() {
        val o = mode.applyDart(AroundTheClockState(19), Dart.single(19), config)
        assertFalse(o.legWon)
        assertEquals(AroundTheClockState(20), o.newState)
        assertEquals(1, o.scored)
    }

    @Test
    fun trefferDerZwanzig_mitJedemMultiplier_gewinntGleichermassen() {
        listOf(Dart.single(20), Dart.double(20), Dart.triple(20)).forEach { dart ->
            val o = mode.applyDart(AroundTheClockState(20), dart, config)
            assertTrue("Dart $dart muss gewinnen", o.legWon)
            assertFalse(o.bust)
            assertEquals(1, o.scored)
            assertEquals(AroundTheClockState(21), o.newState)
        }
    }

    // --- IST-Verhalten NACH dem Sieg: state.target > TOTAL ----------------------

    @Test
    fun nachDemSieg_jederWeitereDart_istPermanentesNoOp() {
        // Dokumentiert das IST-Verhalten: target=21 ist von keinem gueltigen
        // Dart-Segment (0..20, 25) mehr erreichbar - dart.segment == 21 ist
        // physikalisch unmoeglich. applyDart bleibt daher ab hier fuer JEDEN
        // Dart (auch Dart.single(20), den frueher gueltigen "letzten" Treffer)
        // ein No-Op mit unveraendertem State, kein erneutes legWon.
        val won = AroundTheClockState(21)

        val afterTwenty = mode.applyDart(won, Dart.single(20), config)
        assertSame(won, afterTwenty.newState)
        assertEquals(0, afterTwenty.scored)
        assertFalse(afterTwenty.legWon)
        assertFalse(afterTwenty.bust)

        val afterMiss = mode.applyDart(won, Dart.miss(), config)
        assertSame(won, afterMiss.newState)
        assertEquals(0, afterMiss.scored)
        assertFalse(afterMiss.legWon)
    }

    // --- Voller Durchlauf 1..20 mit wechselnden Multipliern ----------------------

    @Test
    fun vollerDurchlauf_mitWechselndenMultipliern_erreichtSiegExaktBeiZwanzig() {
        var state = mode.initialState(config)
        val multiplierZyklus = listOf(1, 2, 3)

        for (n in 1..19) {
            val mult = multiplierZyklus[(n - 1) % multiplierZyklus.size]
            val o = mode.applyDart(state, Dart(n, mult), config)
            assertFalse("Zwischenschritt $n (mult=$mult) darf nicht gewinnen", o.legWon)
            assertFalse(o.bust)
            assertEquals(1, o.scored)
            assertEquals(AroundTheClockState(n + 1), o.newState)
            state = o.newState
        }
        assertEquals(AroundTheClockState(20), state)

        val finalOutcome = mode.applyDart(state, Dart.double(20), config)
        assertTrue(finalOutcome.legWon)
        assertEquals(AroundTheClockState(21), finalOutcome.newState)
    }

    // --- Gegner-Ignoranz auch bei Sieg- und No-Op-Pfaden -------------------------

    @Test
    fun gegnerWerdenIgnoriert_auchBeimSiegDart() {
        val opponent = AroundTheClockState(1)
        val ohneGegner = mode.applyDart(AroundTheClockState(20), Dart.single(20), config)
        val mitGegnern = mode.applyDart(
            AroundTheClockState(20),
            Dart.single(20),
            config,
            opponents = listOf(opponent),
        )
        assertEquals(ohneGegner, mitGegnern)
        assertTrue(mitGegnern.legWon)
    }

    @Test
    fun gegnerWerdenIgnoriert_auchBeiNoOpDarts() {
        val start = AroundTheClockState(5)
        val ohneGegner = mode.applyDart(start, Dart.miss(), config)
        val mitGegnern = mode.applyDart(
            start,
            Dart.miss(),
            config,
            opponents = listOf(AroundTheClockState(20), AroundTheClockState(1)),
        )
        assertEquals(ohneGegner, mitGegnern)
        assertSame(start, mitGegnern.newState)
        assertEquals(0, mitGegnern.scored)
    }

    // --- scored-Semantik ---------------------------------------------------------

    @Test
    fun scored_istImmerEinsBeiVorrueckenUndNullSonst() {
        val vorrueckend = mode.applyDart(AroundTheClockState(4), Dart.single(4), config)
        val noOp = mode.applyDart(AroundTheClockState(4), Dart.single(5), config)
        assertEquals(1, vorrueckend.scored)
        assertEquals(0, noOp.scored)
    }
}
