package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.X01Mode
import com.mechanicel.tomsdarts.game.X01State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Happy-Path-Basistests fuer [LegEngine] (Einzelspieler, ein Leg) mit [X01Mode]
 * als konkretem Modus. Reines JUnit, kein Robolectric.
 *
 * Deckt regulaere Aufnahme, Bust-Revert, Sofort-Checkout, Aufnahme-Wechsel,
 * No-op-Verhalten und die Throw-/Turn-Daten ab. Das systematische Abhaerten
 * (weitere Randfaelle) uebernimmt der tester-Workflow.
 */
class LegEngineTest {

    private val config = GameConfig(startScore = 501, doubleOut = true)

    private fun engine(start: Int = 501): LegEngine<X01State> =
        LegEngine(X01Mode(), config.copy(startScore = start))

    @Test
    fun initial_zustandIstStartScoreUndLeereAufnahme() {
        val e = engine()
        assertEquals(X01State(501), e.state)
        assertEquals(0, e.dartsInTurn)
        assertFalse(e.isLegWon)
        assertFalse(e.isTurnEnded)
    }

    @Test
    fun regulaereAufnahme_dreiTriple20_reduziertAuf321() {
        val e = engine()

        val r1 = e.applyDart(Dart.triple(20))
        assertTrue(r1.accepted)
        assertEquals(0, r1.dartIndex)
        assertEquals(60, r1.scored)
        assertEquals(60, r1.totalScored)
        assertFalse(r1.turnEnded)
        assertFalse(r1.bust)
        assertFalse(r1.legWon)
        assertEquals(X01State(441), e.state)
        assertEquals(1, e.dartsInTurn)

        val r2 = e.applyDart(Dart.triple(20))
        assertEquals(1, r2.dartIndex)
        assertEquals(120, r2.totalScored)
        assertFalse(r2.turnEnded)
        assertEquals(X01State(381), e.state)

        val r3 = e.applyDart(Dart.triple(20))
        assertEquals(2, r3.dartIndex)
        assertEquals(180, r3.totalScored)
        // Aufnahme endet nach drei Darts, aber weder Bust noch Leg-Gewinn.
        assertTrue(r3.turnEnded)
        assertFalse(r3.bust)
        assertFalse(r3.legWon)
        assertEquals(X01State(321), e.state)
        assertEquals(3, e.dartsInTurn)
        assertTrue(e.isTurnEnded)
        assertFalse(e.isLegWon)
    }

    @Test
    fun bust_setztModusZustandAufAufnahmeStartZurueck() {
        // Aufnahme-Start 100: T20 -> 40, dann T20 ueberwirft (40 - 60 < 0) -> Bust.
        val e = engine(start = 100)

        val r1 = e.applyDart(Dart.triple(20))
        assertEquals(X01State(40), e.state)
        assertEquals(60, r1.totalScored)

        val r2 = e.applyDart(Dart.triple(20))
        assertTrue(r2.accepted)
        assertTrue(r2.bust)
        assertFalse(r2.legWon)
        assertTrue(r2.turnEnded)
        assertEquals(0, r2.scored)
        // Aufnahme wird komplett verworfen -> totalScored 0.
        assertEquals(0, r2.totalScored)
        // Modus-Zustand zurueck auf Aufnahme-Startzustand (100), nicht 40.
        assertEquals(X01State(100), e.state)
        // Throw-Daten der Aufnahme bleiben fuer die Persistenz erhalten (2 Darts).
        val snap = e.snapshot()
        assertEquals(2, snap.dartsInTurn)
        assertEquals(listOf(Dart.triple(20), Dart.triple(20)), snap.turnDarts)
        assertTrue(snap.turnBust)
        assertEquals(0, snap.turnScored)
        assertEquals(X01State(100), snap.turnStartState)
    }

    @Test
    fun checkout_gewinntLegSofortUndBeendetAufnahme() {
        // Rest 40 -> Double 20 -> 0 mit Double -> legWon (sofort, < 3 Darts).
        val e = engine(start = 40)

        val r = e.applyDart(Dart.double(20))
        assertTrue(r.accepted)
        assertTrue(r.legWon)
        assertFalse(r.bust)
        assertTrue(r.turnEnded)
        assertEquals(40, r.scored)
        assertEquals(40, r.totalScored)
        assertEquals(0, r.dartIndex)
        assertEquals(X01State(0), e.state)
        assertTrue(e.isLegWon)
        assertTrue(e.isTurnEnded)
        assertEquals(1, e.dartsInTurn)
    }

    @Test
    fun startNewTurn_beginntNaechsteAufnahmeMitAktuellemRest() {
        val e = engine(start = 120)
        // Volle regulaere Aufnahme: 3x Single 20 -> Rest 60.
        e.applyDart(Dart.single(20))
        e.applyDart(Dart.single(20))
        e.applyDart(Dart.single(20))
        assertEquals(X01State(60), e.state)
        assertTrue(e.isTurnEnded)

        val started = e.startNewTurn()
        assertTrue(started)
        assertFalse(e.isTurnEnded)
        assertEquals(0, e.dartsInTurn)
        val snap = e.snapshot()
        // Aufnahme-Startzustand = aktueller Rest.
        assertEquals(X01State(60), snap.turnStartState)
        assertEquals(X01State(60), snap.state)
        assertTrue(snap.turnDarts.isEmpty())
        assertFalse(snap.turnBust)
    }

    @Test
    fun startNewTurn_istNoOpWennLegGewonnen() {
        val e = engine(start = 40)
        e.applyDart(Dart.double(20))
        assertTrue(e.isLegWon)

        val started = e.startNewTurn()
        assertFalse(started)
        // Zustand unveraendert.
        assertEquals(X01State(0), e.state)
        assertTrue(e.isLegWon)
        assertTrue(e.isTurnEnded)
    }

    @Test
    fun applyDart_istNoOpBeiVollerAufnahme() {
        val e = engine(start = 200)
        e.applyDart(Dart.single(20))
        e.applyDart(Dart.single(20))
        e.applyDart(Dart.single(20))
        assertEquals(X01State(140), e.state)
        assertTrue(e.isTurnEnded)

        val r = e.applyDart(Dart.triple(20))
        assertFalse(r.accepted)
        assertNull(r.outcome)
        assertEquals(-1, r.dartIndex)
        // Zustand unveraendert: weiterhin 140, weiterhin 3 Darts.
        assertEquals(X01State(140), e.state)
        assertEquals(3, e.dartsInTurn)
    }

    @Test
    fun applyDart_istNoOpBeiGewonnenemLeg() {
        val e = engine(start = 40)
        e.applyDart(Dart.double(20))
        assertTrue(e.isLegWon)

        val r = e.applyDart(Dart.triple(20))
        assertFalse(r.accepted)
        assertNull(r.outcome)
        assertEquals(-1, r.dartIndex)
        assertEquals(X01State(0), e.state)
        assertTrue(e.isLegWon)
    }

    @Test
    fun throwDaten_dartIndexUndScoredPlausibelUeberAufnahme() {
        val e = engine(start = 180)
        val r1 = e.applyDart(Dart.triple(20)) // 60
        val r2 = e.applyDart(Dart.single(20)) // 20
        val r3 = e.applyDart(Dart.single(5))  // 5

        assertEquals(listOf(0, 1, 2), listOf(r1.dartIndex, r2.dartIndex, r3.dartIndex))
        assertEquals(listOf(60, 20, 5), listOf(r1.scored, r2.scored, r3.scored))
        assertEquals(listOf(60, 80, 85), listOf(r1.totalScored, r2.totalScored, r3.totalScored))
        assertEquals(X01State(95), e.state)
        assertEquals(3, e.snapshot().dartsInTurn)
        assertEquals(85, e.snapshot().turnScored)
    }
}
