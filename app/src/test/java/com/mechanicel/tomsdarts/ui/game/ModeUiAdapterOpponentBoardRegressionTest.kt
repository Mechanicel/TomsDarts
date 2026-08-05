package com.mechanicel.tomsdarts.ui.game

import com.mechanicel.tomsdarts.game.AroundTheClockMode
import com.mechanicel.tomsdarts.game.CountUpMode
import com.mechanicel.tomsdarts.game.CricketMode
import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.ShanghaiMode
import com.mechanicel.tomsdarts.game.X01Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Regressionstest fuer die gegner-bewusste [ModeUiAdapter.board]-Ueberladung
 * (Killer-Infrastruktur, PR A) auf ALLEN fuenf Bestandsadaptern: keiner von
 * ihnen hat Gegnerbezug, daher MUSS die zweistellige Variante ueber den
 * Interface-Default exakt dasselbe Board liefern wie die einstellige -
 * unabhaengig vom Inhalt der uebergebenen Gegnerliste (leer, ein Gegner, viele
 * Gegner). [GameModeInfrastructureTest] deckt denselben Pfad bereits
 * INDIREKT ueber das [GameViewModel] ab (das intern die zweistellige Variante
 * ruft); dieser Test prueft ihn direkt und explizit am Adapter. Reines JUnit,
 * kein Robolectric (die Adapter haben keinen Android-/Room-Bezug).
 */
class ModeUiAdapterOpponentBoardRegressionTest {

    private val config = GameConfig(startScore = 501, doubleOut = true)

    @Test
    fun x01_zweistelligeVariante_ignoriertGegnerUndLiefertIdentischesBoard() {
        val adapter = X01UiAdapter()
        val mode = X01Mode()
        val state = mode.applyDart(mode.initialState(config), Dart.triple(20), config).newState
        val oneOpponent = listOf(mode.initialState(config))
        val manyOpponents = listOf(mode.initialState(config), mode.initialState(config), mode.initialState(config))

        val single = adapter.board(state)
        assertEquals(single, adapter.board(state, emptyList()))
        assertEquals(single, adapter.board(state, oneOpponent))
        assertEquals(single, adapter.board(state, manyOpponents))
    }

    @Test
    fun cricket_zweistelligeVariante_ignoriertGegnerUndLiefertIdentischesBoard() {
        val adapter = CricketUiAdapter()
        val mode = CricketMode()
        val state = mode.applyDart(mode.initialState(config), Dart.triple(20), config).newState
        val oneOpponent = listOf(mode.initialState(config))

        val single = adapter.board(state)
        assertEquals(single, adapter.board(state, emptyList()))
        assertEquals(single, adapter.board(state, oneOpponent))
    }

    @Test
    fun aroundTheClock_zweistelligeVariante_ignoriertGegnerUndLiefertIdentischesBoard() {
        val adapter = AroundTheClockUiAdapter()
        val mode = AroundTheClockMode()
        val state = mode.applyDart(mode.initialState(config), Dart.single(1), config).newState
        val oneOpponent = listOf(mode.initialState(config))

        val single = adapter.board(state)
        assertEquals(single, adapter.board(state, emptyList()))
        assertEquals(single, adapter.board(state, oneOpponent))
    }

    @Test
    fun shanghai_zweistelligeVariante_ignoriertGegnerUndLiefertIdentischesBoard() {
        val adapter = ShanghaiUiAdapter()
        val mode = ShanghaiMode()
        val state = mode.applyDart(mode.initialState(config), Dart.single(1), config).newState
        val oneOpponent = listOf(mode.initialState(config))

        val single = adapter.board(state)
        assertEquals(single, adapter.board(state, emptyList()))
        assertEquals(single, adapter.board(state, oneOpponent))
    }

    @Test
    fun countUp_zweistelligeVariante_ignoriertGegnerUndLiefertIdentischesBoard() {
        val adapter = CountUpUiAdapter()
        val mode = CountUpMode()
        val state = mode.applyDart(mode.initialState(config), Dart.single(1), config).newState
        val oneOpponent = listOf(mode.initialState(config))

        val single = adapter.board(state)
        assertEquals(single, adapter.board(state, emptyList()))
        assertEquals(single, adapter.board(state, oneOpponent))
    }

    @Test
    fun boardWertIstNichtDerLeereStartzustand_sanityCheckDassDerTestUeberhauptEtwasPrueft() {
        // Sanity-Check: die Board-Werte oben sind NICHT alle trivial gleich dem
        // Startzustand - sonst waere obige Gleichheit selbst bei einem
        // kaputten Interface-Default (das z.B. immer den Startzustand
        // liefert) versehentlich gruen.
        val adapter = X01UiAdapter()
        val mode = X01Mode()
        val fresh = adapter.board(mode.initialState(config))
        val afterThrow = adapter.board(mode.applyDart(mode.initialState(config), Dart.triple(20), config).newState)
        assertNotEquals(fresh, afterThrow)
    }
}
