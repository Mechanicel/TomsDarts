package com.mechanicel.tomsdarts.ui.game

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.ShanghaiMode
import com.mechanicel.tomsdarts.game.ShanghaiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests fuer [ShanghaiUiAdapter]: die Uebersetzung des [ShanghaiState] in den
 * [PlayerBoardUi.Shanghai]-Anzeige-Kern (Runde, Zielzahl, Punkte, Trefferspur der
 * laufenden Aufnahme), inklusive der Sudden-Death-Runden und der Konsistenz zur
 * EINEN Formel-Quelle in [ShanghaiState] (der Adapter rechnet Runde/Ziel bewusst
 * nicht selbst nach). Reines JUnit, kein Robolectric (der Adapter hat keinen
 * Android-/Room-Bezug).
 */
class ShanghaiUiAdapterTest {

    private val adapter = ShanghaiUiAdapter()
    private val mode = ShanghaiMode()
    private val config = GameConfig()

    /** [ShanghaiUiAdapter.board] typisiert auf die konkrete Shanghai-Unterart. */
    private fun board(state: ShanghaiState): PlayerBoardUi.Shanghai =
        adapter.board(state) as PlayerBoardUi.Shanghai

    @Test
    fun board_bildetLeerenStartzustandAb() {
        assertEquals(
            PlayerBoardUi.Shanghai(round = 1, target = 1, points = 0, visitHits = emptySet()),
            board(ShanghaiState.initial()),
        )
    }

    @Test
    fun board_bildetRundePunkteUndTrefferMittenImLegAb() {
        // 13 Darts -> Runde 5 (13/3 + 1), Ziel 5.
        val state = ShanghaiState(dartsThrown = 13, points = 42, visitHits = setOf(1, 3))
        val board = board(state)
        assertEquals(5, board.round)
        assertEquals(5, board.target)
        assertEquals(42, board.points)
        assertEquals(setOf(1, 3), board.visitHits)
    }

    @Test
    fun board_suddenDeathRundeAcht_zieltWiederAufEins() {
        // 21 Darts = 7 volle Runden -> Runde 8, Ziel zyklisch zurueck auf 1.
        val board = board(ShanghaiState(dartsThrown = 21, points = 84))
        assertEquals(8, board.round)
        assertEquals(1, board.target)
    }

    @Test
    fun board_suddenDeathRundeNeun_zieltAufZwei() {
        val board = board(ShanghaiState(dartsThrown = 24, points = 84))
        assertEquals(9, board.round)
        assertEquals(2, board.target)
    }

    @Test
    fun board_gefuellteTrefferspurWirdUnveraendertDurchgereicht() {
        val board = board(ShanghaiState(dartsThrown = 2, points = 3, visitHits = setOf(1, 2)))
        assertEquals(setOf(1, 2), board.visitHits)
    }

    @Test
    fun checkout_istImmerNull_shanghaiKenntKeinenVorschlag() {
        assertNull(adapter.checkout(ShanghaiState.initial(), config))
        assertNull(adapter.checkout(ShanghaiState(dartsThrown = 30, points = 90), config))
    }

    @Test
    fun board_folgtDurchgehendDerEinenFormelQuelleAusShanghaiState_ueberEchtenSpielverlauf() {
        // Konsistenz-Regression: die Adapter-Werte weichen zu KEINEM Zeitpunkt
        // eines echten Spielverlaufs von den abgeleiteten Eigenschaften des
        // ShanghaiState ab - der Adapter rechnet die Formel nicht selbst nach.
        var current = mode.initialState(config)
        val darts = (1..30).map { i ->
            when (i % 3) {
                0 -> Dart.miss()
                1 -> Dart.single((i % 20) + 1)
                else -> Dart.double(((i * 7) % 20) + 1)
            }
        }
        darts.forEach { dart ->
            val outcome = mode.applyDart(current, dart, config)
            current = outcome.newState
            val board = board(current)
            assertEquals(current.round, board.round)
            assertEquals(current.target, board.target)
            assertEquals(current.points, board.points)
            assertEquals(current.visitHits, board.visitHits)
        }
    }
}
