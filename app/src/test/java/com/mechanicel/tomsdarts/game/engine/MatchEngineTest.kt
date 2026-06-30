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
 * Happy-Path-Basistests fuer [MatchEngine] (Mehrspieler, Legs/Sets) mit
 * [X01Mode] als konkretem Modus. Reines JUnit, kein Robolectric.
 *
 * Deckt Aufnahme-Wechsel, Bust-Wechsel, Match-Gewinn im degenerierten Fall
 * (legs=sets=1), Leg-Rotation bei legsToWin>1, Set-Aggregation bei setsToWin>1
 * und die Zaehler-/Spieler-Konsistenz ab. Das systematische Abhaerten
 * uebernimmt der tester-Workflow.
 */
class MatchEngineTest {

    private val playerA = 10L
    private val playerB = 20L

    private fun engine(
        start: Int = 501,
        legsToWin: Int = 1,
        setsToWin: Int = 1,
        players: List<Long> = listOf(playerA, playerB),
    ): MatchEngine<X01State> = MatchEngine(
        mode = X01Mode(),
        config = GameConfig(
            startScore = start,
            doubleOut = true,
            legsToWin = legsToWin,
            setsToWin = setsToWin,
        ),
        playerIds = players,
    )

    /** Wirft eine volle, nicht checkende Aufnahme (3x Single 1). */
    private fun throwHarmlessTurn(e: MatchEngine<X01State>): MatchDartResult<X01State> {
        e.applyDart(Dart.single(1))
        e.applyDart(Dart.single(1))
        return e.applyDart(Dart.single(1))
    }

    @Test
    fun initial_ersterSpielerIstAmZug() {
        val e = engine()
        assertEquals(playerA, e.currentPlayerId)
        assertEquals(0, e.currentPlayerIndex)
        assertEquals(1, e.currentSetNumber)
        assertEquals(1, e.currentLegNumber)
        assertFalse(e.isMatchWon)
        assertNull(e.matchWinnerId)
        assertEquals(X01State(501), e.playerStates[0].state)
        assertEquals(X01State(501), e.playerStates[1].state)
    }

    @Test
    fun aufnahmeEnde_wechseltZumNaechstenSpielerUndZurueck() {
        val e = engine()

        // A wirft drei harmlose Darts -> Aufnahme endet -> B ist dran.
        val last = throwHarmlessTurn(e)
        assertTrue(last.turnEnded)
        assertFalse(last.legWon)
        assertEquals(playerA, last.playerId)
        assertEquals(playerB, last.nextPlayerId)
        assertEquals(playerB, e.currentPlayerId)

        // B wirft drei harmlose Darts -> zurueck zu A.
        val last2 = throwHarmlessTurn(e)
        assertEquals(playerB, last2.playerId)
        assertEquals(playerA, last2.nextPlayerId)
        assertEquals(playerA, e.currentPlayerId)

        // A's Rest: 501 - 3 = 498; B's Rest ebenso.
        assertEquals(X01State(498), e.playerStates[0].state)
        assertEquals(X01State(498), e.playerStates[1].state)
    }

    @Test
    fun bust_wechseltSpielerUndRevertiertRest() {
        // Start 100: A wirft T20 (->40), dann T20 ueberwirft -> Bust.
        val e = engine(start = 100)
        e.applyDart(Dart.triple(20))
        assertEquals(X01State(40), e.playerStates[0].state)

        val r = e.applyDart(Dart.triple(20))
        assertTrue(r.bust)
        assertTrue(r.turnEnded)
        assertFalse(r.legWon)
        assertEquals(playerA, r.playerId)
        assertEquals(playerB, r.nextPlayerId)
        assertEquals(playerB, e.currentPlayerId)
        // A's Rest auf Aufnahme-Start zurueck (100), nicht 40.
        assertEquals(X01State(100), e.playerStates[0].state)
    }

    @Test
    fun checkout_gewinntMatchBeiLegsUndSetsGleichEins() {
        // Start 40: A checkt mit Double 20 -> Leg = Set = Match.
        val e = engine(start = 40)

        val r = e.applyDart(Dart.double(20))
        assertTrue(r.legWon)
        assertTrue(r.setWon)
        assertTrue(r.matchWon)
        assertEquals(playerA, r.matchWinnerId)
        assertTrue(e.isMatchWon)
        assertEquals(playerA, e.matchWinnerId)
        assertEquals(1, e.playerStates[0].setsWon)

        // Danach ist applyDart ein No-op.
        val noop = e.applyDart(Dart.triple(20))
        assertFalse(noop.accepted)
        assertNull(noop.dartResult)
        assertTrue(noop.matchWon)
        assertTrue(e.isMatchWon)
    }

    @Test
    fun legsToWinZwei_legGewinnRotiertStartspielerDannMatch() {
        // legsToWin=2, setsToWin=1, Start 40 -> jeder Checkout per Double 20.
        val e = engine(start = 40, legsToWin = 2)

        // Leg 1: A beginnt und checkt sofort.
        val r1 = e.applyDart(Dart.double(20))
        assertTrue(r1.legWon)
        assertFalse(r1.setWon)
        assertFalse(r1.matchWon)
        assertEquals(1, e.playerStates[0].legsWonInSet)
        // Neues Leg im selben Set, Startspieler rotiert -> B beginnt.
        assertEquals(2, e.currentLegNumber)
        assertEquals(1, e.currentSetNumber)
        assertEquals(playerB, e.currentPlayerId)
        // Frische LegEngines: beide wieder auf 40.
        assertEquals(X01State(40), e.playerStates[0].state)
        assertEquals(X01State(40), e.playerStates[1].state)

        // Leg 2: B ist dran und wirft harmlos (3x Single 1, kein Check) -> A dran.
        throwHarmlessTurn(e)
        assertEquals(playerA, e.currentPlayerId)

        // A checkt das zweite Leg -> Set (2 Legs) und Match (setsToWin=1).
        // A's Rest ist nach Leg-Reset 40 -> Double 20 checkt.
        val rWin = e.applyDart(Dart.double(20))
        assertTrue(rWin.legWon)
        assertTrue(rWin.setWon)
        assertTrue(rWin.matchWon)
        assertEquals(playerA, e.matchWinnerId)
        assertEquals(1, e.playerStates[0].setsWon)
        // legsWonInSet wird beim Set-Gewinn zurueckgesetzt.
        assertEquals(0, e.playerStates[0].legsWonInSet)
    }

    @Test
    fun setsToWinZwei_zweiSetsGewinnenMatch() {
        // setsToWin=2, legsToWin=1, Start 40.
        val e = engine(start = 40, legsToWin = 1, setsToWin = 2)

        // Set 1: A checkt -> Set 1, kein Match.
        val r1 = e.applyDart(Dart.double(20))
        assertTrue(r1.legWon)
        assertTrue(r1.setWon)
        assertFalse(r1.matchWon)
        assertEquals(1, e.playerStates[0].setsWon)
        assertEquals(2, e.currentSetNumber)
        assertEquals(1, e.currentLegNumber)
        // Startspieler rotiert -> B beginnt Set 2.
        assertEquals(playerB, e.currentPlayerId)

        // B wirft harmlos -> A dran; A checkt Set 2 -> Match.
        throwHarmlessTurn(e)
        assertEquals(playerA, e.currentPlayerId)
        val r2 = e.applyDart(Dart.double(20))
        assertTrue(r2.setWon)
        assertTrue(r2.matchWon)
        assertEquals(playerA, e.matchWinnerId)
        assertEquals(2, e.playerStates[0].setsWon)
    }

    @Test
    fun undoLastDart_innerhalbAufnahmeMoeglich() {
        val e = engine(start = 180)
        e.applyDart(Dart.triple(20)) // 60 -> 120
        e.applyDart(Dart.single(20)) // 20 -> 100
        assertEquals(X01State(100), e.playerStates[0].state)

        assertTrue(e.undoLastDart())
        assertEquals(X01State(120), e.playerStates[0].state)
        assertEquals(playerA, e.currentPlayerId)
    }

    @Test
    fun undoLastDart_istNoOpNachMatchGewinn() {
        val e = engine(start = 40)
        e.applyDart(Dart.double(20))
        assertTrue(e.isMatchWon)
        assertFalse(e.undoLastDart())
    }

    @Test
    fun legSnapshot_traegtThrowDatenDesWerfers() {
        val e = engine(start = 100)
        val r = e.applyDart(Dart.triple(20))
        // Snapshot des Werfers enthaelt den geworfenen Dart fuer die Persistenz.
        assertEquals(1, r.legSnapshot.dartsInTurn)
        assertEquals(listOf(Dart.triple(20)), r.legSnapshot.turnDarts)
        assertEquals(X01State(40), r.legSnapshot.state)
        assertEquals(0, r.dartResult!!.dartIndex)
        assertEquals(60, r.dartResult!!.scored)
    }
}
