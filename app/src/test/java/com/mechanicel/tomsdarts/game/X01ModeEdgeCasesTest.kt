package com.mechanicel.tomsdarts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Abhaertungs-/Regressionstests fuer [X01Mode] (ergaenzt die Happy-Path-Tests in
 * [X01ModeTest]). Reines JUnit, kein Robolectric, deterministisch.
 *
 * Schwerpunkte:
 * - Aufrechnen ueber ganze Aufnahmen (mehrere [X01Mode.applyDart] in Folge),
 * - Checkout-Varianten auf verschiedenen Doubles inkl. High-Checkout,
 * - Bust-Varianten (ueberworfen, Rest 1, Rest 0 ohne Double, Triple != Double),
 * - Verhalten ohne Double-Out (Rest 1 ist regulaer, Finish mit Single/Triple),
 * - verschiedene Startscores,
 * - Invariante bust XOR legWon,
 * - Eingabe-Robustheit als IST-Verhalten dokumentiert (keine erzwungene Soll-Semantik).
 */
class X01ModeEdgeCasesTest {

    private val mode = X01Mode()
    private val config = GameConfig(startScore = 501, doubleOut = true)

    // --- Hilfen ---------------------------------------------------------------

    /**
     * Faltet eine Aufnahme (Liste von Darts) ueber [applyDart], beginnend bei
     * [start]. Bricht bei Bust/LegWon NICHT ab (die Engine entscheidet das),
     * sondern wendet jeden Dart auf den jeweils zurueckgemeldeten newState an,
     * damit reine Reduktions-Sequenzen sauber durchgerechnet werden koennen.
     * Gibt die Liste aller Einzelergebnisse zurueck.
     */
    private fun applyAufnahme(
        start: X01State,
        darts: List<Dart>,
        cfg: GameConfig = config,
    ): List<DartOutcome<X01State>> {
        var state = start
        val outcomes = mutableListOf<DartOutcome<X01State>>()
        for (dart in darts) {
            val o = mode.applyDart(state, dart, cfg)
            outcomes += o
            state = o.newState
        }
        return outcomes
    }

    // --- Aufrechnen ueber Sequenzen ------------------------------------------

    @Test
    fun aufnahme_dreiTriple20_reduziert501Auf321() {
        val outcomes = applyAufnahme(X01State(501), listOf(Dart.triple(20), Dart.triple(20), Dart.triple(20)))
        assertEquals(listOf(X01State(441), X01State(381), X01State(321)), outcomes.map { it.newState })
        outcomes.forEach {
            assertEquals(60, it.scored)
            assertFalse(it.bust)
            assertFalse(it.legWon)
        }
    }

    @Test
    fun aufnahme_gemischt_rechnetKorrektHerunterUndWertetJeDart() {
        // 100 -> T20(60)=40 -> S1(1)=39 -> D... separat. Hier reine Reduktion + scored je Dart.
        val outcomes = applyAufnahme(X01State(180), listOf(Dart.triple(20), Dart.single(5), Dart.bull()))
        assertEquals(listOf(X01State(120), X01State(115), X01State(90)), outcomes.map { it.newState })
        assertEquals(listOf(60, 5, 25), outcomes.map { it.scored })
        outcomes.forEach { assertFalse(it.bust); assertFalse(it.legWon) }
    }

    // --- Checkout-Varianten ---------------------------------------------------

    @Test
    fun checkout_aufDouble1_gewinntLeg() {
        // Rest 2 -> Double 1 (2) -> 0 mit Double -> legWon.
        val o = mode.applyDart(X01State(2), Dart.double(1), config)
        assertEquals(X01State(0), o.newState)
        assertEquals(2, o.scored)
        assertTrue(o.legWon)
        assertFalse(o.bust)
    }

    @Test
    fun highCheckout_170_letzterDartDoppelBull() {
        // 170: T20(60) -> 110 regulaer, T20(60) -> 50 regulaer, DBull(50) -> 0 -> legWon.
        val outcomes = applyAufnahme(X01State(170), listOf(Dart.triple(20), Dart.triple(20), Dart.doubleBull()))
        // Erste zwei Darts regulaer.
        assertEquals(X01State(110), outcomes[0].newState)
        assertFalse(outcomes[0].legWon); assertFalse(outcomes[0].bust)
        assertEquals(X01State(50), outcomes[1].newState)
        assertFalse(outcomes[1].legWon); assertFalse(outcomes[1].bust)
        // Letzter Dart gewinnt das Leg.
        assertEquals(X01State(0), outcomes[2].newState)
        assertEquals(50, outcomes[2].scored)
        assertTrue(outcomes[2].legWon)
        assertFalse(outcomes[2].bust)
    }

    @Test
    fun checkout_aufDouble20_gewinntLeg() {
        // Rest 40 -> Double 20 (40) -> 0 mit Double -> legWon.
        val o = mode.applyDart(X01State(40), Dart.double(20), config)
        assertTrue(o.legWon)
        assertEquals(X01State(0), o.newState)
        assertEquals(40, o.scored)
    }

    // --- Bust-Varianten -------------------------------------------------------

    @Test
    fun bust_ueberworfen_grossesUeberwerfen() {
        // Rest 10 -> Triple 20 (60) -> -50 -> bust, Eingangszustand unveraendert.
        val o = mode.applyDart(X01State(10), Dart.triple(20), config)
        assertTrue(o.bust)
        assertFalse(o.legWon)
        assertEquals(0, o.scored)
        assertEquals(X01State(10), o.newState)
    }

    @Test
    fun bust_restEins_durchSingle1AufRest2() {
        // Rest 2 -> Single 1 (1) -> Rest 1 -> bust (Rest 1 nicht per Double ausspielbar).
        val o = mode.applyDart(X01State(2), Dart.single(1), config)
        assertTrue(o.bust)
        assertFalse(o.legWon)
        assertEquals(0, o.scored)
        assertEquals(X01State(2), o.newState)
    }

    @Test
    fun bust_restNull_mitTriple_istKeinDouble() {
        // Rest 60 -> Triple 20 (60) -> 0, aber Triple ist kein Double -> bust.
        val o = mode.applyDart(X01State(60), Dart.triple(20), config)
        assertTrue(o.bust)
        assertFalse(o.legWon)
        assertEquals(0, o.scored)
        assertEquals(X01State(60), o.newState)
    }

    @Test
    fun bust_restNull_mitSingleBull_istKeinDouble() {
        // Rest 25 -> Bull (25, Single) -> 0 ohne Double -> bust.
        val o = mode.applyDart(X01State(25), Dart.bull(), config)
        assertTrue(o.bust)
        assertFalse(o.legWon)
        assertEquals(0, o.scored)
        assertEquals(X01State(25), o.newState)
    }

    // --- Ohne Double-Out ------------------------------------------------------

    @Test
    fun ohneDoubleOut_restEins_istKeinBust_sondernRegulaer() {
        val noDouble = config.copy(doubleOut = false)
        // Rest 3 -> Single 2 (2) -> Rest 1: ohne Double-Out KEIN Bust, regulaerer Zustand.
        val o = mode.applyDart(X01State(3), Dart.single(2), noDouble)
        assertEquals(X01State(1), o.newState)
        assertEquals(2, o.scored)
        assertFalse(o.bust)
        assertFalse(o.legWon)
        // ...und von Rest 1 aus mit Single 1 finishbar.
        val o2 = mode.applyDart(X01State(1), Dart.single(1), noDouble)
        assertEquals(X01State(0), o2.newState)
        assertTrue(o2.legWon)
        assertFalse(o2.bust)
    }

    @Test
    fun ohneDoubleOut_finishMitTriple() {
        val noDouble = config.copy(doubleOut = false)
        // Rest 60 -> Triple 20 -> 0 ohne Double erlaubt -> legWon.
        val o = mode.applyDart(X01State(60), Dart.triple(20), noDouble)
        assertEquals(X01State(0), o.newState)
        assertEquals(60, o.scored)
        assertTrue(o.legWon)
        assertFalse(o.bust)
    }

    @Test
    fun ohneDoubleOut_ueberwerfen_bleibtBust() {
        val noDouble = config.copy(doubleOut = false)
        // Auch ohne Double-Out ist Ueberwerfen ein Bust.
        val o = mode.applyDart(X01State(5), Dart.triple(20), noDouble)
        assertTrue(o.bust)
        assertFalse(o.legWon)
        assertEquals(0, o.scored)
        assertEquals(X01State(5), o.newState)
    }

    // --- Verschiedene Startscores --------------------------------------------

    @Test
    fun initialState_301und701() {
        assertEquals(X01State(301), mode.initialState(config.copy(startScore = 301)))
        assertEquals(X01State(701), mode.initialState(config.copy(startScore = 701)))
    }

    @Test
    fun checkout_in301_durchgespielt() {
        val cfg301 = config.copy(startScore = 301)
        var state = mode.initialState(cfg301)
        assertEquals(X01State(301), state)

        // Reduktions-Darts bis zum letzten Double: jeweils regulaer erwartet.
        val reduktion = listOf(
            Dart.triple(20) to X01State(241),
            Dart.triple(20) to X01State(181),
            Dart.triple(20) to X01State(121),
            Dart.triple(17) to X01State(70),  // 121 - 51 = 70
            Dart.single(10) to X01State(60),  // 70 - 10 = 60
            Dart.single(20) to X01State(40),  // 60 - 20 = 40
        )
        for ((dart, expected) in reduktion) {
            val o = mode.applyDart(state, dart, cfg301)
            assertEquals(expected, o.newState)
            assertFalse(o.bust)
            assertFalse(o.legWon)
            state = o.newState
        }
        assertEquals(X01State(40), state)

        // Finish: 40 -> Double 20 -> 0 -> legWon.
        val finish = mode.applyDart(state, Dart.double(20), cfg301)
        assertEquals(X01State(0), finish.newState)
        assertTrue(finish.legWon)
        assertFalse(finish.bust)
        assertEquals(40, finish.scored)
    }

    // --- Invariante bust XOR legWon ------------------------------------------

    @Test
    fun invariante_bustUndLegWon_niemalsGleichzeitig() {
        val cfgDO = config
        val cfgNoDO = config.copy(doubleOut = false)
        val faelle = listOf(
            mode.applyDart(X01State(501), Dart.triple(20), cfgDO),   // regulaer
            mode.applyDart(X01State(40), Dart.double(20), cfgDO),    // legWon
            mode.applyDart(X01State(50), Dart.doubleBull(), cfgDO),  // legWon DBull
            mode.applyDart(X01State(10), Dart.triple(20), cfgDO),    // bust ueberworfen
            mode.applyDart(X01State(2), Dart.single(1), cfgDO),      // bust Rest 1
            mode.applyDart(X01State(20), Dart.single(20), cfgDO),    // bust Rest 0 ohne Double
            mode.applyDart(X01State(60), Dart.triple(20), cfgDO),    // bust Rest 0 mit Triple
            mode.applyDart(X01State(20), Dart.single(20), cfgNoDO),  // legWon ohne DO
            mode.applyDart(X01State(5), Dart.triple(20), cfgNoDO),   // bust ohne DO
            mode.applyDart(X01State(100), Dart.miss(), cfgDO),       // regulaer Miss
        )
        faelle.forEach { o ->
            assertFalse("bust und legWon duerfen nie gleichzeitig true sein: $o", o.bust && o.legWon)
        }
    }

    // --- Eingabe-Robustheit: IST-Verhalten dokumentiert ----------------------
    // Hinweis: Die folgenden Tests halten das TATSAECHLICHE Verhalten fest, ohne
    // eine fragwuerdige Soll-Semantik festzuschreiben. Guard-Clauses (Validierung
    // von startScore/Dart) sind bewusst Sache der aufrufenden Engine bzw. Backlog.

    @Test
    fun robustheit_missLaesstRestUnveraendert_scored0_keinBust() {
        // Miss (value 0): newRemaining == remaining, weder 0/1 noch <0 -> regulaer.
        // Wichtig: scored == 0 OHNE Bust (unterscheidet sich vom Bust-Fall mit scored 0).
        val o = mode.applyDart(X01State(100), Dart.miss(), config)
        assertEquals(X01State(100), o.newState)
        assertEquals(0, o.scored)
        assertFalse(o.bust)
        assertFalse(o.legWon)
    }

    @Test
    fun robustheit_initialState_startScore0_ergibtRest0() {
        // IST-Verhalten: kein Guard, startScore wird unveraendert uebernommen.
        assertEquals(X01State(0), mode.initialState(config.copy(startScore = 0)))
    }

    @Test
    fun robustheit_initialState_negativerStartScore_wirdUebernommen() {
        // IST-Verhalten: negativer Startscore wird ohne Validierung uebernommen.
        assertEquals(X01State(-7), mode.initialState(config.copy(startScore = -7)))
    }

    @Test
    fun robustheit_ausRest0_jederWurfFuehrtZuBust_mitDoubleOut() {
        // IST-Verhalten: Aus Rest 0 ist mit Double-Out kein Gewinn moeglich.
        // Miss: newRemaining 0, aber kein Double -> Bust. DBull: 0-50 < 0 -> Bust.
        val miss = mode.applyDart(X01State(0), Dart.miss(), config)
        assertTrue(miss.bust)
        assertFalse(miss.legWon)
        val dbull = mode.applyDart(X01State(0), Dart.doubleBull(), config)
        assertTrue(dbull.bust)
        assertFalse(dbull.legWon)
    }

    @Test
    fun robustheit_applyDart_ignoriertDartIsValid_rechnetNurMitValue() {
        // IST-Verhalten: applyDart konsultiert dart.isValid NICHT, sondern rechnet
        // ausschliesslich mit dart.value. Hier ein physikalisch unmoeglicher
        // Triple-Bull (25*3 = 75, isValid == false) auf Rest 100.
        val tripleBull = Dart(25, 3)
        assertFalse(tripleBull.isValid)
        assertEquals(75, tripleBull.value)
        val o = mode.applyDart(X01State(100), tripleBull, config)
        assertEquals(X01State(25), o.newState)
        assertEquals(75, o.scored)
        assertFalse(o.bust)
        assertFalse(o.legWon)
    }

    @Test
    fun robustheit_applyDart_ungueltigesSegment_wirdNurUeberValueVerarbeitet() {
        // IST-Verhalten: Segment 21 (ausserhalb 1..20/25) ist ungueltig, applyDart
        // verarbeitet dennoch value (21*1 = 21) als regulaeren Wurf.
        val ungueltig = Dart(21, 1)
        assertFalse(ungueltig.isValid)
        val o = mode.applyDart(X01State(100), ungueltig, config)
        assertEquals(X01State(79), o.newState)
        assertEquals(21, o.scored)
        assertFalse(o.bust)
        assertFalse(o.legWon)
    }

    @Test
    fun robustheit_negativerDartValue_erhoehtRest_keinBust() {
        // IST-Verhalten: Ein Dart mit negativem multiplier (value < 0) erhoeht den
        // Rest (remaining - negativ). newRemaining ist nie < 0/0/1 -> regulaer.
        // Dokumentiert die fehlende Eingabevalidierung; siehe Finding/offene Frage.
        val negativ = Dart(5, -1) // value -5
        assertEquals(-5, negativ.value)
        val o = mode.applyDart(X01State(100), negativ, config)
        assertEquals(X01State(105), o.newState)
        assertEquals(-5, o.scored)
        assertFalse(o.bust)
        assertFalse(o.legWon)
    }
}
