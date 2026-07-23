package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.AroundTheClockMode
import com.mechanicel.tomsdarts.game.AroundTheClockState
import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integrationstests fuer [AroundTheClockMode] durch die echten
 * [LegEngine]/[MatchEngine] hindurch (kein Fake-Modus), analog zu
 * [CricketMatchIntegrationTest] und [MatchEngineOpponentProviderTest]. Reines
 * JUnit, kein Robolectric, deterministisch.
 *
 * Belegt, dass der Modus mit der Aufnahme-Buendelung (3 Darts), dem
 * Spieler-/Aufnahmewechsel, dem fehlenden Bust sowie dem sofortigen
 * Aufnahme-Ende bei Leg-Gewinn der Engines korrekt zusammenspielt, und dass
 * Around the Clock die Gegner-Zustaende auch ueber die echte MatchEngine
 * hinweg nachweislich ignoriert.
 */
class AroundTheClockMatchIntegrationTest {

    private val playerA = 1L
    private val playerB = 2L
    private val config = GameConfig(legsToWin = 1, setsToWin = 1)

    @Test
    fun legEngine_buendeltNoOpDartsGenauSowieTreffer_zuAufnahmenVonDreiDarts() {
        // Auch No-Op-Darts (falsche Zahl) zaehlen zur 3-Darts-Kadenz der
        // Aufnahme; Around the Clock hat keine Sonderbehandlung dafuer.
        val leg = LegEngine(mode = AroundTheClockMode(), config = config)

        val d1 = leg.applyDart(Dart.single(15)) // falsche Zahl (Ziel=1) -> No-Op
        assertFalse(d1.turnEnded)
        assertEquals(1, leg.dartsInTurn)

        val d2 = leg.applyDart(Dart.single(1)) // Treffer -> Ziel 2
        assertFalse(d2.turnEnded)
        assertEquals(2, leg.dartsInTurn)
        assertEquals(AroundTheClockState(2), leg.state)

        val d3 = leg.applyDart(Dart.miss()) // Miss -> No-Op, aber 3. Dart der Aufnahme
        assertTrue("Aufnahme endet nach genau 3 Darts, auch bei No-Ops", d3.turnEnded)
        assertEquals(AroundTheClockState(2), leg.state)
    }

    @Test
    fun matchEngine_wechseltSpielerNachDreiDarts_ohneBustUndOhneGegnerEinfluss() {
        val engine = MatchEngine(
            mode = AroundTheClockMode(),
            config = config,
            playerIds = listOf(playerA, playerB),
        )

        // A: 3 Darts. 1. und 2. Dart treffen das (jeweils aktuelle) Ziel
        // (1, dann 2) und ruecken vor; der 3. Dart (Bull) ist ein No-Op. Kein
        // Bust, kein Legwin, Aufnahme endet trotzdem nach genau 3 Darts -> B ist dran.
        val a1 = engine.applyDart(Dart.single(1))
        assertFalse(a1.bust)
        assertEquals(1, a1.dartResult!!.scored)
        val a2 = engine.applyDart(Dart.single(2))
        assertEquals(1, a2.dartResult!!.scored)
        val a3 = engine.applyDart(Dart.bull())
        assertEquals(0, a3.dartResult!!.scored)
        assertTrue(a3.turnEnded)
        assertEquals(playerB, engine.currentPlayerId)

        val aState = engine.playerStates.first { it.playerId == playerA }.state
        assertEquals(AroundTheClockState(3), aState)

        // B wirft: A ist weit voraus (Ziel 3), aber B beginnt bei Ziel 1 - der
        // fortgeschrittene Gegner-Zustand darf B's eigenen Fortschritt nicht
        // beeinflussen (Around the Clock ignoriert opponents).
        val b1 = engine.applyDart(Dart.single(1))
        assertEquals(1, b1.dartResult!!.scored)
        val bState = engine.playerStates.first { it.playerId == playerB }.state
        assertEquals(AroundTheClockState(2), bState)
        assertFalse(b1.bust)
    }

    @Test
    fun matchEngine_legGewinnBeendetAufnahmeSofort_auchVorDemDrittenDart() {
        // A ist bereits kurz vor dem Sieg (Ziel 20) und trifft mit dem ERSTEN
        // Dart der Aufnahme -> Aufnahme muss sofort enden, auch ohne 3 Darts.
        val engine = MatchEngine(
            mode = AroundTheClockMode(),
            config = config,
            playerIds = listOf(playerA, playerB),
        )

        // A auf Ziel 20 vorspulen: nur an A's eigenen Aufnahmen die jeweils
        // korrekte Zielzahl werfen, B's zwischenzeitliche Aufnahmen mit
        // wirkungslosen Miss-Darts ueberbruecken (No-Op, aendert B's Ziel nicht).
        var aTarget = 1
        while (aTarget < AroundTheClockState.TOTAL) {
            if (engine.currentPlayerId == playerA) {
                engine.applyDart(Dart.single(aTarget))
                aTarget++
            } else {
                engine.applyDart(Dart.miss())
            }
        }
        // A ist am Zug oder wird es gleich sein; falls gerade B dran ist, dessen
        // Aufnahme mit Miss-Darts zu Ende bringen.
        while (engine.currentPlayerId != playerA) {
            engine.applyDart(Dart.miss())
        }
        val aBeforeWin = engine.playerStates.first { it.playerId == playerA }.state
        assertEquals(AroundTheClockState(20), aBeforeWin)

        val winningDart = engine.applyDart(Dart.single(20))
        assertTrue(winningDart.legWon)
        assertTrue(winningDart.turnEnded)
        assertTrue(winningDart.setWon)
        assertTrue(winningDart.matchWon)
        assertEquals(playerA, winningDart.matchWinnerId)
        assertFalse(winningDart.bust)
    }

    @Test
    fun legEngine_gegnerZustaendeBeeinflussenAroundTheClockNieUeberDenEchtenProvider() {
        // Live-Gegner-Provider liefert einen weit fortgeschrittenen Zustand; das
        // darf die Wertung des werfenden Spielers nicht veraendern (Gegenpol zu
        // Cricket, wo das Gegner-Feld direkt die Wertung beeinflusst).
        var opponentState = AroundTheClockState(1)
        val leg = LegEngine(
            mode = AroundTheClockMode(),
            config = config,
            opponents = { listOf(opponentState) },
        )

        val before = leg.applyDart(Dart.single(1))
        assertEquals(1, before.outcome!!.scored)
        assertEquals(AroundTheClockState(2), leg.state)

        // Gegner "springt" auf Ziel 20 - fuer Around the Clock voellig irrelevant.
        opponentState = AroundTheClockState(20)

        val after = leg.applyDart(Dart.single(2))
        assertEquals(1, after.outcome!!.scored)
        assertEquals(AroundTheClockState(3), leg.state)
    }
}
