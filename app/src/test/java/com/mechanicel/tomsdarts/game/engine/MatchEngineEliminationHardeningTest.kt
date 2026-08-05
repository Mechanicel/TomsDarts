package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.testing.EliminationFakeMode
import com.mechanicel.tomsdarts.testing.EliminationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test-Gate-Haertung der Eliminierungs-Infrastruktur in der [MatchEngine]
 * (Killer-Infrastruktur, PR A), ergaenzend zu den Basis-Faellen in
 * [MatchEngineEliminationTest]. Schwerpunkt: die Wechselwirkung des
 * Eliminierungs-Skips ([GameMode.isEliminated]) mit ALLEN anderen
 * Engine-Mechaniken - Undo ueber mehrere Aufnahmen/Skip-Sequenzen hinweg,
 * Sieg-Pfade (Aufschub/Commit/Undo eines eliminierungs-behafteten Sieges),
 * Mehrfach-Eliminierung ueber viele Aufnahmen sowie Spieler-Index-Stabilitaet
 * bei groesseren Spielerzahlen.
 *
 * Als Modus dient wie in [MatchEngineEliminationTest] der geteilte
 * [EliminationFakeMode] (Test-Fixture, kein Produktions-Modus). Reines JUnit,
 * kein Robolectric, deterministisch.
 */
class MatchEngineEliminationHardeningTest {

    private val playerA = 10L
    private val playerB = 20L
    private val playerC = 30L
    private val playerD = 40L
    private val playerE = 50L

    private fun engine(
        players: List<Long>,
        lives: Int = EliminationFakeMode.DEFAULT_LIVES,
        legsToWin: Int = 1,
    ): MatchEngine<EliminationState> = MatchEngine(
        mode = EliminationFakeMode(lives = lives),
        config = GameConfig(legsToWin = legsToWin, setsToWin = 1),
        playerIds = players,
    )

    private fun MatchEngine<EliminationState>.throwAll(darts: List<Dart>) {
        darts.forEach { applyDart(it) }
    }

    private fun MatchEngine<EliminationState>.targets(): List<Int> =
        playerStates.map { it.state.target }

    // --- Skip x Undo-Ketten -------------------------------------------------

    @Test
    fun mehrereAufnahmenNachEinerEliminierung_undoBisVorDieEliminierung_rotationWiederOhneSkip_undRundreiseIdentisch() {
        val e = engine(listOf(playerA, playerB, playerC, playerD))

        val turn1 = listOf(Dart.single(2), Dart.miss(), Dart.miss()) // A eliminiert B.
        val turn2 = listOf(Dart.miss(), Dart.miss(), Dart.miss()) // C (B uebersprungen).
        val turn3 = listOf(Dart.miss(), Dart.miss(), Dart.miss()) // D.
        val turn4Partial = listOf(Dart.miss()) // A (ueber die Listengrenze, B erneut uebersprungen).
        val allDarts = turn1 + turn2 + turn3 + turn4Partial

        e.throwAll(allDarts)
        assertEquals(playerA, e.currentPlayerId)
        assertEquals(1, e.playerStates[0].legSnapshot.dartsInTurn)
        val statesBeforeRewind = e.playerStates.map { it.state }

        // Alle Darts zurueck: Stand vor der Eliminierung, B ist wieder im Spiel.
        repeat(allDarts.size) { assertTrue(e.undoLastDart()) }
        assertEquals(0, e.dartsThrownInCurrentLeg)
        assertEquals(playerA, e.currentPlayerId)
        assertTrue(e.playerStates.all { it.state.hitsOn.isEmpty() })

        // OHNE zu eliminieren: A wirft eine harmlose Aufnahme -> Rotation OHNE
        // Skip direkt zu B (Beweis: der Skip war wirklich an die Eliminierung
        // gebunden, nicht an feste Reihenfolge).
        e.throwAll(List(LegEngine.MAX_DARTS_PER_TURN) { Dart.miss() })
        assertEquals(playerB, e.currentPlayerId)

        // Zurueck auf den Ausgangspunkt und die urspruengliche Sequenz erneut
        // spielen -> exakt derselbe Endstand (deterministisches Replay).
        repeat(LegEngine.MAX_DARTS_PER_TURN) { assertTrue(e.undoLastDart()) }
        assertEquals(playerA, e.currentPlayerId)
        assertEquals(0, e.dartsThrownInCurrentLeg)

        e.throwAll(allDarts)
        assertEquals(statesBeforeRewind, e.playerStates.map { it.state })
        assertEquals(playerA, e.currentPlayerId)
        assertEquals(1, e.playerStates[0].legSnapshot.dartsInTurn)
        assertEquals(allDarts.size, e.dartsThrownInCurrentLeg)
    }

    @Test
    fun undoMittenInDerSkipSequenz_eliminierungBleibtErhalten_rotationUeberspringtWeiterhin() {
        val e = engine(listOf(playerA, playerB, playerC, playerD))

        val turn1 = listOf(Dart.single(2), Dart.miss(), Dart.miss()) // A eliminiert B.
        val turn2 = listOf(Dart.miss(), Dart.miss(), Dart.miss()) // C.
        val turn3 = listOf(Dart.miss(), Dart.miss(), Dart.miss()) // D.
        e.throwAll(turn1 + turn2 + turn3)
        assertEquals(playerA, e.currentPlayerId)

        // Zwei Darts von D's letzter (skip-getragener) Aufnahme zurueck: mitten
        // in der Skip-Sequenz, WEIT VOR dem Undo der Eliminierung selbst.
        assertTrue(e.undoLastDart())
        assertTrue(e.undoLastDart())
        assertEquals(playerD, e.currentPlayerId)
        assertEquals(1, e.playerStates[3].legSnapshot.dartsInTurn)

        // B bleibt eliminiert (die Eliminierung liegt VOR dem zurueckgenommenen
        // Bereich in der Historie).
        val fake = EliminationFakeMode()
        val othersOfB = listOf(e.playerStates[0].state, e.playerStates[2].state, e.playerStates[3].state)
        assertTrue(fake.isEliminated(e.playerStates[1].state, othersOfB))

        // Weiterspielen: D beendet seine Aufnahme, Rotation ueberspringt B
        // unveraendert weiter (zurueck zu A ueber die Listengrenze).
        e.applyDart(Dart.miss())
        e.applyDart(Dart.miss())
        assertEquals(playerA, e.currentPlayerId)
    }

    // --- Skip x Sieg-Pfade ----------------------------------------------------

    @Test
    fun legSieg_waehrendAndererSpielerEliminiertIst_commit_frischesLegAlleWiederAktiv_starterKorrekt() {
        val e = engine(listOf(playerA, playerB, playerC), legsToWin = 2)

        e.applyDart(Dart.single(2)) // B raus.
        val winResult = e.applyDart(Dart.single(3)) // C raus -> A gewinnt (beide Gegner eliminiert).
        assertTrue(winResult.legWon)
        assertEquals(playerA, winResult.legWinnerId)

        assertTrue(e.commitLegTransition())

        // Startspieler des neuen Legs ist B - der im ALTEN Leg eliminiert war.
        assertEquals(playerB, e.currentPlayerId)
        assertEquals(listOf(1, 2, 3), e.targets())
        // Frisches Leg: niemand mehr eliminiert (auch B nicht, obwohl er es im
        // alten Leg war) - der Skip an der Leg-Grenze ist bewusst weggelassen.
        assertTrue(e.playerStates.all { it.state.hitsOn.isEmpty() })

        // B kann jetzt regulaer werfen (kein Skip, keine Blockade).
        val throwResult = e.applyDart(Dart.miss())
        assertTrue(throwResult.accepted)
        assertEquals(playerB, throwResult.playerId)
    }

    @Test
    fun undoLastDart_nachEliminierendemSiegDart_stelltEliminierungUndMatchStandWiederHer() {
        val e = engine(listOf(playerA, playerB, playerC), legsToWin = 1)

        e.applyDart(Dart.single(2)) // B raus.
        val winResult = e.applyDart(Dart.single(3)) // C raus -> A gewinnt, Match gewonnen.
        assertTrue(winResult.legWon)
        assertTrue(winResult.matchWon)
        assertTrue(e.isMatchWon)

        assertTrue(e.undoLastDart())

        // Match ist wieder offen, A weiterhin (mitten in seiner Aufnahme) am Zug -
        // mit genau dem EINEN verbliebenen Dart (der Sieg-Dart wurde entfernt).
        assertFalse(e.isMatchWon)
        assertEquals(playerA, e.currentPlayerId)
        assertEquals(1, e.playerStates[0].legSnapshot.dartsInTurn)

        // C ist wieder am Leben (der zurueckgenommene Dart betraf sie); B
        // bleibt eliminiert (ihr Dart liegt weiterhin in der Historie).
        val fake = EliminationFakeMode()
        val aState = e.playerStates[0].state
        val bState = e.playerStates[1].state
        val cState = e.playerStates[2].state
        assertFalse(fake.isEliminated(cState, listOf(aState, bState)))
        assertTrue(fake.isEliminated(bState, listOf(aState, cState)))
    }

    // --- Mehrfach-Eliminierung ueber mehrere Aufnahmen -------------------------

    @Test
    fun vierSpieler_zweiNacheinanderEliminiert_rotationPendeltNurNochZwischenDenZweiAktiven() {
        val e = engine(listOf(playerA, playerB, playerC, playerD))

        // A eliminiert B in seiner Aufnahme.
        e.throwAll(listOf(Dart.single(2), Dart.miss(), Dart.miss()))
        assertEquals(playerC, e.currentPlayerId)

        // C eliminiert D in einer SPAETEREN, eigenen Aufnahme.
        e.throwAll(listOf(Dart.single(4), Dart.miss(), Dart.miss()))
        assertEquals(playerA, e.currentPlayerId)

        // Ab jetzt pendelt die Rotation ueber mehrere weitere Aufnahmen nur
        // noch zwischen den zwei verbliebenen aktiven Spielern A und C - nur
        // noch 2 von 4 Spielern werfen ueberhaupt.
        repeat(4) {
            val expected = if (e.currentPlayerId == playerA) playerC else playerA
            e.throwAll(List(LegEngine.MAX_DARTS_PER_TURN) { Dart.miss() })
            assertEquals(expected, e.currentPlayerId)
        }
    }

    // --- playerIndex-Haertung: groessere Spielerzahl + mehrere Zyklen ---------

    @Test
    fun fuenfSpieler_indexBleibtStabil_ueberMehrereAufnahmenUndoUndEinenLegWechsel() {
        val e = engine(listOf(playerA, playerB, playerC, playerD, playerE), legsToWin = 2)
        assertEquals(listOf(1, 2, 3, 4, 5), e.targets())

        // Ein paar Darts werfen und wieder zuruecknehmen - Identitaet bleibt stabil.
        e.throwAll(listOf(Dart.miss(), Dart.miss()))
        e.undoLastDart()
        e.undoLastDart()
        assertEquals(listOf(1, 2, 3, 4, 5), e.targets())

        // A eliminiert B, C, D in seiner eigenen (vollen) Aufnahme - E lebt noch,
        // also KEIN Sieg, die Aufnahme endet regulaer nach 3 Darts.
        e.throwAll(listOf(Dart.single(2), Dart.single(3), Dart.single(4)))
        assertEquals(playerE, e.currentPlayerId) // B, C, D uebersprungen.

        // E wirft eine harmlose Aufnahme -> zurueck zu A (B, C, D weiter uebersprungen).
        e.throwAll(List(LegEngine.MAX_DARTS_PER_TURN) { Dart.miss() })
        assertEquals(playerA, e.currentPlayerId)

        // A eliminiert nun auch E mit einem einzigen Dart -> alle vier Gegner
        // sind erledigt (B/C/D aus A's bisherigen Treffern, E jetzt frisch) -> Sieg.
        val win = e.applyDart(Dart.single(5))
        assertTrue(win.legWon)
        assertEquals(playerA, win.legWinnerId)

        assertTrue(e.commitLegTransition())

        // Nach dem Leg-Wechsel bleibt die Zielzahl (== Sitzplatz-Index + 1) je
        // Spieler exakt stabil - unabhaengig von der vorangegangenen Eliminierung.
        assertEquals(listOf(1, 2, 3, 4, 5), e.targets())
        // Startspieler rotiert regulaer (nextIndex(0) = 1 = B), obwohl B im alten
        // Leg eliminiert war.
        assertEquals(playerB, e.currentPlayerId)

        // Noch ein Undo/Redo-Zyklus im neuen Leg - Identitaet bleibt stabil.
        e.applyDart(Dart.miss())
        e.undoLastDart()
        assertEquals(listOf(1, 2, 3, 4, 5), e.targets())
        assertTrue(e.playerStates.all { it.state.hitsOn.isEmpty() })
    }

    // --- isEliminated-Aufrufdisziplin: live states -----------------------------

    @Test
    fun eliminierungAufDemLetztenDartDerAufnahme_rotationReflektiertDenNeuenZustandSofort() {
        val e = engine(listOf(playerA, playerB, playerC))

        e.applyDart(Dart.miss())
        e.applyDart(Dart.miss())
        // Der DRITTE (letzte) Dart der Aufnahme eliminiert B UND beendet zugleich
        // die Aufnahme - die Rotation darf sich nur auf den Zustand NACH diesem
        // Dart stuetzen, nicht auf einen vor der Aufnahme zwischengespeicherten.
        val result = e.applyDart(Dart.single(2))

        assertTrue(result.turnEnded)
        assertFalse("C lebt noch -> kein Leg-Sieg", result.legWon)
        assertEquals(playerC, result.nextPlayerId)
        assertEquals(playerC, e.currentPlayerId)
    }
}
