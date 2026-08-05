package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.AroundTheClockMode
import com.mechanicel.tomsdarts.game.AroundTheClockState
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
 * Test-Gate-Haertung fuer das Zuruecknehmen eines Leg-/Match-Sieg-Darts
 * ([MatchEngine.undoLastDart] bei aufgeschobenem [MatchEngine.commitLegTransition]).
 * Ergaenzt [MatchEngineTest], [MatchEngineEdgeCasesTest] und
 * [MatchEngineUndoHardeningTest] um:
 * - mehrfache Sieg-Undo-Zyklen mit voller Snapshot-Gleichheit vor dem Sieg und
 *   nach dem Undo (Rundreise-Invariante) ueber mehrere Runden,
 * - die Set-Grenze (ein Leg-Sieg, der zugleich einen Set abschliesst) und die
 *   Match-Grenze (voller Set-/Leg-Pfad) mit derselben Rundreise-Invariante,
 * - den Lazy-Commit-Pfad (naechster [MatchEngine.applyDart] OHNE expliziten
 *   [MatchEngine.commitLegTransition]-Aufruf): keine doppelte Zaehlung und der
 *   vorige Sieg ist danach endgueltig (nicht mehr per Undo ruecknehmbar),
 * - einen tieferen Undo als der Sieg-Dart, der bis VOR den Aufnahme-Start des
 *   Sieg-Legs zurueckspult und dadurch einen ANDEREN Spieler gewinnen laesst
 *   (Rotation bleibt unabhaengig vom tatsaechlichen Gewinner korrekt),
 * - einen Smoke-Test mit einem NICHT-X01-Modus ([AroundTheClockMode]), damit
 *   die Sieg-Undo-Faehigkeit nicht nur fuer X01 belegt ist.
 *
 * Reines JUnit, kein Robolectric, deterministisch.
 */
class MatchEngineWinUndoHardeningTest {

    private val playerA = 10L
    private val playerB = 20L

    private fun x01Engine(
        start: Int = 40,
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

    /** Wirft eine volle, nicht checkende Aufnahme (3x Single 1) des aktiven Spielers. */
    private fun throwHarmlessTurn(e: MatchEngine<X01State>) {
        e.applyDart(Dart.single(1))
        e.applyDart(Dart.single(1))
        e.applyDart(Dart.single(1))
    }

    // --- Mehrfache Sieg-Undo-Zyklen: Rundreise-Invariante ----------------------

    @Test
    fun mehrfacherSiegUndoZyklus_snapshotVorSiegEntsprichtSnapshotNachUndo_ueberDreiRunden() {
        // legsToWin hoch genug, damit keine Runde einen Set abschliesst - jede
        // Runde startet mit frischem Rest 40 fuer den jeweils aktuellen Spieler.
        val e = x01Engine(start = 40, legsToWin = 5)

        repeat(3) { round ->
            val before = e.snapshot()

            val win = e.applyDart(Dart.double(20))
            assertTrue("Runde ${round + 1}: Sieg-Dart sollte das Leg gewinnen", win.legWon)
            assertTrue(e.undoLastDart())

            // Rundreise-Invariante: nach dem Undo ist der VOLLE Snapshot wieder
            // exakt der Stand von vor dem Sieg-Dart (keine Zaehler-Drift).
            assertEquals("Runde ${round + 1}: Snapshot nach Undo weicht ab", before, e.snapshot())

            // Erneuter Sieg und diesmal Vollzug -> naechste Runde startet frisch.
            val winAgain = e.applyDart(Dart.double(20))
            assertTrue(winAgain.legWon)
            assertTrue(e.commitLegTransition())
        }
    }

    // --- Set-Grenze: Leg-Sieg, der zugleich den Set abschliesst ----------------

    @Test
    fun siegUndoAnSetGrenze_setZaehlerUndNummernRundreiseInvariant() {
        // legsToWin=2, setsToWin=2: das ZWEITE Leg eines Sets schliesst den Set ab,
        // aber noch nicht das Match.
        val e = x01Engine(start = 40, legsToWin = 2, setsToWin = 2)

        // Leg 1 (A) gewinnen und vollziehen.
        e.applyDart(Dart.double(20))
        assertTrue(e.commitLegTransition())
        assertEquals(1, e.playerStates[0].legsWonInSet)
        assertEquals(playerB, e.currentPlayerId)

        // Leg 2: B wirft harmlos (kein Sieg) -> A dran und gewinnt -> Set 1 komplett.
        throwHarmlessTurn(e)
        assertEquals(playerA, e.currentPlayerId)

        val beforeSetWin = e.snapshot()
        val setWin = e.applyDart(Dart.double(20))
        assertTrue(setWin.legWon)
        assertTrue(setWin.setWon)
        assertFalse(setWin.matchWon)
        assertEquals(1, e.playerStates[0].setsWon)
        assertEquals(0, e.playerStates[0].legsWonInSet)
        assertEquals(2, e.currentSetNumber)
        assertEquals(1, e.currentLegNumber)

        // Undo: exakt der Stand von vor dem Set-Sieg (inkl. B's harmloser Aufnahme
        // im laufenden Leg, die NICHT verloren gehen darf).
        assertTrue(e.undoLastDart())
        assertEquals(beforeSetWin, e.snapshot())
        assertEquals(0, e.playerStates[0].setsWon)
        assertEquals(1, e.playerStates[0].legsWonInSet)
        assertEquals(1, e.currentSetNumber)
        assertEquals(2, e.currentLegNumber)

        // Erneuter Sieg: der Set ist wieder komplett, derselbe Endstand wie zuvor.
        val setWinAgain = e.applyDart(Dart.double(20))
        assertTrue(setWinAgain.setWon)
        assertFalse(setWinAgain.matchWon)
        assertEquals(1, e.playerStates[0].setsWon)
        assertEquals(0, e.playerStates[0].legsWonInSet)
    }

    // --- Match-Grenze: voller Set-/Leg-Pfad -------------------------------------

    @Test
    fun siegUndoAnMatchGrenze_matchWiederOffenUndRundreiseInvariant() {
        val e = x01Engine(start = 40, legsToWin = 2, setsToWin = 2)

        // Set 1 komplett (2 Legs fuer A).
        e.applyDart(Dart.double(20))
        assertTrue(e.commitLegTransition())
        throwHarmlessTurn(e) // B harmlos
        e.applyDart(Dart.double(20))
        assertTrue(e.commitLegTransition())
        assertEquals(1, e.playerStates[0].setsWon)

        // Set 2, Leg 1 fuer A.
        e.applyDart(Dart.double(20))
        assertTrue(e.commitLegTransition())
        throwHarmlessTurn(e) // B harmlos

        // Set 2, Leg 2: Match-Sieg fuer A.
        val beforeMatchWin = e.snapshot()
        val matchWin = e.applyDart(Dart.double(20))
        assertTrue(matchWin.legWon)
        assertTrue(matchWin.setWon)
        assertTrue(matchWin.matchWon)
        assertTrue(e.isMatchWon)
        assertEquals(playerA, e.matchWinnerId)
        assertEquals(2, e.playerStates[0].setsWon)

        // Undo des Match-Siegs: exakt der Stand von davor, Match wieder offen.
        assertTrue(e.undoLastDart())
        assertFalse(e.isMatchWon)
        assertNull(e.matchWinnerId)
        assertEquals(beforeMatchWin, e.snapshot())
        assertEquals(1, e.playerStates[0].setsWon)

        // Die Engine nimmt wieder Darts an (kein No-op mehr).
        val filler = e.applyDart(Dart.single(1))
        assertTrue(filler.accepted)

        // Undo des Fuellwurfs, dann erneuter Match-Sieg -> konsistent zum ersten Mal.
        assertTrue(e.undoLastDart())
        val matchWinAgain = e.applyDart(Dart.double(20))
        assertTrue(matchWinAgain.matchWon)
        assertEquals(playerA, e.matchWinnerId)
        assertEquals(2, e.playerStates[0].setsWon)
    }

    // --- Lazy-Commit: naechster applyDart OHNE expliziten Commit ---------------

    @Test
    fun applyDart_lazyCommitNachLegGewinn_holtWechselNachOhneDoppeltZaehlenUndVorherigerSiegWirdEndgueltig() {
        val e = x01Engine(start = 40, legsToWin = 3)

        val win = e.applyDart(Dart.double(20)) // A gewinnt Leg 1 sofort.
        assertTrue(win.legWon)
        assertEquals(1, e.playerStates[0].legsWonInSet)
        assertEquals(2, e.currentLegNumber)
        assertEquals(playerB, e.currentPlayerId)

        // KEIN expliziter commitLegTransition-Aufruf: applyDart holt den Wechsel
        // selbst nach, bevor der neue Dart verarbeitet wird.
        val next = e.applyDart(Dart.single(1))
        assertTrue(next.accepted)
        assertFalse(next.legWon)
        // Zaehler nicht doppelt fortgeschrieben durch den Lazy-Commit.
        assertEquals(1, e.playerStates[0].legsWonInSet)
        assertEquals(2, e.currentLegNumber)
        // B's Rest im NEUEN Leg (nicht mehr der alte Sieg-Snapshot).
        assertEquals(X01State(39), e.playerStates[1].state)
        // Neue Leg-Historie enthaelt nur den einen neuen Dart.
        assertEquals(1, e.dartsThrownInCurrentLeg)

        // Der Sieg von Leg 1 ist jetzt endgueltig: Undo nimmt NUR B's neuen Dart
        // zurueck, NICHT den Leg-1-Sieg.
        assertTrue(e.undoLastDart())
        assertEquals(0, e.dartsThrownInCurrentLeg)
        assertEquals(X01State(40), e.playerStates[1].state)
        assertEquals(1, e.playerStates[0].legsWonInSet)
        assertEquals(2, e.currentLegNumber)
        assertFalse(e.undoLastDart())
    }

    // --- Tieferer Undo als der Sieg-Dart: anderer Spieler gewinnt --------------

    @Test
    fun siegUndo_tieferAlsSiegDart_andererSpielerGewinntNachCrossTurnUndo() {
        // legsToWin=2, Start 40: A wirft harmlos, B checkt sofort aus -> B gewinnt Leg 1.
        val e = x01Engine(start = 40, legsToWin = 2)
        throwHarmlessTurn(e) // A: 40 -> 37, Wechsel zu B.
        assertEquals(playerB, e.currentPlayerId)

        val win = e.applyDart(Dart.double(20)) // B checkt (eigene, frische LegEngine: Rest 40).
        assertTrue(win.legWon)
        assertEquals(playerB, win.playerId)
        assertEquals(1, e.playerStates[1].legsWonInSet)
        assertEquals(4, e.dartsThrownInCurrentLeg)

        // Sieg-Dart zurueck: B wieder dran, leere Aufnahme, Zaehler zurueck.
        assertTrue(e.undoLastDart())
        assertEquals(0, e.playerStates[1].legsWonInSet)
        assertEquals(playerB, e.currentPlayerId)
        assertEquals(3, e.dartsThrownInCurrentLeg)

        // Weiter zurueck (Cross-Turn) zu A's drittem Dart.
        assertTrue(e.undoLastDart())
        assertEquals(playerA, e.currentPlayerId)
        assertEquals(2, e.playerStates[0].legSnapshot.dartsInTurn)
        assertEquals(X01State(38), e.playerStates[0].state)

        // A checkt STATTDESSEN direkt aus (Rest 38 -> Double 19), statt den
        // dritten harmlosen Dart zu wiederholen: A gewinnt anstelle von B.
        val aWins = e.applyDart(Dart.double(19))
        assertTrue(aWins.legWon)
        assertEquals(playerA, aWins.playerId)
        assertEquals(1, e.playerStates[0].legsWonInSet)
        // B's zuvor zurueckgenommener Sieg darf NICHT nachwirken.
        assertEquals(0, e.playerStates[1].legsWonInSet)
        assertEquals(2, e.currentLegNumber)
        // Rotation ist unabhaengig vom tatsaechlichen Gewinner: Leg 1 startete bei
        // A (Index 0), Leg 2 startet beim NAECHSTEN Index -> B (Index 1).
        assertEquals(playerB, e.currentPlayerId)

        assertTrue(e.commitLegTransition())
        assertFalse(e.undoLastDart())
    }

    // --- Nicht-X01-Modus-Smoke: AroundTheClockMode ------------------------------

    private fun targetOf(e: MatchEngine<AroundTheClockState>, playerId: Long): Int =
        e.playerStates.first { it.playerId == playerId }.state.target

    @Test
    fun aroundTheClock_siegUndoStelltZielWiederHerUndBleibtSpielbar() {
        val e = MatchEngine(
            mode = AroundTheClockMode(),
            config = GameConfig(legsToWin = 1, setsToWin = 1),
            playerIds = listOf(playerA, playerB),
        )

        // A rueckt Ziel fuer Ziel bis 20 vor (B fuellt seine Aufnahmen mit Missen,
        // die fuer Around the Clock No-ops sind - kein Fortschritt bei B).
        var guard = 0
        while (targetOf(e, playerA) < AroundTheClockState.TOTAL && guard < 200) {
            if (e.currentPlayerId == playerA) {
                e.applyDart(Dart.single(targetOf(e, playerA)))
            } else {
                e.applyDart(Dart.miss())
            }
            guard++
        }
        assertEquals(AroundTheClockState.TOTAL, targetOf(e, playerA))
        // B nie fortgeschritten (nur No-op-Missen geworfen) -> Ziel noch beim Start.
        assertEquals(AroundTheClockState.FIRST_TARGET, targetOf(e, playerB))

        // Sicherstellen, dass A am Zug ist, um den Sieg-Dart selbst zu werfen.
        guard = 0
        while (e.currentPlayerId != playerA && guard < 10) {
            e.applyDart(Dart.miss())
            guard++
        }
        assertEquals(playerA, e.currentPlayerId)

        val win = e.applyDart(Dart.single(AroundTheClockState.TOTAL))
        assertTrue(win.legWon)
        assertTrue(win.matchWon)
        assertEquals(AroundTheClockState.TOTAL + 1, targetOf(e, playerA))

        // Sieg-Undo: Ziel wieder auf 20, Match wieder offen, weiterspielbar.
        assertTrue(e.undoLastDart())
        assertFalse(e.isMatchWon)
        assertNull(e.matchWinnerId)
        assertEquals(AroundTheClockState.TOTAL, targetOf(e, playerA))
        assertEquals(playerA, e.currentPlayerId)

        val winAgain = e.applyDart(Dart.single(AroundTheClockState.TOTAL))
        assertTrue(winAgain.legWon)
        assertTrue(winAgain.matchWon)
        assertEquals(AroundTheClockState.TOTAL + 1, targetOf(e, playerA))
    }
}
