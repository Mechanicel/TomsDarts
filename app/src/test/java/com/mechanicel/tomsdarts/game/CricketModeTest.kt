package com.mechanicel.tomsdarts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Happy-Path-Basistests fuer [CricketMode]. Reines JUnit, kein Robolectric.
 *
 * Deckt initialState, Marks setzen (Single/Double/Triple), Schliessen bei 3,
 * Overflow (schliessen + punkten in einem Dart), die gegnerabhaengige Wertung
 * (offen vs. alle Gegner geschlossen), die No-Ops (Segmente 1-14 / Miss) und den
 * Leg-Gewinn ab. Das systematische Abhaerten (weitere Randfaelle) uebernimmt der
 * tester-Workflow.
 */
class CricketModeTest {

    private val mode = CricketMode()
    private val config = GameConfig()

    /** Baut einen Cricket-Zustand aus abweichenden Marks (fehlende Felder == 0). */
    private fun state(points: Int = 0, marks: Map<Int, Int> = emptyMap()): CricketState =
        CricketState(marks = CricketState.FIELDS.associateWith { marks[it] ?: 0 }, points = points)

    @Test
    fun keyUndDisplayName_sindGesetzt() {
        assertEquals("CRICKET", mode.key)
        assertEquals("Cricket", mode.displayName)
    }

    @Test
    fun initialState_alleFelderNullUndKeinePunkte() {
        val initial = mode.initialState(config)
        assertEquals(0, initial.points)
        assertEquals(setOf(15, 16, 17, 18, 19, 20, 25), initial.marks.keys)
        assertTrue(initial.marks.values.all { it == 0 })
    }

    @Test
    fun single_setztEinMark() {
        val o = mode.applyDart(state(), Dart.single(20), config)
        assertEquals(1, o.newState.marksOf(20))
        assertEquals(0, o.scored)
        assertFalse(o.bust)
        assertFalse(o.legWon)
    }

    @Test
    fun double_setztZweiMarks() {
        val o = mode.applyDart(state(), Dart.double(19), config)
        assertEquals(2, o.newState.marksOf(19))
        assertEquals(0, o.scored)
    }

    @Test
    fun triple_schliesstFeldOhnePunkteWennKeinOverflow() {
        // Triple 18 von 0: 3 Marks -> geschlossen, kein Overflow -> scored 0.
        val o = mode.applyDart(state(), Dart.triple(18), config)
        assertEquals(3, o.newState.marksOf(18))
        assertTrue(o.newState.isClosed(18))
        assertEquals(0, o.scored)
        assertFalse(o.legWon)
    }

    @Test
    fun overflow_schliesstUndPunktetInEinemDart_solo() {
        // 2 Marks auf 20, dann Triple 20: toClose 1 -> geschlossen, overflow 2 ->
        // 2 * 20 = 40 Punkte (Solo -> Gegnerliste leer, wird gewertet).
        val o = mode.applyDart(state(marks = mapOf(20 to 2)), Dart.triple(20), config)
        assertEquals(3, o.newState.marksOf(20))
        assertEquals(40, o.scored)
        assertEquals(40, o.newState.points)
    }

    @Test
    fun geschlossenesFeld_punktetWeiter_wennGegnerNochOffen() {
        // Eigenes 20 bereits geschlossen, Gegner hat 20 offen -> Triple 20 gibt
        // vollen Overflow (3 * 20 = 60).
        val player = state(marks = mapOf(20 to 3))
        val opponent = state(marks = mapOf(20 to 0))
        val o = mode.applyDart(player, Dart.triple(20), config, opponents = listOf(opponent))
        assertEquals(60, o.scored)
        assertEquals(60, o.newState.points)
    }

    @Test
    fun geschlossenesFeld_punktetNicht_wennAlleGegnerGeschlossen() {
        // Eigenes 20 zu, einziger Gegner hat 20 ebenfalls zu -> kein Punkt.
        val player = state(marks = mapOf(20 to 3))
        val opponent = state(marks = mapOf(20 to 3))
        val o = mode.applyDart(player, Dart.triple(20), config, opponents = listOf(opponent))
        assertEquals(0, o.scored)
        assertEquals(0, o.newState.points)
        assertEquals(3, o.newState.marksOf(20))
    }

    @Test
    fun bull_zaehltAlsFeld_mitFeldwert25() {
        // Bull bereits geschlossen, Gegner offen -> Doppel-Bull gibt 2 * 25 = 50.
        val player = state(marks = mapOf(25 to 3))
        val opponent = state(marks = mapOf(25 to 0))
        val o = mode.applyDart(player, Dart.doubleBull(), config, opponents = listOf(opponent))
        assertEquals(50, o.scored)
        assertEquals(50, o.newState.points)
    }

    @Test
    fun segmentAusserhalbDerFelder_istNoOp() {
        val start = state(points = 42, marks = mapOf(20 to 2))
        // Segmente 1-14 und Miss aendern nichts.
        assertEquals(start, mode.applyDart(start, Dart.single(14), config).newState)
        assertEquals(start, mode.applyDart(start, Dart.triple(1), config).newState)
        val miss = mode.applyDart(start, Dart.miss(), config)
        assertEquals(start, miss.newState)
        assertEquals(0, miss.scored)
        assertFalse(miss.bust)
        assertFalse(miss.legWon)
    }

    @Test
    fun sieg_wennAlleFelderZuUndPunkteMindestensGegner() {
        // Alle Felder zu bis auf 15 (2 Marks), 50 Punkte. Single 15 schliesst das
        // letzte Feld; Gegner hat 40 Punkte -> 50 >= 40 -> Leg gewonnen.
        val almost = state(
            points = 50,
            marks = mapOf(20 to 3, 19 to 3, 18 to 3, 17 to 3, 16 to 3, 15 to 2, 25 to 3),
        )
        val opponent = state(points = 40, marks = mapOf(15 to 3))
        val o = mode.applyDart(almost, Dart.single(15), config, opponents = listOf(opponent))
        assertTrue(o.newState.allClosed())
        assertTrue(o.legWon)
        assertFalse(o.bust)
    }

    @Test
    fun keinSieg_wennAlleFelderZuAberPunkteHinterGegner() {
        // Alle Felder zu, aber weniger Punkte als der Gegner -> noch kein Sieg.
        val almost = state(
            points = 30,
            marks = mapOf(20 to 3, 19 to 3, 18 to 3, 17 to 3, 16 to 3, 15 to 2, 25 to 3),
        )
        val opponent = state(points = 100, marks = mapOf(15 to 3))
        val o = mode.applyDart(almost, Dart.single(15), config, opponents = listOf(opponent))
        assertTrue(o.newState.allClosed())
        assertFalse(o.legWon)
        assertFalse(o.bust)
    }
}
