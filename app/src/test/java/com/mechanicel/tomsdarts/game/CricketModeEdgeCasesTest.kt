package com.mechanicel.tomsdarts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Abhaertungs-/Regressionstests fuer [CricketMode] (ergaenzt die Happy-Path-Tests
 * in [CricketModeTest]). Reines JUnit, kein Robolectric, deterministisch.
 *
 * Schwerpunkte:
 * - Bull-Sonderfaelle (Single-/Doppel-Bull-Marks, Bull-Overflow, Bull als
 *   Pflichtfeld fuer den Sieg),
 * - Overflow-Grenzfaelle bei unterschiedlichen Ausgangs-Marks (0/1/2/3),
 * - Scorable-Logik mit mehreren Gegnern (gemischter Schliessstand),
 * - Sieg-Bedingung: Gleichstand-Kante, "alle geschlagen" bei mehreren Gegnern,
 *   Sieg im selben Dart, der zugleich schliesst und in Fuehrung bringt,
 * - No-Op-Identitaet (Segmente 1-14 / Miss aendern den State-Verweis nicht),
 * - Kapp-/Konsistenz-Invarianten (Marks nie > 3, Map-Keys stabil),
 * - Eingabe-Robustheit als IST-Verhalten dokumentiert (keine erzwungene Soll-Semantik).
 */
class CricketModeEdgeCasesTest {

    private val mode = CricketMode()
    private val config = GameConfig()

    /** Baut einen Cricket-Zustand aus abweichenden Marks (fehlende Felder == 0). */
    private fun state(points: Int = 0, marks: Map<Int, Int> = emptyMap()): CricketState =
        CricketState(marks = CricketState.FIELDS.associateWith { marks[it] ?: 0 }, points = points)

    // --- Bull-Sonderfaelle -----------------------------------------------------

    @Test
    fun bull_single_setztEinMark_ausgehendVonNull() {
        val o = mode.applyDart(state(), Dart.bull(), config)
        assertEquals(1, o.newState.marksOf(25))
        assertEquals(0, o.scored)
        assertFalse(o.newState.isClosed(25))
        assertFalse(o.bust)
        assertFalse(o.legWon)
    }

    @Test
    fun bull_double_setztZweiMarks_ausgehendVonNull() {
        val o = mode.applyDart(state(), Dart.doubleBull(), config)
        assertEquals(2, o.newState.marksOf(25))
        assertEquals(0, o.scored)
        assertFalse(o.newState.isClosed(25))
    }

    @Test
    fun bull_overflow_punktetFeldwert25ProUeberschussmark_solo() {
        // Bull bei 2 Marks, Doppel-Bull: toClose = min(2,1) = 1 -> geschlossen,
        // overflow = 1 -> 1 * 25 = 25 (solo, Gegnerliste leer).
        val o = mode.applyDart(state(marks = mapOf(25 to 2)), Dart.doubleBull(), config)
        assertEquals(3, o.newState.marksOf(25))
        assertTrue(o.newState.isClosed(25))
        assertEquals(25, o.scored)
        assertEquals(25, o.newState.points)
    }

    @Test
    fun bull_alsPflichtfeldFuerSieg_schliessenOhneOverflowGenuegtBeiGleichstand() {
        // Alle sechs Zahlenfelder bereits zu, Bull bei 2 Marks, 50 Punkte.
        // Single Bull schliesst das letzte Pflichtfeld (kein Overflow, scored 0);
        // Gegner steht ebenfalls bei 50 -> Gleichstand genuegt fuer den Sieg.
        val almost = state(
            points = 50,
            marks = mapOf(20 to 3, 19 to 3, 18 to 3, 17 to 3, 16 to 3, 15 to 3, 25 to 2),
        )
        val opponent = state(points = 50, marks = mapOf(25 to 3))
        val o = mode.applyDart(almost, Dart.bull(), config, opponents = listOf(opponent))
        assertEquals(3, o.newState.marksOf(25))
        assertEquals(0, o.scored)
        assertEquals(50, o.newState.points)
        assertTrue(o.newState.allClosed())
        assertTrue(o.legWon)
        assertFalse(o.bust)
    }

    // --- Overflow-Grenzfaelle bei unterschiedlichen Ausgangs-Marks --------------

    @Test
    fun overflow_ausgehendVonEinemMark_triple_toCloseZweiOverflowEins() {
        // m=1, Triple(16): toClose = min(3,2) = 2 -> geschlossen, overflow = 1
        // -> 1 * 16 = 16 (solo).
        val o = mode.applyDart(state(marks = mapOf(16 to 1)), Dart.triple(16), config)
        assertEquals(3, o.newState.marksOf(16))
        assertEquals(16, o.scored)
        assertEquals(16, o.newState.points)
    }

    @Test
    fun overflow_ausgehendVonEinemMark_double_schliesstOhneOverflow() {
        // m=1, Double(17): toClose = min(2,2) = 2 -> geschlossen exakt, overflow = 0.
        val o = mode.applyDart(state(marks = mapOf(17 to 1)), Dart.double(17), config)
        assertEquals(3, o.newState.marksOf(17))
        assertEquals(0, o.scored)
        assertEquals(0, o.newState.points)
    }

    @Test
    fun overflow_bereitsGeschlossenesFeld_soloOhneGegnerliste_punktetVollenOverflow() {
        // m=3 (bereits zu), Triple(20): toClose = 0, overflow = 3 -> 3*20 = 60,
        // auch OHNE explizite Gegnerliste (leere Liste -> Solo-Wertung).
        val o = mode.applyDart(state(marks = mapOf(20 to 3)), Dart.triple(20), config, opponents = emptyList())
        assertEquals(3, o.newState.marksOf(20))
        assertEquals(60, o.scored)
        assertEquals(60, o.newState.points)
    }

    @Test
    fun overflow_vonNullMitDouble_schliesstNichtVollstaendig_keinOverflow() {
        // m=0, Double(19): toClose = min(2,3) = 2 -> newMarks = 2 (NICHT geschlossen),
        // overflow = 0 -> kein Punkt, Feld bleibt offen.
        val o = mode.applyDart(state(), Dart.double(19), config)
        assertEquals(2, o.newState.marksOf(19))
        assertFalse(o.newState.isClosed(19))
        assertEquals(0, o.scored)
    }

    // --- Scorable-Logik mit mehreren Gegnern ------------------------------------

    @Test
    fun mehrereGegner_punktetSolangeMindestensEinerDasFeldOffenHat() {
        val player = state(marks = mapOf(20 to 3))
        val opponentClosed = state(marks = mapOf(20 to 3))
        val opponentOpen = state(marks = mapOf(20 to 1))
        val o = mode.applyDart(player, Dart.triple(20), config, opponents = listOf(opponentClosed, opponentOpen))
        assertEquals(60, o.scored)
        assertEquals(60, o.newState.points)
    }

    @Test
    fun mehrereGegner_keinPunkt_wennAusnahmslosAlleDasFeldGeschlossenHaben() {
        val player = state(marks = mapOf(20 to 3))
        val opponentA = state(marks = mapOf(20 to 3))
        val opponentB = state(marks = mapOf(20 to 3))
        val opponentC = state(marks = mapOf(20 to 3))
        val o = mode.applyDart(player, Dart.triple(20), config, opponents = listOf(opponentA, opponentB, opponentC))
        assertEquals(0, o.scored)
        assertEquals(0, o.newState.points)
    }

    @Test
    fun dreiGegner_gemischterSchliessstand_reihenfolgeEgal() {
        // Zwei Gegner haben das Feld zu, einer noch offen -> punktet trotzdem
        // unabhaengig davon, an welcher Position der offene Gegner in der Liste steht.
        val player = state(marks = mapOf(18 to 3))
        val closed = state(marks = mapOf(18 to 3))
        val open = state(marks = mapOf(18 to 2))
        val vorne = mode.applyDart(player, Dart.single(18), config, opponents = listOf(open, closed, closed))
        val hinten = mode.applyDart(player, Dart.single(18), config, opponents = listOf(closed, closed, open))
        assertEquals(18, vorne.scored)
        assertEquals(18, hinten.scored)
    }

    // --- Sieg-Bedingung: Gleichstand, mehrere Gegner, Sieg im selben Dart -------

    @Test
    fun sieg_beiExaktemGleichstandMitDemGegner() {
        // Alle Felder zu bis auf 15 (2 Marks), 40 Punkte. Single 15 schliesst das
        // letzte Feld ohne Overflow; Gegner steht exakt bei 40 -> Gleichstand
        // genuegt (>=), Sieg.
        val almost = state(
            points = 40,
            marks = mapOf(20 to 3, 19 to 3, 18 to 3, 17 to 3, 16 to 3, 15 to 2, 25 to 3),
        )
        val opponent = state(points = 40, marks = mapOf(15 to 3))
        val o = mode.applyDart(almost, Dart.single(15), config, opponents = listOf(opponent))
        assertEquals(40, o.newState.points)
        assertTrue(o.newState.allClosed())
        assertTrue(o.legWon)
        assertFalse(o.bust)
    }

    @Test
    fun keinSieg_wennAlleFelderZuAberNichtGegenJedenGegnerFuehrend() {
        // Alle Felder zu, 50 Punkte. Ein Gegner steht bei 40 (geschlagen), der
        // andere bei 60 (nicht geschlagen) -> Sieg erfordert ALLE Gegner, also
        // noch kein Sieg.
        val almost = state(
            points = 50,
            marks = mapOf(20 to 3, 19 to 3, 18 to 3, 17 to 3, 16 to 3, 15 to 2, 25 to 3),
        )
        val opponentBehind = state(points = 40, marks = mapOf(15 to 3))
        val opponentAhead = state(points = 60, marks = mapOf(15 to 3))
        val o = mode.applyDart(almost, Dart.single(15), config, opponents = listOf(opponentBehind, opponentAhead))
        assertTrue(o.newState.allClosed())
        assertFalse(o.legWon)
        assertFalse(o.bust)
    }

    @Test
    fun sieg_imSelbenDart_derSchliesstUndDurchOverflowInFuehrungBringt() {
        // Vor dem Dart liegt der Spieler (40) HINTER dem Gegner (55). Das letzte
        // offene Feld (15, 2 Marks) ist beim Gegner noch offen -> der schliessende
        // Triple-Dart punktet zugleich Overflow (2*15=30) und bringt den Spieler
        // im selben Dart auf 70 >= 55 -> Sieg.
        val almost = state(
            points = 40,
            marks = mapOf(20 to 3, 19 to 3, 18 to 3, 17 to 3, 16 to 3, 15 to 2, 25 to 3),
        )
        val opponent = state(points = 55, marks = mapOf(15 to 0))
        val o = mode.applyDart(almost, Dart.triple(15), config, opponents = listOf(opponent))
        assertEquals(30, o.scored)
        assertEquals(70, o.newState.points)
        assertTrue(o.newState.allClosed())
        assertTrue(o.legWon)
        assertFalse(o.bust)
    }

    @Test
    fun keinSieg_nurEinFeldFehltNochTrotzGenuegendPunkten() {
        // Alle Felder bis auf Bull (0 Marks) sind zu, Punkte weit ueber dem
        // Gegner -> ohne "alle geschlossen" trotzdem kein Sieg.
        val almost = state(
            points = 500,
            marks = mapOf(20 to 3, 19 to 3, 18 to 3, 17 to 3, 16 to 3, 15 to 3, 25 to 0),
        )
        val opponent = state(points = 1, marks = mapOf(20 to 0))
        // Regulaerer, nicht-schliessender Dart auf ein bereits geschlossenes Feld.
        val o = mode.applyDart(almost, Dart.single(20), config, opponents = listOf(opponent))
        assertFalse(o.newState.allClosed())
        assertFalse(o.legWon)
        assertFalse(o.bust)
    }

    // --- No-Op-Identitaet --------------------------------------------------------

    @Test
    fun noOp_segmenteAusserhalbDerFelder_liefernDenselbenStateVerweis() {
        val start = state(points = 42, marks = mapOf(20 to 2, 25 to 1))
        val darts = listOf(
            Dart.single(1), Dart.double(7), Dart.triple(14), Dart.miss(),
        )
        darts.forEach { dart ->
            val o = mode.applyDart(start, dart, config)
            // Identitaet, nicht nur Gleichheit: dieselbe Instanz wird durchgereicht.
            assertSame("No-Op darf keine neue State-Instanz erzeugen", start, o.newState)
            assertEquals(0, o.scored)
            assertFalse(o.bust)
            assertFalse(o.legWon)
        }
    }

    @Test
    fun noOp_segment21AusserhalbDesBoards_istEbenfallsNoOp() {
        // IST-Verhalten: applyDart validiert dart.isValid nicht extra, aber die
        // FIELDS-Pruefung greift fuer jedes nicht gelistete Segment - auch fuer
        // physikalisch unmoegliche wie 21.
        val start = state(points = 10, marks = mapOf(20 to 3))
        val ungueltig = Dart(21, 3)
        val o = mode.applyDart(start, ungueltig, config)
        assertSame(start, o.newState)
        assertEquals(0, o.scored)
    }

    // --- Kapp-/Konsistenz-Invarianten --------------------------------------------

    @Test
    fun marks_werdenNieUeberDreiHinausErhoeht_auchNachMehrerenTriples() {
        var current = state()
        repeat(3) {
            current = mode.applyDart(current, Dart.triple(20), config).newState
        }
        assertEquals(3, current.marksOf(20))
        // Ein vierter Triple darf die Marks nicht weiter erhoehen.
        val o = mode.applyDart(current, Dart.triple(20), config)
        assertEquals(3, o.newState.marksOf(20))
    }

    @Test
    fun marks_keysBleibenExaktDieSiebenFelder_nachBeliebigenWuerfen() {
        var current = state()
        val darts = listOf(Dart.single(20), Dart.triple(18), Dart.doubleBull(), Dart.double(15))
        darts.forEach { current = mode.applyDart(current, it, config).newState }
        assertEquals(setOf(15, 16, 17, 18, 19, 20, 25), current.marks.keys)
    }

    @Test
    fun invariante_bustIstNiemalsTrue_ueberVerschiedeneSzenarien() {
        val faelle = listOf(
            mode.applyDart(state(), Dart.triple(20), config),
            mode.applyDart(state(marks = mapOf(20 to 3)), Dart.triple(20), config),
            mode.applyDart(state(), Dart.miss(), config),
            mode.applyDart(state(), Dart.single(1), config),
            mode.applyDart(
                state(marks = CricketState.FIELDS.associateWith { 3 }, points = 100),
                Dart.triple(20),
                config,
                opponents = listOf(state(points = 200)),
            ),
        )
        faelle.forEach { o ->
            assertFalse("Cricket bustet laut Spec nie", o.bust)
            assertFalse("bust und legWon nie gleichzeitig", o.bust && o.legWon)
        }
    }

    // --- Eingabe-Robustheit: IST-Verhalten dokumentiert -------------------------
    // Hinweis: Analog zu X01ModeEdgeCasesTest haelt dieser Abschnitt das
    // TATSAECHLICHE Verhalten fest, ohne eine fragwuerdige Soll-Semantik
    // festzuschreiben. Guard-Clauses (Dart-Validierung) sind bewusst Sache der
    // aufrufenden Engine bzw. Backlog.

    @Test
    fun robustheit_applyDart_ignoriertDartIsValid_verarbeitetTripleBullUeberMultiplier() {
        // Physikalisch unmoeglicher Triple-Bull (25*3, isValid == false):
        // CricketMode behandelt 25 dennoch als FIELDS-Mitglied und rechnet mit
        // dart.multiplier == 3 weiter.
        val tripleBull = Dart(25, 3)
        assertFalse(tripleBull.isValid)
        val o = mode.applyDart(state(), tripleBull, config)
        assertEquals(3, o.newState.marksOf(25))
        assertEquals(0, o.scored)
    }

    @Test
    fun robustheit_multiplierNull_erhoehtMarksNicht_bleibtRegulaer() {
        // IST-Verhalten: Ein Dart mit multiplier 0 (kein Treffer registriert,
        // physikalisch ungueltig) fuegt 0 Marks hinzu -> No-Change, kein Bust.
        val ungueltig = Dart(20, 0)
        val o = mode.applyDart(state(marks = mapOf(20 to 1)), ungueltig, config)
        assertEquals(1, o.newState.marksOf(20))
        assertEquals(0, o.scored)
        assertFalse(o.bust)
        assertFalse(o.legWon)
    }
}
