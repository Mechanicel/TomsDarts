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
}
