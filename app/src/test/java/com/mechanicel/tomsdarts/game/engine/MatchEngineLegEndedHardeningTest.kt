package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.testing.RoundLimitFakeMode
import com.mechanicel.tomsdarts.testing.RoundLimitState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test-Gate-Haertung fuer die Vertragserweiterung "Leg-Ende ohne Werfer-Sieg"
 * (`legEnded`) auf [MatchEngine]-Ebene, ergaenzt [MatchEngineLegEndedTest] um:
 * - Cross-Turn-Undo-Tiefe nach einem `legEnded`-Sieg ueber mehrere Rewind-/
 *   Replay-Zyklen (Rundreise-Invarianz per Snapshot-Vergleich),
 * - `legEnded`-Siege an der Set- bzw. Match-Grenze inkl. Rundreise-invariantem
 *   Undo an genau dieser Grenze (analog [MatchEngineWinUndoHardeningTest], aber
 *   fuer den Rangvergleich-Pfad),
 * - argmax ueber 3+ Spieler (Gewinner in der Mitte der Reihenfolge, Gleichstand
 *   zwischen zwei NICHT-Werfern),
 * - den Degenerat-Fall eines Modus OHNE [com.mechanicel.tomsdarts.game.GameMode.legScore]-
 *   Override (Default `0` fuer alle -> Gleichstand-Konvention entscheidet).
 *
 * Als Modus dient der geteilte [RoundLimitFakeMode] (Test-Fixture). Reines
 * JUnit, kein Robolectric, deterministisch.
 */
class MatchEngineLegEndedHardeningTest {

    private val playerA = 10L
    private val playerB = 20L
    private val playerC = 30L

    private fun roundLimitEngine(
        dartLimit: Int = RoundLimitFakeMode.DEFAULT_DART_LIMIT,
        legsToWin: Int = 1,
        setsToWin: Int = 1,
        reportLegScore: Boolean = true,
        players: List<Long> = listOf(playerA, playerB),
    ): MatchEngine<RoundLimitState> = MatchEngine(
        mode = RoundLimitFakeMode(dartLimit = dartLimit, reportLegScore = reportLegScore),
        config = GameConfig(legsToWin = legsToWin, setsToWin = setsToWin),
        playerIds = players,
    )

    /** Wirft [count] Darts desselben Segments fuer den gerade aktiven Spieler. */
    private fun MatchEngine<RoundLimitState>.throwSingles(segment: Int, count: Int) {
        repeat(count) { applyDart(Dart.single(segment)) }
    }

    // --- legEnded x Undo-Tiefe: Cross-Turn, mehrere Zyklen ---------------------

    @Test
    fun legEnded_undoTiefe_mehrfacherRewindReplayZyklusIstSnapshotInvariant() {
        // legsToWin hoch genug, damit keine Runde einen Set abschliesst.
        val e = roundLimitEngine(legsToWin = 5)
        val darts = listOf(
            Dart.single(20), Dart.single(20), Dart.single(20), // A: volle Aufnahme, 60 Punkte
            Dart.single(1), Dart.single(1), // B: 2. Dart beendet das Leg (2 Punkte)
        )

        darts.forEach { e.applyDart(it) }
        val referenceSnapshot = e.snapshot()
        // Rotation nach dem Leg-Ende: Leg startete bei A (Index 0) -> naechster
        // Leg-Start ist B (Index 1), unabhaengig davon, dass A gewonnen hat.
        assertEquals(playerB, e.currentPlayerId)
        assertEquals(1, e.playerStates[0].legsWonInSet)

        repeat(3) { cycle ->
            // Vollstaendiger Cross-Turn-Rewind bis zum Leg-Anfang.
            repeat(darts.size) { i ->
                assertTrue(
                    "Zyklus ${cycle + 1}, Undo #${i + 1} sollte moeglich sein",
                    e.undoLastDart(),
                )
            }
            assertEquals(0, e.dartsThrownInCurrentLeg)
            assertFalse("Weiteres Undo ist ein No-op", e.undoLastDart())
            assertEquals(playerA, e.currentPlayerId)
            assertEquals(RoundLimitState(), e.playerStates[0].state)
            assertEquals(RoundLimitState(), e.playerStates[1].state)
            assertEquals(0, e.playerStates[0].legsWonInSet)

            // Dieselbe Sequenz erneut werfen -> dasselbe legEnded-Ergebnis.
            val results = darts.map { e.applyDart(it) }
            assertEquals(
                "Zyklus ${cycle + 1}: derselbe Rang-Sieger",
                playerA,
                results.last().legWinnerId,
            )
            assertEquals(
                "Zyklus ${cycle + 1}: Snapshot weicht von der Referenz ab",
                referenceSnapshot,
                e.snapshot(),
            )
            assertEquals(1, e.playerStates[0].legsWonInSet)
        }
    }

    @Test
    fun legEnded_undo_einzelschritteUeberSpielerwechselGrenzeZurueckStellenZwischenstaendeWiederHer() {
        val e = roundLimitEngine(legsToWin = 3)
        e.throwSingles(20, 3) // A: volle Aufnahme, 60 Punkte
        e.applyDart(Dart.single(1)) // B: 1. Dart, 1 Punkt
        val end = e.applyDart(Dart.single(1)) // B: 2. Dart -> legEnded, A gewinnt
        assertEquals(playerA, end.legWinnerId)

        // Schritt 1: den legEnded-Dart zurueck -> B wieder dran, Aufnahme mit
        // ihrem ersten Dart noch offen (nicht "gewonnen").
        assertTrue(e.undoLastDart())
        assertEquals(playerB, e.currentPlayerId)
        assertEquals(RoundLimitState(darts = 1, points = 1), e.playerStates[1].state)
        assertEquals(1, e.playerStates[1].legSnapshot.dartsInTurn)
        assertFalse(e.playerStates[1].legSnapshot.isLegWon)
        assertEquals(0, e.playerStates[0].legsWonInSet)

        // Schritt 2: B's einzigen Dart zurueck -> B mit leerer, weiterhin
        // offener Aufnahme (kein Spielerwechsel, da B noch nicht am Zug war).
        assertTrue(e.undoLastDart())
        assertEquals(playerB, e.currentPlayerId)
        assertEquals(RoundLimitState(), e.playerStates[1].state)
        assertEquals(0, e.playerStates[1].legSnapshot.dartsInTurn)

        // Schritt 3: ueber die Spielerwechsel-Grenze zurueck zu A's letztem
        // Dart der vollen Aufnahme.
        assertTrue(e.undoLastDart())
        assertEquals(playerA, e.currentPlayerId)
        assertEquals(2, e.playerStates[0].legSnapshot.dartsInTurn)
        assertEquals(RoundLimitState(darts = 2, points = 40), e.playerStates[0].state)

        // Wieder vorspielen bis zum Leg-Ende -- erneut gewinnt A.
        e.applyDart(Dart.single(20))
        e.applyDart(Dart.single(1))
        val endAgain = e.applyDart(Dart.single(1))
        assertEquals(playerA, endAgain.legWinnerId)
        assertEquals(1, e.playerStates[0].legsWonInSet)
    }

    // --- legEnded x Set-/Match-Grenzen ------------------------------------------

    @Test
    fun legEnded_setGrenze_ueberZweiLegsMitWechselndemWerferRundreiseInvariantBeimUndo() {
        // legsToWin=2, setsToWin=2: zwei legEnded-Siege desselben Rang-Siegers
        // (A) schliessen den ersten Set ab, aber noch nicht das Match. Der
        // WERFER des jeweils leg-beendenden Darts wechselt zwischen den Legs.
        val e = roundLimitEngine(legsToWin = 2, setsToWin = 2)

        // Leg 1 (A startet): A volle Aufnahme (60), B beendet mit 2 Punkten.
        e.throwSingles(20, 3)
        e.applyDart(Dart.single(1))
        val leg1 = e.applyDart(Dart.single(1))
        assertEquals("Rang-Sieger A, nicht Werfer B", playerA, leg1.legWinnerId)
        assertEquals(playerB, leg1.playerId)
        assertFalse(leg1.setWon)
        assertEquals(1, e.playerStates[0].legsWonInSet)
        assertTrue(e.commitLegTransition())
        assertEquals(playerB, e.currentPlayerId) // Rotation: Leg 2 startet bei B.

        // Leg 2 (B startet, niedrige Werte): B volle Aufnahme (3 Punkte).
        e.throwSingles(1, 3)
        // A beendet mit hohen Werten (40 Punkte) -> A fuehrt UND wirft den
        // leg-beendenden Dart (thrower == winner in DIESEM Leg).
        e.applyDart(Dart.single(20))
        val beforeSetWin = e.snapshot()
        val leg2 = e.applyDart(Dart.single(20))

        assertEquals(playerA, leg2.legWinnerId)
        assertTrue("Zweiter Leg-Sieg schliesst den Set ab", leg2.setWon)
        assertFalse(leg2.matchWon)
        assertEquals(1, e.playerStates[0].setsWon)
        assertEquals(0, e.playerStates[0].legsWonInSet)
        assertEquals(2, e.currentSetNumber)
        assertEquals(1, e.currentLegNumber)

        // Undo: exakte Rundreise auf den Stand direkt vor dem Set-Sieg.
        assertTrue(e.undoLastDart())
        assertEquals(beforeSetWin, e.snapshot())
        assertEquals(0, e.playerStates[0].setsWon)
        assertEquals(1, e.playerStates[0].legsWonInSet)
        assertEquals(1, e.currentSetNumber)
        assertEquals(2, e.currentLegNumber)

        // Erneuter Sieg fuehrt zum selben Set-Abschluss.
        val leg2Again = e.applyDart(Dart.single(20))
        assertTrue(leg2Again.setWon)
        assertFalse(leg2Again.matchWon)
        assertEquals(1, e.playerStates[0].setsWon)
        assertEquals(0, e.playerStates[0].legsWonInSet)
    }

    @Test
    fun legEnded_matchGrenze_ueberZweiSetsRundreiseInvariantBeimUndo() {
        // legsToWin=1, setsToWin=2: jeder legEnded-Sieg schliesst sofort einen
        // Set ab; der zweite Set-Sieg fuer A entscheidet das Match.
        val e = roundLimitEngine(legsToWin = 1, setsToWin = 2)

        // Set 1 (A startet): A volle Aufnahme (60), B beendet mit 2 Punkten -> Set 1 fuer A.
        e.throwSingles(20, 3)
        e.applyDart(Dart.single(1))
        val set1 = e.applyDart(Dart.single(1))
        assertEquals(playerA, set1.legWinnerId)
        assertTrue(set1.setWon)
        assertFalse(set1.matchWon)
        assertEquals(1, e.playerStates[0].setsWon)
        assertEquals(2, e.currentSetNumber)
        assertTrue(e.commitLegTransition())
        assertEquals(playerB, e.currentPlayerId) // Rotation ueber die Set-Grenze hinweg.

        // Set 2 (B startet, niedrige Werte): B volle Aufnahme (3 Punkte).
        e.throwSingles(1, 3)
        // A beendet mit hohen Werten (40 Punkte) -> A gewinnt Set 2 -> Match.
        e.applyDart(Dart.single(20))
        val beforeMatchWin = e.snapshot()
        val matchEnd = e.applyDart(Dart.single(20))

        assertEquals(playerA, matchEnd.legWinnerId)
        assertTrue(matchEnd.setWon)
        assertTrue(matchEnd.matchWon)
        assertEquals(playerA, matchEnd.matchWinnerId)
        assertTrue(e.isMatchWon)
        assertEquals(playerA, e.matchWinnerId)
        assertEquals(2, e.playerStates[0].setsWon)

        // Undo: Match wieder offen, exakte Rundreise auf den Stand davor.
        assertTrue(e.undoLastDart())
        assertFalse(e.isMatchWon)
        assertNull(e.matchWinnerId)
        assertEquals(beforeMatchWin, e.snapshot())
        assertEquals(1, e.playerStates[0].setsWon)
        assertEquals(2, e.currentSetNumber)

        // Die Engine nimmt wieder Darts an; derselbe Dart entscheidet erneut das Match.
        val matchEndAgain = e.applyDart(Dart.single(20))
        assertTrue(matchEndAgain.matchWon)
        assertEquals(playerA, matchEndAgain.matchWinnerId)
        assertEquals(2, e.playerStates[0].setsWon)
    }

    // --- legEnded x 3+ Spieler: argmax und Gleichstand --------------------------

    @Test
    fun dreiSpieler_argmaxErmitteltGewinnerInDerMitteDerReihenfolge() {
        // dartLimit=1: A und B werfen je eine volle (nicht leg-beendende)
        // Aufnahme, C beendet das Leg mit ihrem ERSTEN Dart. Der Rang-Sieger B
        // steht in der MITTE der Spieler-Reihenfolge und ist NICHT der Werfer.
        val e = roundLimitEngine(dartLimit = 1, legsToWin = 3, players = listOf(playerA, playerB, playerC))

        e.throwSingles(5, 3) // A: 15 Punkte
        e.throwSingles(20, 3) // B: 60 Punkte -> fuehrt
        val end = e.applyDart(Dart.single(1)) // C: 1 Punkt, beendet das Leg

        assertTrue("Leg-Ende ohne Werfer-Sieg", end.dartResult?.legEnded == true)
        assertFalse("Kein klassischer Werfer-Sieg", end.legWon)
        assertEquals("Werfer ist C", playerC, end.playerId)
        assertEquals("Gewinner ist B (Mitte der Reihenfolge)", playerB, end.legWinnerId)
        assertEquals(0, e.playerStates[0].legsWonInSet)
        assertEquals(1, e.playerStates[1].legsWonInSet)
        assertEquals(0, e.playerStates[2].legsWonInSet)
    }

    @Test
    fun dreiSpieler_gleichstandZwischenZweiNichtWerfern_kleinsterIndexGewinnt() {
        // A und B erzielen EXAKT denselben Punktestand (echter Gleichstand),
        // C wirft den leg-beendenden Dart mit weniger Punkten. Die Konvention
        // "kleinster Index gewinnt" muss zwischen A und B entscheiden, obwohl
        // BEIDE nicht der Werfer sind.
        val e = roundLimitEngine(dartLimit = 1, legsToWin = 3, players = listOf(playerA, playerB, playerC))

        e.throwSingles(20, 3) // A: 60 Punkte
        e.throwSingles(20, 3) // B: 60 Punkte -> exakter Gleichstand mit A
        val end = e.applyDart(Dart.single(1)) // C: 1 Punkt, beendet das Leg

        assertEquals(playerC, end.playerId)
        assertEquals("Gleichstand A/B -> kleinerer Index (A) gewinnt", playerA, end.legWinnerId)
        assertEquals(1, e.playerStates[0].legsWonInSet)
        assertEquals(0, e.playerStates[1].legsWonInSet)
        assertEquals(0, e.playerStates[2].legsWonInSet)
    }

    // --- legScore-Default: Modus ohne Override -----------------------------------

    @Test
    fun legScoreDefault_ohneOverride_alleSpielerGleich0_kleinsterIndexGewinntTrotzMehrPunkten() {
        // reportLegScore=false simuliert einen Modus OHNE eigenen legScore-
        // Override: der Interface-Default 0 gilt fuer ALLE Spieler, unabhaengig
        // vom tatsaechlichen Punktestand. B wirft mehr Punkte (40) als A (3) UND
        // den leg-beendenden Dart - dennoch gewinnt A (kleinster Index bei
        // Gleichstand 0==0), weil der Rangwert komplett degeneriert ist.
        val e = roundLimitEngine(legsToWin = 1, setsToWin = 1, reportLegScore = false)

        e.throwSingles(1, 3) // A: 3 "echte" Punkte, aber legScore() liefert 0
        e.applyDart(Dart.single(20)) // B: 1. Dart, 20 "echte" Punkte
        val end = e.applyDart(Dart.single(20)) // B: 2. Dart -> legEnded, 40 "echte" Punkte

        assertEquals(playerB, end.playerId)
        assertEquals("Degenerat-Fall: Gleichstand 0==0 -> kleinster Index", playerA, end.legWinnerId)
        assertTrue(e.isMatchWon)
        assertEquals(playerA, e.matchWinnerId)
        // legsToWin=1 -> der Leg-Sieg schliesst sofort Set UND Match ab; die
        // Set-Buchfuehrung setzt legsWonInSet dabei aller Spieler zurueck (0),
        // waehrend setsWon den tatsaechlichen Sieg traegt.
        assertEquals(1, e.playerStates[0].setsWon)
        assertEquals(0, e.playerStates[0].legsWonInSet)
    }
}
