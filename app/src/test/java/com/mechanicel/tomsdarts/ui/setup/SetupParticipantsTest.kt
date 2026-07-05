package com.mechanicel.tomsdarts.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Reine JUnit-Tests fuer die Reorder-/Remove-Reducer der Teilnehmerverwaltung
 * ([movePlayerUp], [movePlayerDown], [removePlayerAt]) sowie die
 * [MIN_MATCH_PLAYERS]-Konstante. Keine Android-/Compose-Laufzeit noetig - die
 * Funktionen sind reine Listen-Transformationen. Edge-Cases haertet das
 * Test-Gate ab.
 */
class SetupParticipantsTest {

    private val a = SetupPlayer(id = 1, name = "Anna")
    private val b = SetupPlayer(id = 2, name = "Bjoern")
    private val c = SetupPlayer(id = 3, name = "Chris")
    private val d = SetupPlayer(id = 4, name = "Dora")

    @Test
    fun minMatchPlayers_istZwei() {
        assertEquals(2, MIN_MATCH_PLAYERS)
    }

    @Test
    fun movePlayerUp_tauschtMitVorgaenger() {
        val list = listOf(a, b, c)
        assertEquals(listOf(b, a, c), list.movePlayerUp(1))
    }

    @Test
    fun movePlayerUp_ersterElementBleibtUnveraendert() {
        val list = listOf(a, b, c)
        // Kein Wurf, dieselbe Liste (Identitaet) - das erste Element hat keinen
        // Vorgaenger.
        assertSame(list, list.movePlayerUp(0))
    }

    @Test
    fun movePlayerDown_tauschtMitNachfolger() {
        val list = listOf(a, b, c)
        assertEquals(listOf(a, c, b), list.movePlayerDown(1))
    }

    @Test
    fun movePlayerDown_letztesElementBleibtUnveraendert() {
        val list = listOf(a, b, c)
        assertSame(list, list.movePlayerDown(2))
    }

    @Test
    fun reorder_positionsReihenfolgeStimmtNachMehrfachSwap() {
        // Ausgang [a, b, c, d]; b nach oben, dann d nach oben -> [b, a, d, c].
        val result = listOf(a, b, c, d)
            .movePlayerUp(1)
            .movePlayerUp(3)
        assertEquals(listOf(b, a, d, c), result)
        // Reihenfolge (= 1-basierte Positionen) explizit als IDs geprueft.
        assertEquals(listOf(2L, 1L, 4L, 3L), result.map { it.id })
    }

    @Test
    fun removePlayerAt_entferntKorrektesElement() {
        val list = listOf(a, b, c)
        assertEquals(listOf(a, c), list.removePlayerAt(1))
    }

    @Test
    fun removePlayerAt_beiMinimumBleibtUnveraendert() {
        // Zwei Teilnehmer = MIN_MATCH_PLAYERS: Entfernen ist nicht erlaubt.
        val list = listOf(a, b)
        assertSame(list, list.removePlayerAt(0))
        assertSame(list, list.removePlayerAt(1))
    }

    // --- Grenzwerte / ungueltige Indizes ---
    // Die drei Reducer sind bewusst defensiv (kein Wurf bei einem
    // fachlich unmoeglichen Index) - die UI kann Buttons ohnehin deaktivieren,
    // aber die reine Logik-Ebene muss auch bei einem falschen Aufrufer robust
    // bleiben, statt eine IndexOutOfBoundsException zu werfen.

    @Test
    fun movePlayerUp_negativerIndexBleibtUnveraendert() {
        val list = listOf(a, b, c)
        assertSame(list, list.movePlayerUp(-1))
    }

    @Test
    fun movePlayerUp_indexGleichSizeBleibtUnveraendert() {
        val list = listOf(a, b, c)
        assertSame(list, list.movePlayerUp(list.size))
    }

    @Test
    fun movePlayerUp_indexWeitAusserhalbBleibtUnveraendert() {
        val list = listOf(a, b, c)
        assertSame(list, list.movePlayerUp(100))
    }

    @Test
    fun movePlayerDown_negativerIndexBleibtUnveraendert() {
        val list = listOf(a, b, c)
        assertSame(list, list.movePlayerDown(-1))
    }

    @Test
    fun movePlayerDown_indexGleichSizeBleibtUnveraendert() {
        val list = listOf(a, b, c)
        assertSame(list, list.movePlayerDown(list.size))
    }

    @Test
    fun movePlayerDown_indexWeitAusserhalbBleibtUnveraendert() {
        val list = listOf(a, b, c)
        assertSame(list, list.movePlayerDown(100))
    }

    @Test
    fun removePlayerAt_negativerIndexBleibtUnveraendert() {
        val list = listOf(a, b, c)
        assertSame(list, list.removePlayerAt(-1))
    }

    @Test
    fun removePlayerAt_indexGleichSizeBleibtUnveraendert() {
        val list = listOf(a, b, c)
        assertSame(list, list.removePlayerAt(list.size))
    }

    @Test
    fun removePlayerAt_indexWeitAusserhalbBleibtUnveraendert() {
        val list = listOf(a, b, c)
        assertSame(list, list.removePlayerAt(100))
    }

    // --- Robustheit bei sehr kurzen/leeren Listen ---
    // Die Reducer sind reine Listen-Transformationen ohne Kenntnis der
    // MIN_MATCH_PLAYERS-Regel des Aufrufers (die Regel setzt nur removePlayerAt
    // gezielt durch) - sie duerfen aber auch auf einer leeren oder
    // einelementigen Liste nicht werfen.

    @Test
    fun movePlayerUp_aufLeererListeBleibtUnveraendert() {
        val list = emptyList<SetupPlayer>()
        assertSame(list, list.movePlayerUp(0))
    }

    @Test
    fun movePlayerDown_aufLeererListeBleibtUnveraendert() {
        val list = emptyList<SetupPlayer>()
        assertSame(list, list.movePlayerDown(0))
    }

    @Test
    fun removePlayerAt_aufLeererListeBleibtUnveraendert() {
        val list = emptyList<SetupPlayer>()
        assertSame(list, list.removePlayerAt(0))
    }

    @Test
    fun movePlayerUp_aufEinelementigerListeBleibtUnveraendert() {
        val list = listOf(a)
        assertSame(list, list.movePlayerUp(0))
    }

    @Test
    fun movePlayerDown_aufEinelementigerListeBleibtUnveraendert() {
        val list = listOf(a)
        assertSame(list, list.movePlayerDown(0))
    }

    @Test
    fun removePlayerAt_aufEinelementigerListeBleibtUnveraendert() {
        // Groesse 1 liegt unterhalb MIN_MATCH_PLAYERS - die Invariante greift
        // hier zusaetzlich zur (nicht relevanten) Index-Pruefung.
        val list = listOf(a)
        assertSame(list, list.removePlayerAt(0))
    }

    // --- Mengen-/Reihenfolge-Erhalt ---

    @Test
    fun reorder_verliertOderDupliziertKeineIds() {
        val start = listOf(a, b, c, d)
        val result = start
            .movePlayerDown(0)
            .movePlayerUp(2)
            .movePlayerDown(1)
            .movePlayerUp(3)
        assertEquals(start.map { it.id }.toSet(), result.map { it.id }.toSet())
        assertEquals(start.size, result.size)
    }

    @Test
    fun movePlayerUp_gefolgtVonMovePlayerDown_stelltUrsprungWiederHer() {
        // Nachbar-Swap ist involutorisch: hoch an Position i, dann runter an
        // Position i-1 (dem neuen Index des verschobenen Elements) macht den
        // Swap exakt rueckgaengig.
        val start = listOf(a, b, c)
        val result = start.movePlayerUp(2).movePlayerDown(1)
        assertEquals(start, result)
    }

    @Test
    fun removePlayerAt_mehrfachBisZurUntergrenzeHaeltMengeKonsistent() {
        // Von 4 auf 2 entfernen; ab dann bleibt die Liste stabil (Untergrenze).
        var list = listOf(a, b, c, d)
        list = list.removePlayerAt(0)
        assertEquals(listOf(b, c, d), list)
        list = list.removePlayerAt(0)
        assertEquals(listOf(c, d), list)
        // Am Minimum angekommen: weitere Removes sind No-ops (Identitaet).
        val atMin = list
        list = list.removePlayerAt(0)
        assertSame(atMin, list)
        list = list.removePlayerAt(1)
        assertSame(atMin, list)
    }
}
