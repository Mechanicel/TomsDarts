package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.X01Mode
import com.mechanicel.tomsdarts.game.X01State
import com.mechanicel.tomsdarts.testing.RoundLimitFakeMode
import com.mechanicel.tomsdarts.testing.RoundLimitState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Basistests fuer die Vertragserweiterung "Leg-Ende ohne Werfer-Sieg" auf
 * [MatchEngine]-Ebene: Ein Modus meldet `legEnded` (statt `legWon`), die Engine
 * ermittelt den Gewinner per Rangvergleich ueber
 * [com.mechanicel.tomsdarts.game.GameMode.legScore] und fuehrt danach exakt
 * dieselbe Leg-/Set-/Match-Buchfuehrung wie beim klassischen Werfer-Sieg.
 *
 * Als Modus dient der geteilte [RoundLimitFakeMode] (Test-Fixture, kein
 * Produktions-Modus) mit `dartLimit = 2`: Spieler A wirft eine volle Aufnahme
 * (3 Darts, das Kontingent bremst ihn nicht, weil B noch nicht geworfen hat),
 * danach beendet B mit seinem ZWEITEN Dart das Leg. Dadurch ist der Werfer des
 * leg-beendenden Darts (B) systematisch ein anderer als der moegliche Gewinner
 * (A) - genau der neue Fall.
 *
 * Reines JUnit, kein Robolectric, deterministisch.
 */
class MatchEngineLegEndedTest {

    private val playerA = 10L
    private val playerB = 20L

    private fun roundLimitEngine(
        dartLimit: Int = 2,
        legsToWin: Int = 1,
        setsToWin: Int = 1,
        players: List<Long> = listOf(playerA, playerB),
    ): MatchEngine<RoundLimitState> = MatchEngine(
        mode = RoundLimitFakeMode(dartLimit = dartLimit),
        config = GameConfig(legsToWin = legsToWin, setsToWin = setsToWin),
        playerIds = players,
    )

    /** Wirft [count] Darts desselben Segments fuer den gerade aktiven Spieler. */
    private fun MatchEngine<RoundLimitState>.throwSingles(segment: Int, count: Int) {
        repeat(count) { applyDart(Dart.single(segment)) }
    }

    // --- Leg-Ende und Gewinner-Ermittlung -------------------------------------

    @Test
    fun legEndedDart_beendetLegSofortUndMeldetDenGewinnerPerLegWinnerId() {
        val e = roundLimitEngine()

        // A: volle Aufnahme mit 3x Single 20 (60 Punkte), danach Wechsel zu B.
        e.throwSingles(20, 3)
        assertEquals(playerB, e.currentPlayerId)

        // B: erster Dart laeuft regulaer weiter (Kontingent noch nicht voll).
        val open = e.applyDart(Dart.single(1))
        assertNull("Leg laeuft noch -> kein Gewinner", open.legWinnerId)
        assertFalse(open.turnEnded)

        // B's ZWEITER Dart beendet das Leg - mitten in der Aufnahme (< 3 Darts).
        val end = e.applyDart(Dart.single(1))
        assertTrue("Aufnahme endet sofort mit dem Leg", end.turnEnded)
        assertFalse("Kein Werfer-Sieg", end.legWon)
        assertEquals("Geworfen hat B", playerB, end.playerId)
        assertEquals("Gewonnen hat A (mehr Punkte)", playerA, end.legWinnerId)
        assertEquals(2, end.legSnapshot.dartsInTurn)
    }

    @Test
    fun legEnded_gewinnerIstArgmaxLegScore_auchWennNichtDerWerferFuehrt() {
        val e = roundLimitEngine(legsToWin = 3)

        e.throwSingles(20, 3) // A: 60 Punkte
        e.applyDart(Dart.single(1)) // B: 1
        val end = e.applyDart(Dart.single(1)) // B: 2 -> Leg entschieden

        // Der Leg-Gewinn wird dem RANG-Sieger A gutgeschrieben, nicht dem Werfer B.
        assertEquals(playerA, end.legWinnerId)
        assertEquals(1, e.playerStates[0].legsWonInSet)
        assertEquals(0, e.playerStates[1].legsWonInSet)
        assertFalse(e.isMatchWon)
    }

    @Test
    fun legEnded_werferGewinntWennErDenHoechstenRangwertHat() {
        // Gegenprobe: fuehrt der Werfer, gewinnt auch er - dieselbe Mechanik.
        val e = roundLimitEngine(legsToWin = 3)

        e.throwSingles(1, 3) // A: 3 Punkte
        e.applyDart(Dart.single(20))
        val end = e.applyDart(Dart.single(20)) // B: 40 Punkte -> Leg entschieden

        assertEquals(playerB, end.playerId)
        assertEquals(playerB, end.legWinnerId)
        assertEquals(1, e.playerStates[1].legsWonInSet)
        assertEquals(0, e.playerStates[0].legsWonInSet)
    }

    @Test
    fun legEnded_beiGleichstandGewinntDerZuerstGelisteteSpieler() {
        // Dokumentierte Konvention: exakter Gleichstand -> kleinster Index.
        // Modi, die das nicht wollen, melden bei Gleichstand kein legEnded.
        val e = roundLimitEngine(legsToWin = 3)

        e.throwSingles(5, 3) // A: 15 Punkte in 3 Darts
        e.applyDart(Dart.single(10))
        val end = e.applyDart(Dart.single(5)) // B: 15 Punkte in 2 Darts -> Gleichstand

        assertEquals(
            15,
            e.playerStates[0].state.points,
        )
        assertEquals(
            15,
            e.playerStates[1].state.points,
        )
        assertEquals("Gleichstand -> erster Index gewinnt", playerA, end.legWinnerId)
        assertEquals(1, e.playerStates[0].legsWonInSet)
    }

    // --- Fortschreibung: Legs, Sets, Match, Rotation ---------------------------

    @Test
    fun legEnded_setUndMatchFortschreibungFolgenDemRangSieger() {
        // legsToWin = 1, setsToWin = 1 -> das erste Leg entscheidet alles.
        val e = roundLimitEngine(legsToWin = 1, setsToWin = 1)

        e.throwSingles(20, 3) // A fuehrt mit 60
        e.applyDart(Dart.single(1))
        val end = e.applyDart(Dart.single(1)) // B beendet das Leg

        assertTrue(end.setWon)
        assertTrue(end.matchWon)
        assertEquals(playerA, end.legWinnerId)
        assertEquals("Match-Gewinner ist der Rang-Sieger", playerA, end.matchWinnerId)
        assertTrue(e.isMatchWon)
        assertEquals(playerA, e.matchWinnerId)
        assertEquals(1, e.playerStates[0].setsWon)
        assertEquals(0, e.playerStates[1].setsWon)

        // Match entschieden -> weitere Darts sind No-ops.
        val after = e.applyDart(Dart.single(20))
        assertFalse(after.accepted)
    }

    @Test
    fun legEnded_naechstesLegRotiertDenStartspielerUndSetztZustaendeZurueck() {
        val e = roundLimitEngine(legsToWin = 3)

        e.throwSingles(20, 3)
        e.applyDart(Dart.single(1))
        e.applyDart(Dart.single(1)) // Leg 1 entschieden (A gewinnt)

        assertEquals(2, e.currentLegNumber)
        assertEquals(1, e.currentSetNumber)
        // Rotation ist gewinner-agnostisch: Leg 1 startete bei A -> Leg 2 bei B.
        assertEquals(playerB, e.currentPlayerId)

        assertTrue(e.commitLegTransition())
        assertEquals(playerB, e.currentPlayerId)
        assertEquals(0, e.dartsThrownInCurrentLeg)
        assertEquals(RoundLimitState(), e.playerStates[0].state)
        assertEquals(RoundLimitState(), e.playerStates[1].state)
        // Der Leg-Zaehler von A bleibt erhalten.
        assertEquals(1, e.playerStates[0].legsWonInSet)
    }

    @Test
    fun legEnded_lazyCommit_naechsterDartGehoertBereitsZumNeuenLeg() {
        val e = roundLimitEngine(legsToWin = 3)

        e.throwSingles(20, 3)
        e.applyDart(Dart.single(1))
        e.applyDart(Dart.single(1)) // Leg 1 entschieden (A gewinnt)

        // KEIN expliziter commitLegTransition: der naechste Dart holt ihn nach.
        val next = e.applyDart(Dart.single(3))
        assertTrue(next.accepted)
        assertNull("Neues Leg laeuft -> noch kein Gewinner", next.legWinnerId)
        assertEquals(playerB, next.playerId)
        assertEquals(RoundLimitState(darts = 1, points = 3), e.playerStates[1].state)
        assertEquals(RoundLimitState(), e.playerStates[0].state)
        assertEquals(1, e.dartsThrownInCurrentLeg)
        // Keine Doppelzaehlung durch den nachgeholten Wechsel.
        assertEquals(1, e.playerStates[0].legsWonInSet)
        assertEquals(2, e.currentLegNumber)
    }

    // --- Undo nach einem Leg-Ende ohne Werfer-Sieg ------------------------------

    @Test
    fun undoLastDart_nachLegEnded_stelltDenStandVorDemLegEndeExaktWiederHer() {
        val e = roundLimitEngine(legsToWin = 3)

        e.throwSingles(20, 3)
        e.applyDart(Dart.single(1))

        val before = e.snapshot()
        val end = e.applyDart(Dart.single(1))
        assertEquals(playerA, end.legWinnerId)
        assertEquals(1, e.playerStates[0].legsWonInSet)

        // Aufschub-Mechanik greift auch hier: der leg-beendende Dart ist der
        // letzte Historien-Eintrag und damit ruecknehmbar.
        assertTrue(e.undoLastDart())
        assertEquals("Snapshot nach Undo weicht ab", before, e.snapshot())
        assertEquals(0, e.playerStates[0].legsWonInSet)
        assertEquals(1, e.currentLegNumber)
        assertEquals(playerB, e.currentPlayerId)

        // Danach normal weiterspielbar: derselbe Dart entscheidet erneut fuer A.
        val endAgain = e.applyDart(Dart.single(1))
        assertEquals(playerA, endAgain.legWinnerId)
        assertEquals(1, e.playerStates[0].legsWonInSet)
    }

    @Test
    fun undoLastDart_nachMatchEndePerLegEnded_oeffnetDasMatchWieder() {
        val e = roundLimitEngine(legsToWin = 1, setsToWin = 1)

        e.throwSingles(20, 3)
        e.applyDart(Dart.single(1))

        val before = e.snapshot()
        e.applyDart(Dart.single(1)) // Leg + Set + Match fuer A entschieden
        assertTrue(e.isMatchWon)

        assertTrue(e.undoLastDart())
        assertFalse(e.isMatchWon)
        assertNull(e.matchWinnerId)
        assertEquals(before, e.snapshot())

        // Die Engine nimmt wieder Darts an.
        val end = e.applyDart(Dart.single(1))
        assertTrue(end.accepted)
        assertTrue(end.matchWon)
        assertEquals(playerA, end.matchWinnerId)
    }

    // --- Regression: der klassische legWon-Pfad bleibt unveraendert ------------

    @Test
    fun legWon_x01Pfad_unveraendert_legWinnerIdIstDerWerfer() {
        val e = MatchEngine(
            mode = X01Mode(),
            config = GameConfig(startScore = 40, doubleOut = true, legsToWin = 2, setsToWin = 1),
            playerIds = listOf(playerA, playerB),
        )

        val open = e.applyDart(Dart.single(1))
        assertNull("Leg laeuft -> kein Gewinner", open.legWinnerId)

        e.applyDart(Dart.single(1))
        e.applyDart(Dart.single(1)) // Aufnahme-Ende, Wechsel zu B
        assertEquals(playerB, e.currentPlayerId)

        val win = e.applyDart(Dart.double(20)) // B checkt aus
        assertTrue(win.legWon)
        assertFalse("X01 meldet nie legEnded", win.dartResult?.legEnded ?: true)
        assertEquals(playerB, win.playerId)
        assertEquals("Beim Werfer-Sieg ist der Werfer der Gewinner", playerB, win.legWinnerId)
        assertEquals(1, e.playerStates[1].legsWonInSet)
        assertEquals(X01State(0), e.playerStates[1].state)
    }
}
