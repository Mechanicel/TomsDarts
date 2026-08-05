package com.mechanicel.tomsdarts.ui.game

import com.mechanicel.tomsdarts.game.CountUpMode
import com.mechanicel.tomsdarts.game.CountUpState
import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests fuer [CountUpUiAdapter]: die Uebersetzung des [CountUpState] in den
 * [PlayerBoardUi.CountUp]-Anzeige-Kern (Runde, Punkte), inklusive der
 * Sudden-Death-Runden und der Konsistenz zur EINEN Formel-Quelle in
 * [CountUpState] (der Adapter rechnet die Runde bewusst nicht selbst nach).
 * Reines JUnit, kein Robolectric (der Adapter hat keinen Android-/Room-Bezug).
 */
class CountUpUiAdapterTest {

    private val adapter = CountUpUiAdapter()
    private val mode = CountUpMode()
    private val config = GameConfig()

    /** [CountUpUiAdapter.board] typisiert auf die konkrete Count-Up-Unterart. */
    private fun board(state: CountUpState): PlayerBoardUi.CountUp =
        adapter.board(state) as PlayerBoardUi.CountUp

    @Test
    fun board_bildetLeerenStartzustandAb() {
        assertEquals(
            PlayerBoardUi.CountUp(round = 1, points = 0),
            board(CountUpState.initial()),
        )
    }

    @Test
    fun board_bildetRundeUndPunkteMittenImLegAb() {
        // 13 Darts -> Runde 5 (13/3 + 1).
        val board = board(CountUpState(dartsThrown = 13, points = 267))
        assertEquals(5, board.round)
        assertEquals(267, board.points)
    }

    @Test
    fun board_suddenDeathRundeNeun_nachAchtVollenRunden() {
        // 24 Darts = 8 volle Runden -> Runde 9 (Verlaengerung).
        assertEquals(9, board(CountUpState(dartsThrown = 24, points = 480)).round)
        assertEquals(10, board(CountUpState(dartsThrown = 27, points = 520)).round)
    }

    @Test
    fun checkout_istImmerNull_countUpKenntKeinenVorschlag() {
        assertNull(adapter.checkout(CountUpState.initial(), config))
        assertNull(adapter.checkout(CountUpState(dartsThrown = 24, points = 480), config))
    }

    @Test
    fun board_folgtDurchgehendDerEinenFormelQuelleAusCountUpState_ueberEchtenSpielverlauf() {
        // Konsistenz-Regression: die Adapter-Werte weichen zu KEINEM Zeitpunkt
        // eines echten Spielverlaufs von den abgeleiteten Eigenschaften des
        // CountUpState ab - der Adapter rechnet die Formel nicht selbst nach.
        var current = mode.initialState(config)
        val darts = (1..30).map { i ->
            when (i % 3) {
                0 -> Dart.miss()
                1 -> Dart.single((i % 20) + 1)
                else -> Dart.triple(((i * 7) % 20) + 1)
            }
        }
        darts.forEach { dart ->
            current = mode.applyDart(current, dart, config).newState
            val board = board(current)
            assertEquals(current.round, board.round)
            assertEquals(current.points, board.points)
        }
    }
}
