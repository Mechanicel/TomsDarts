package com.mechanicel.tomsdarts.ui.game

import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.KillerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests fuer [KillerUiAdapter]: die Uebersetzung des [KillerState] in den
 * [PlayerBoardUi.Killer]-Anzeige-Kern (Zahl, Killer-Status, abgeleitete Leben).
 * Kern ist die GEGNER-bewusste [ModeUiAdapter.board]-Variante - nur sie kennt die
 * Treffer der Mitspieler und damit die Leben (Inversions-Muster, ADR-0031).
 * Reines JUnit, kein Robolectric (der Adapter hat keinen Android-/Room-Bezug).
 */
class KillerUiAdapterTest {

    private val adapter = KillerUiAdapter()
    private val config = GameConfig(killerSeed = 4711L)

    private val tom = KillerState(number = 5)
    private val anna = KillerState(number = 12)
    private val bjoern = KillerState(number = 18)

    /** Gegner-bewusste [KillerUiAdapter.board]-Variante, typisiert. */
    private fun board(state: KillerState, opponents: List<KillerState>): PlayerBoardUi.Killer =
        adapter.board(state, opponents) as PlayerBoardUi.Killer

    @Test
    fun board_ohneGegnerTreffer_zeigtVolleLebenUndKeinenKiller() {
        val board = board(tom, listOf(anna, bjoern))

        assertEquals(5, board.number)
        assertFalse(board.isKiller)
        assertEquals(KillerState.LIVES, board.lives)
        assertEquals(KillerState.LIVES, board.maxLives)
        assertFalse(board.eliminated)
    }

    @Test
    fun board_leitetLebenAusDenTreffernAllerGegnerAb() {
        // Anna hat Tom einmal, Bjoern ihn zweimal getroffen -> 3 - 3 = 0 Leben.
        val board = board(
            tom,
            listOf(
                anna.copy(isKiller = true, hitsOn = mapOf(5 to 1)),
                bjoern.copy(isKiller = true, hitsOn = mapOf(5 to 2)),
            ),
        )

        assertEquals(0, board.lives)
        assertTrue("keine Leben mehr -> ausgeschieden", board.eliminated)
    }

    @Test
    fun board_zaehltNurTrefferAufDerEigenenZahl() {
        // Bjoerns Treffer liegen auf Annas Zahl (12) - Tom (5) verliert nichts.
        val board = board(tom, listOf(anna, bjoern.copy(isKiller = true, hitsOn = mapOf(12 to 2))))

        assertEquals(KillerState.LIVES, board.lives)
    }

    @Test
    fun board_spiegeltDenKillerStatus() {
        val board = board(tom.copy(isKiller = true), listOf(anna))

        assertTrue(board.isKiller)
        assertEquals(KillerState.LIVES, board.lives)
    }

    @Test
    fun board_einstellig_faelltAufVolleLebenZurueck() {
        // Ohne Gegner-Zustaende sind keine Treffer bekannt: der Fallback zeigt
        // bewusst die vollen Leben statt zu raten.
        val board = adapter.board(tom.copy(isKiller = true)) as PlayerBoardUi.Killer

        assertEquals(5, board.number)
        assertTrue(board.isKiller)
        assertEquals(KillerState.LIVES, board.lives)
        assertEquals(KillerState.LIVES, board.maxLives)
        assertFalse(board.eliminated)
    }

    @Test
    fun checkout_istImmerNull_killerKenntKeinenVorschlag() {
        assertNull(adapter.checkout(tom, config))
        assertNull(adapter.checkout(tom.copy(isKiller = true, hitsOn = mapOf(12 to 3)), config))
    }
}
