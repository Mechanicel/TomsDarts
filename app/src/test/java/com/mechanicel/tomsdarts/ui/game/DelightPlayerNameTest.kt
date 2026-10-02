package com.mechanicel.tomsdarts.ui.game

import com.mechanicel.tomsdarts.ui.input.DartInputState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests von [delightPlayerName]: der Werfer-Name erscheint im Feier-Untertitel
 * nur bei mehreren Spielern und aufloesbarer ID (ADR-0039).
 */
class DelightPlayerNameTest {

    private val tom = PlayerScoreUi(1L, "Tom", PlayerBoardUi.X01(501), 0, 0, isCurrent = true)
    private val anna = PlayerScoreUi(2L, "Anna", PlayerBoardUi.X01(501), 0, 0, isCurrent = false)

    private fun playing(players: List<PlayerScoreUi>) = GameUiState.Playing(
        players = players,
        startScore = 501,
        input = DartInputState(),
        currentLegNumber = 1,
        currentSetNumber = 1,
        legsToWin = 1,
        setsToWin = 1,
    )

    @Test
    fun nameWirdImSpielAufgeloest() {
        assertEquals("Anna", delightPlayerName(playing(listOf(tom, anna)), 2L))
    }

    @Test
    fun nameWirdAuchNachLegUndMatchSiegAufgeloest() {
        val legWon = GameUiState.LegWon(listOf(tom, anna), "Tom", "Anna", 2, 15)
        val matchWon = GameUiState.MatchWon(listOf(tom, anna), "Tom", 12)
        assertEquals("Tom", delightPlayerName(legWon, 1L))
        assertEquals("Tom", delightPlayerName(matchWon, 1L))
    }

    @Test
    fun keinNameBeiEinemSpieler() {
        assertNull(delightPlayerName(playing(listOf(tom)), 1L))
    }

    @Test
    fun keinNameBeiUnbekannterOderFehlenderId() {
        assertNull(delightPlayerName(playing(listOf(tom, anna)), 99L))
        assertNull(delightPlayerName(playing(listOf(tom, anna)), null))
    }

    @Test
    fun keinNameOhneSpielzustand() {
        assertNull(delightPlayerName(GameUiState.Loading, 1L))
        assertNull(delightPlayerName(GameUiState.Error, 1L))
        assertNull(delightPlayerName(GameUiState.NoPlayer, 1L))
    }

    @Test
    fun leererNameWirdNichtGezeigt() {
        assertNull(delightPlayerName(playing(listOf(tom.copy(name = " "), anna)), 1L))
    }
}
