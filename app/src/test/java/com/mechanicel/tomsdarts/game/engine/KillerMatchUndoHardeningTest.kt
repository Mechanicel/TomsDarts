package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.KillerMode
import com.mechanicel.tomsdarts.game.KillerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test-Gate-Haertung fuer [KillerMode] auf [MatchEngine]-Ebene, ergaenzend zu den
 * Happy-Path-Faellen in [KillerMatchIntegrationTest]. Schwerpunkt: Undo-Rundreisen
 * (Lebensabzug, Eliminierung, Sieg-Dart) und die Stabilitaet der - aus dem
 * eingefrorenen [GameConfig.killerSeed] abgeleiteten - Zielzahlen ueber Undo,
 * `commitLegTransition` und Leg-Wechsel hinweg (der zentrale ADR-0031-Vertrag:
 * [com.mechanicel.tomsdarts.game.GameMode.initialState] darf NIE wuerfeln, weil
 * die Engine den Startzustand bei jedem dieser Ereignisse neu erzeugt).
 *
 * Reines JUnit, kein Robolectric, deterministisch.
 */
class KillerMatchUndoHardeningTest {

    private val tom = 10L
    private val anna = 20L
    private val bjoern = 30L

    private val mode = KillerMode()

    private fun engine(players: List<Long>, legsToWin: Int = 1): MatchEngine<KillerState> =
        MatchEngine(
            mode = mode,
            config = GameConfig(legsToWin = legsToWin, setsToWin = 1, killerSeed = SEED),
            playerIds = players,
        )

    private fun numberOf(index: Int): Int = KillerState.numberFor(SEED, index)

    private fun MatchEngine<KillerState>.stateOf(playerId: Long): KillerState =
        playerStates.first { it.playerId == playerId }.state

    private fun MatchEngine<KillerState>.numbers(): List<Int> = playerStates.map { it.state.number }

    private fun MatchEngine<KillerState>.isEliminated(playerId: Long): Boolean {
        val target = stateOf(playerId)
        val opponents = playerStates.filter { it.playerId != playerId }.map { it.state }
        return mode.isEliminated(target, opponents)
    }

    // --- Lebensabzug per Undo zurueck -------------------------------------------

    @Test
    fun undo_nimmtEinenLebensabzugZurueck_lebenSindWiederDaUndKillerStatusBleibtErhalten() {
        val e = engine(listOf(tom, anna))
        val n0 = numberOf(0)
        val n1 = numberOf(1)

        e.applyDart(Dart.double(n0)) // Tom wird Killer.
        e.applyDart(Dart.double(n1)) // Treffer auf Anna (Leben 2).
        assertEquals(KillerState.LIVES - 1, KillerState.livesOf(n1, listOf(e.stateOf(tom))))

        assertTrue(e.undoLastDart())

        // Der Treffer ist weg, die Killer-Werdung (der davor liegende Dart)
        // bleibt unangetastet.
        assertEquals(KillerState.LIVES, KillerState.livesOf(n1, listOf(e.stateOf(tom))))
        assertTrue("nur der LETZTE Dart wird zurueckgenommen", e.stateOf(tom).isKiller)
        assertTrue(e.stateOf(tom).hitsOn.isEmpty())
    }

    @Test
    fun undo_nimmtDieKillerWerdungSelbstZurueck_replaySpieltSieDeterministischWiederEin() {
        val e = engine(listOf(tom, anna))
        val n0 = numberOf(0)

        e.applyDart(Dart.double(n0))
        assertTrue(e.stateOf(tom).isKiller)

        assertTrue(e.undoLastDart())
        assertFalse("Killer-Werdung zurueckgenommen", e.stateOf(tom).isKiller)

        // Rundreise: derselbe Dart erneut -> exakt derselbe Killer-Status.
        e.applyDart(Dart.double(n0))
        assertTrue(e.stateOf(tom).isKiller)
    }

    // --- Eliminierung rueckgaengig: Spieler ist wieder in der Rotation ---------

    @Test
    fun undo_nimmtEineEliminierungZurueck_spielerIstWiederInDerRotation() {
        val e = engine(listOf(tom, anna, bjoern))
        val n0 = numberOf(0)
        val n1 = numberOf(1)

        e.applyDart(Dart.double(n0)) // Tom wird Killer.
        e.applyDart(Dart.double(n1)) // Treffer 1.
        e.applyDart(Dart.double(n1)) // Treffer 2; Aufnahme endet.
        repeat(3) { e.applyDart(Dart.miss()) } // Anna
        repeat(3) { e.applyDart(Dart.miss()) } // Bjoern
        e.applyDart(Dart.double(n1)) // Tom: dritter Treffer eliminiert Anna.
        assertTrue(e.isEliminated(anna))
        e.applyDart(Dart.miss())
        e.applyDart(Dart.miss())
        assertEquals("Anna eliminiert -> uebersprungen", bjoern, e.currentPlayerId)

        // Die drei Darts der eliminierenden Aufnahme zurueck (Treffer + 2 Fuellungen).
        assertTrue(e.undoLastDart())
        assertTrue(e.undoLastDart())
        assertTrue(e.undoLastDart())
        assertFalse("Anna lebt wieder", e.isEliminated(anna))

        // Weiterspielen OHNE den eliminierenden Treffer: die Rotation faellt
        // NICHT mehr auf Bjoern, sondern regulaer auf Anna - Beweis, dass der
        // Skip wirklich an die (jetzt zurueckgenommene) Eliminierung gebunden war.
        repeat(3) { e.applyDart(Dart.miss()) }
        assertEquals(anna, e.currentPlayerId)
    }

    // --- Sieg-Dart-Undo ----------------------------------------------------------

    @Test
    fun undoLastDart_nachLegSieg_stelltAufnahmeUndGegnerLebenWiederHer_undSiegLaesstSichWiederholen() {
        val e = engine(listOf(tom, anna), legsToWin = 2)
        val n0 = numberOf(0)
        val n1 = numberOf(1)

        e.applyDart(Dart.double(n0))
        e.applyDart(Dart.double(n1))
        e.applyDart(Dart.double(n1))
        repeat(3) { e.applyDart(Dart.miss()) } // Anna
        val win = e.applyDart(Dart.double(n1)) // Tom: dritter Treffer -> Sieg.
        assertTrue(win.legWon)
        assertEquals(tom, win.legWinnerId)
        assertFalse(e.isMatchWon)

        assertTrue(e.undoLastDart())

        // Anna lebt wieder (letzter Treffer zurueckgenommen). Der Sieg-Dart war
        // der EINZIGE Dart seiner Aufnahme (Toms vorherige Aufnahme hatte die
        // Zahl schon auf ein Leben gebracht) - nach dem Undo ist die Aufnahme
        // wieder leer, kein Leg-Sieg mehr verbucht.
        assertFalse(e.isEliminated(anna))
        assertEquals(tom, e.currentPlayerId)
        assertEquals(0, e.playerStates.first { it.playerId == tom }.legSnapshot.dartsInTurn)
        assertEquals(0, e.playerStates.first { it.playerId == tom }.legsWonInSet)

        // Erneut werfen -> derselbe Sieg stellt sich wieder ein.
        val winAgain = e.applyDart(Dart.double(n1))
        assertTrue(winAgain.legWon)
        assertEquals(tom, winAgain.legWinnerId)
        assertEquals(1, e.playerStates.first { it.playerId == tom }.legsWonInSet)
    }

    // --- Zahlen bleiben stabil: Undo, commitLegTransition, Leg-Wechsel ----------

    @Test
    fun zahlenBleibenUeberUndoUndLegWechselStabil_seedDeterminismus() {
        val e = engine(listOf(tom, anna), legsToWin = 2)
        val expected = listOf(numberOf(0), numberOf(1))
        assertEquals(expected, e.numbers())

        // Ein paar Darts werfen und wieder zurueck - Identitaet bleibt stabil.
        e.applyDart(Dart.double(expected[0]))
        e.applyDart(Dart.miss())
        assertTrue(e.undoLastDart())
        assertTrue(e.undoLastDart())
        assertEquals(expected, e.numbers())
        assertEquals(0, e.dartsThrownInCurrentLeg)

        // Leg 1 gewinnen: Tom wird Killer und trifft Anna dreimal.
        e.applyDart(Dart.double(expected[0]))
        e.applyDart(Dart.double(expected[1]))
        e.applyDart(Dart.double(expected[1]))
        repeat(3) { e.applyDart(Dart.miss()) } // Anna
        val win = e.applyDart(Dart.double(expected[1]))
        assertTrue(win.legWon)
        assertEquals("Rotation: Leg 2 startet bei Anna", anna, e.currentPlayerId)

        assertTrue(e.commitLegTransition())

        // Frisches Leg: dieselben Zahlen (Seed konstant), aber Leben/Killer-Status
        // zurueckgesetzt.
        assertEquals(expected, e.numbers())
        assertTrue(
            "frisches Leg ohne Treffer/Killer-Status",
            e.playerStates.all { !it.state.isKiller && it.state.hitsOn.isEmpty() },
        )
        assertEquals(KillerState.LIVES, KillerState.livesOf(expected[1], listOf(e.stateOf(tom))))
        assertEquals(anna, e.currentPlayerId)

        // Ein weiterer Undo/Redo-Zyklus im neuen Leg - Identitaet bleibt stabil.
        e.applyDart(Dart.miss())
        assertTrue(e.undoLastDart())
        assertEquals(expected, e.numbers())
    }

    private companion object {
        /** Fester Seed fuer alle Tests dieser Datei - beliebig, aber stabil. */
        const val SEED: Long = 555_444_333L
    }
}
