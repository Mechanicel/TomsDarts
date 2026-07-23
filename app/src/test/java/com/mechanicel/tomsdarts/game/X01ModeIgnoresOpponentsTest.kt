package com.mechanicel.tomsdarts.game

import com.mechanicel.tomsdarts.game.engine.MatchEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tester-Haertung (Modus-Infrastruktur, PR A): belegt explizit, dass [X01Mode]
 * den neuen lesenden `opponents`-Parameter von [GameMode.applyDart] ignoriert -
 * das X01-Verhalten bleibt nach dem Refactor exakt identisch, unabhaengig davon,
 * ob und welche Gegner-Zustaende uebergeben werden. Reines JUnit, kein
 * Robolectric, deterministisch.
 */
class X01ModeIgnoresOpponentsTest {

    private val mode = X01Mode()
    private val config = GameConfig(startScore = 501, doubleOut = true)

    @Test
    fun applyDart_gleichesErgebnisMitLeererUndNichtLeererGegnerListe() {
        val ohneGegner = mode.applyDart(X01State(501), Dart.triple(20), config)
        val mitGegnern = mode.applyDart(
            X01State(501),
            Dart.triple(20),
            config,
            opponents = listOf(X01State(120), X01State(0), X01State(501)),
        )
        assertEquals(ohneGegner, mitGegnern)
    }

    @Test
    fun applyDart_bustPfad_unveraendertMitGegnern() {
        val ohneGegner = mode.applyDart(X01State(40), Dart.triple(20), config) // 40-60<0 -> Bust
        val mitGegnern = mode.applyDart(
            X01State(40),
            Dart.triple(20),
            config,
            opponents = listOf(X01State(1)),
        )
        assertEquals(ohneGegner, mitGegnern)
        assertEquals(true, ohneGegner.bust)
    }

    @Test
    fun applyDart_checkoutPfad_unveraendertMitGegnern() {
        val ohneGegner = mode.applyDart(X01State(40), Dart.double(20), config) // legWon
        val mitGegnern = mode.applyDart(
            X01State(40),
            Dart.double(20),
            config,
            opponents = listOf(X01State(301), X01State(2)),
        )
        assertEquals(ohneGegner, mitGegnern)
        assertEquals(true, ohneGegner.legWon)
    }

    @Test
    fun matchEngine_zweiVsVierSpieler_werferRestpunkteIdentischWeilX01GegnerIgnoriert() {
        // Selbe Wurfserie fuer den ersten Werfer (A), einmal mit 2 und einmal mit
        // 4 Teilnehmern: X01 wertet ausschliesslich den eigenen Rest, die
        // Gegner-Anzahl/-Zustaende duerfen den Verlauf nicht beeinflussen.
        val darts = listOf(Dart.triple(20), Dart.single(5), Dart.double(18))

        val twoPlayers = MatchEngine(
            mode = X01Mode(),
            config = GameConfig(startScore = 170, doubleOut = true, legsToWin = 5, setsToWin = 5),
            playerIds = listOf(1L, 2L),
        )
        val fourPlayers = MatchEngine(
            mode = X01Mode(),
            config = GameConfig(startScore = 170, doubleOut = true, legsToWin = 5, setsToWin = 5),
            playerIds = listOf(1L, 2L, 3L, 4L),
        )

        darts.forEach {
            twoPlayers.applyDart(it)
            fourPlayers.applyDart(it)
        }

        assertEquals(twoPlayers.playerStates[0].state, fourPlayers.playerStates[0].state)
    }
}
