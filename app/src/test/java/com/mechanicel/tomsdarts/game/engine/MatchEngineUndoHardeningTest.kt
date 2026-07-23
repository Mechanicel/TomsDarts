package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.X01Mode
import com.mechanicel.tomsdarts.game.X01State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test-Gate-Haertung fuer das Cross-Turn-[MatchEngine.undoLastDart] (Replay ueber
 * Aufnahme-/Spielerwechsel-Grenzen). Ergaenzt [MatchEngineTest] und
 * [MatchEngineEdgeCasesTest] um:
 * - kompletten Rewind bis zum Leg-Anfang ueber mehrere Spielerwechsel, danach
 *   No-op sowie Replay-Determinismus (gleiche Dart-Sequenz -> gleicher Endzustand
 *   wie ohne Undo/Redo-Zyklus),
 * - Undo des allerersten Darts im Leg,
 * - Undo im ZWEITEN Leg nach echter Starter-Rotation (legStartIndex korrekt,
 *   legsWonInSet/setsWon des vorherigen Leg-Gewinners unberuehrt),
 * - drei Spieler ueber mehrere Wechsel inkl. mehrerer Busts in Folge,
 * - Interleaving von applyDart/undoLastDart mit Konsistenzpruefung von
 *   [MatchEngine.dartsThrownInCurrentLeg] nach jedem Schritt.
 *
 * Reines JUnit, kein Robolectric, deterministisch.
 */
class MatchEngineUndoHardeningTest {

    private val playerA = 10L
    private val playerB = 20L
    private val playerC = 30L

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
    private fun throwHarmlessTurn(e: MatchEngine<X01State>) {
        e.applyDart(Dart.single(1))
        e.applyDart(Dart.single(1))
        e.applyDart(Dart.single(1))
    }

    // --- Kompletter Rewind bis zum Leg-Anfang --------------------------------

    @Test
    fun undo_kompletterRewindUeberMehrereSpielerwechselZumLegAnfang_dannNoOp() {
        val e = engine(start = 501)
        // A und B werfen je zwei volle Aufnahmen: 4 Aufnahmen = 12 Darts total.
        throwHarmlessTurn(e) // A
        throwHarmlessTurn(e) // B
        throwHarmlessTurn(e) // A
        throwHarmlessTurn(e) // B
        assertEquals(12, e.dartsThrownInCurrentLeg)
        assertEquals(playerA, e.currentPlayerId)

        // Vollstaendig zurueckspulen: 12x undo muss moeglich sein.
        repeat(12) { i ->
            assertTrue("undo #${i + 1} sollte moeglich sein", e.undoLastDart())
        }

        // Zurueck auf den Leg-Anfang: Startspieler A, frischer Zustand fuer beide.
        assertEquals(0, e.dartsThrownInCurrentLeg)
        assertEquals(playerA, e.currentPlayerId)
        assertEquals(0, e.currentPlayerIndex)
        assertEquals(X01State(501), e.playerStates[0].state)
        assertEquals(X01State(501), e.playerStates[1].state)
        assertEquals(0, e.playerStates[0].legSnapshot.dartsInTurn)

        // Ein weiteres undo ist ein No-op (Leg-Historie leer).
        assertFalse(e.undoLastDart())
        assertEquals(0, e.dartsThrownInCurrentLeg)
        assertEquals(playerA, e.currentPlayerId)
    }

    @Test
    fun undo_replayDeterminismus_gleicheDartSequenzNachRewindEntsprichtOhneUndo() {
        val darts = listOf(
            Dart.triple(20), Dart.single(5), Dart.single(1), // A
            Dart.single(19), Dart.single(19), Dart.single(19), // B
            Dart.triple(19), Dart.single(1), Dart.single(1), // A
        )

        // Referenz: dieselbe Sequenz ohne jeglichen Undo-Aufruf.
        val reference = engine(start = 501)
        darts.forEach { reference.applyDart(it) }
        val referenceSnapshot = reference.snapshot()

        // Testling: komplett zurueckspulen und dieselbe Sequenz erneut werfen.
        val e = engine(start = 501)
        darts.forEach { e.applyDart(it) }
        repeat(darts.size) { e.undoLastDart() }
        assertEquals(0, e.dartsThrownInCurrentLeg)
        darts.forEach { e.applyDart(it) }

        assertEquals(referenceSnapshot, e.snapshot())
    }

    // --- Undo des allerersten Darts im Leg ------------------------------------

    @Test
    fun undo_ersterDartImLeg_stelltAusgangszustandWiederHerUndStartspielerAktiv() {
        val e = engine(start = 501)
        e.applyDart(Dart.triple(20))
        assertEquals(X01State(441), e.playerStates[0].state)
        assertEquals(1, e.dartsThrownInCurrentLeg)

        assertTrue(e.undoLastDart())
        assertEquals(X01State(501), e.playerStates[0].state)
        assertEquals(playerA, e.currentPlayerId)
        assertEquals(0, e.currentPlayerIndex)
        assertEquals(0, e.dartsThrownInCurrentLeg)
        assertEquals(0, e.playerStates[0].legSnapshot.dartsInTurn)

        // Weiteres undo ist ein No-op.
        assertFalse(e.undoLastDart())
    }

    // --- Undo im zweiten Leg nach echter Starter-Rotation ---------------------

    @Test
    fun undo_zweitesLeg_legStartIndexKorrekt_undoNurBisLegAnfang_zaehlerUnberuehrt() {
        // legsToWin=3 -> Leg 1 gewonnen, Leg 2 laeuft weiter (kein Set-Abschluss).
        val e = engine(start = 40, legsToWin = 3)

        // Leg 1: A checkt sofort -> Startspieler rotiert auf B fuer Leg 2.
        e.applyDart(Dart.double(20))
        assertEquals(1, e.playerStates[0].legsWonInSet)
        assertEquals(0, e.playerStates[1].setsWon)
        assertEquals(2, e.currentLegNumber)
        assertEquals(playerB, e.currentPlayerId)
        assertEquals(0, e.dartsThrownInCurrentLeg)

        // Leg 2: B wirft eine volle Aufnahme (Wechsel zu A), A wirft zwei Darts.
        throwHarmlessTurn(e) // B: 40 -> 37
        e.applyDart(Dart.single(5)) // A: 40 -> 35
        assertEquals(playerA, e.currentPlayerId)
        assertEquals(4, e.dartsThrownInCurrentLeg)

        // Undo bis zum Leg-2-Anfang: 4 Darts zurueck.
        repeat(4) { assertTrue(e.undoLastDart()) }
        assertEquals(0, e.dartsThrownInCurrentLeg)
        // legStartIndex von Leg 2 (B) bleibt der aktive Spieler -- Rotation
        // wurde durch das Undo NICHT rueckgaengig gemacht.
        assertEquals(playerB, e.currentPlayerId)
        assertEquals(1, e.currentPlayerIndex)
        assertEquals(X01State(40), e.playerStates[0].state)
        assertEquals(X01State(40), e.playerStates[1].state)

        // Weiteres undo darf NICHT ueber die Leg-Grenze zurueck ins Leg 1.
        assertFalse(e.undoLastDart())

        // Leg-1-Zaehler bleiben durch das Undo in Leg 2 unberuehrt.
        assertEquals(1, e.playerStates[0].legsWonInSet)
        assertEquals(0, e.playerStates[1].legsWonInSet)
        assertEquals(0, e.playerStates[0].setsWon)
        assertEquals(2, e.currentLegNumber)
        assertEquals(1, e.currentSetNumber)
    }

    // --- Drei Spieler, mehrere Wechsel inkl. mehrerer Busts in Folge ----------

    @Test
    fun dreiSpieler_undoUeberMehrereWechselMitMehrfachenBustsInFolge() {
        val e = engine(start = 100, players = listOf(playerA, playerB, playerC))

        // A: T20 (->40), dann T20 ueberwirft -> Bust -> B.
        e.applyDart(Dart.triple(20))
        e.applyDart(Dart.triple(20))
        assertEquals(playerB, e.currentPlayerId)

        // B: T20 (->40), dann ebenfalls Bust -> C.
        e.applyDart(Dart.triple(20))
        e.applyDart(Dart.triple(20))
        assertEquals(playerC, e.currentPlayerId)

        // C: eine harmlose Aufnahme -> zurueck zu A.
        throwHarmlessTurn(e)
        assertEquals(playerA, e.currentPlayerId)

        assertEquals(7, e.dartsThrownInCurrentLeg)

        // Undo bis kurz vor B's Bust-Dart (5 Schritte zurueck: C's 3 Darts + A's
        // zweiter Bust-Dart + gedanklich ein weiterer): schrittweise pruefen.
        assertTrue(e.undoLastDart()) // C's 3. Dart zurueck
        assertTrue(e.undoLastDart()) // C's 2. Dart zurueck
        assertTrue(e.undoLastDart()) // C's 1. Dart zurueck -> C wieder dran
        assertEquals(playerC, e.currentPlayerId)
        assertEquals(4, e.dartsThrownInCurrentLeg)

        assertTrue(e.undoLastDart()) // B's Bust-Dart zurueck -> B wieder dran, offene Aufnahme
        assertEquals(playerB, e.currentPlayerId)
        val bSnap = e.playerStates[1].legSnapshot
        assertEquals(1, bSnap.dartsInTurn)
        assertFalse(bSnap.turnBust)
        assertEquals(X01State(40), e.playerStates[1].state)

        assertTrue(e.undoLastDart()) // B's letzter verbleibender Dart zurueck -> A's Bust-Turn komplett, B wieder frisch dran (Rest 100)
        assertEquals(playerB, e.currentPlayerId)
        assertEquals(X01State(100), e.playerStates[1].state)
        assertEquals(0, e.playerStates[1].legSnapshot.dartsInTurn)

        // Naechstes undo spult ueber die Aufnahme-Grenze zurueck zu A's
        // Bust-Dart: A wieder dran mit offener Aufnahme (Rest 40, kein Bust).
        assertTrue(e.undoLastDart())
        assertEquals(playerA, e.currentPlayerId)
        val aSnap = e.playerStates[0].legSnapshot
        assertEquals(1, aSnap.dartsInTurn)
        assertFalse(aSnap.turnBust)
        assertEquals(X01State(40), e.playerStates[0].state)

        // C wurde durch all das nicht beruehrt (nie geworfen -> 100).
        assertEquals(X01State(100), e.playerStates[2].state)
    }

    // --- Interleaving applyDart/undo: dartsThrownInCurrentLeg-Konsistenz -----

    @Test
    fun interleaving_applyDartUndoApplyDart_dartsThrownInCurrentLegBleibtKonsistent() {
        val e = engine(start = 501)

        e.applyDart(Dart.single(20))
        assertEquals(1, e.dartsThrownInCurrentLeg)

        e.applyDart(Dart.single(19))
        assertEquals(2, e.dartsThrownInCurrentLeg)

        assertTrue(e.undoLastDart())
        assertEquals(1, e.dartsThrownInCurrentLeg)

        e.applyDart(Dart.single(18))
        assertEquals(2, e.dartsThrownInCurrentLeg)

        assertTrue(e.undoLastDart())
        assertTrue(e.undoLastDart())
        assertEquals(0, e.dartsThrownInCurrentLeg)
        assertFalse(e.undoLastDart())
        assertEquals(0, e.dartsThrownInCurrentLeg)

        // Danach normal weiterspielen -- Historie zaehlt wieder korrekt hoch.
        e.applyDart(Dart.single(1))
        e.applyDart(Dart.single(1))
        e.applyDart(Dart.single(1))
        assertEquals(3, e.dartsThrownInCurrentLeg)
        assertEquals(playerB, e.currentPlayerId)

        e.applyDart(Dart.single(1))
        assertEquals(4, e.dartsThrownInCurrentLeg)

        assertTrue(e.undoLastDart())
        assertEquals(3, e.dartsThrownInCurrentLeg)
        assertEquals(playerB, e.currentPlayerId)
    }

    // --- Undo mit sofortigem Checkout (< 3 Darts, Leg-Historie geleert) -------

    @Test
    fun undo_nachSofortCheckoutMitVorherigenAufnahmen_findetNurNochLeereHistorieVor() {
        // legsToWin=2: A wirft eine volle harmlose Aufnahme, B checkt danach
        // sofort mit einem Dart -> Leg-Gewinn raeumt die Historie ab.
        val e = engine(start = 40, legsToWin = 2)
        throwHarmlessTurn(e) // A: 40 -> 37, Wechsel zu B
        assertEquals(playerB, e.currentPlayerId)

        // B checkt NICHT sofort (Rest 40 fuer B, da frische LegEngine je Spieler):
        // B checkt mit Double 20 in einem Dart -> Leg gewonnen.
        val r = e.applyDart(Dart.double(20))
        assertTrue(r.legWon)
        assertEquals(0, e.dartsThrownInCurrentLeg)

        // Kein Undo mehr moeglich: die Historie des abgeschlossenen Legs
        // (inkl. A's Aufnahme) ist verworfen, das neue Leg ist frisch.
        assertFalse(e.undoLastDart())
        // Rotation ist unabhaengig vom Gewinner: Leg 1 startete bei A (Index 0),
        // Leg 2 startet beim NAECHSTEN Index -> B (Index 1).
        assertEquals(playerB, e.currentPlayerId)
        assertEquals(1, e.currentPlayerIndex)
        assertEquals(X01State(40), e.playerStates[0].state)
        assertEquals(X01State(40), e.playerStates[1].state)
    }
}
