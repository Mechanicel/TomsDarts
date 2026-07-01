package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.X01Mode
import com.mechanicel.tomsdarts.game.X01State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Abhaertungs-/Regressionstests fuer [MatchEngine] (ergaenzt die Happy-Path-Tests
 * in [MatchEngineTest]). Reines JUnit, kein Robolectric, deterministisch.
 *
 * Schwerpunkte:
 * - Spielerwechsel-Reihenfolge bei 2 und 3 Spielern, Rest-Isolation pro Spieler,
 * - Bust-Wechsel auch bei 3 Spielern (naechster, nicht uebernaechster),
 * - Leg-Rotation des Startspielers (rotiert um 1) inkl. ueber Set-Grenzen,
 * - Legs ohne Set-Abschluss (legsToWin>1) und Reset von legsWonInSet/currentLegNumber
 *   bei Set-Wechsel,
 * - vollstaendiger Set-/Match-Pfad (setsToWin=2, legsToWin=2) mit Zaehler-Pruefung,
 * - undoLastDart innerhalb der Aufnahme (Rest + dartsInTurn, kein Spielerwechsel)
 *   und No-op-Faelle (leere/beendete Aufnahme),
 * - Match-Ende-No-op (applyDart/undoLastDart aendern den Zustand nicht),
 * - legSnapshot beim legWon-Dart traegt den abgeschlossenen Leg-Stand,
 * - Konstruktor-Guard (< 2 Spieler),
 * - Invarianten ueber einen vollstaendigen Match-Verlauf.
 */
class MatchEngineEdgeCasesTest {

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

    /** Wirft eine volle, nicht checkende Aufnahme (3x Single 1) des aktiven Spielers. */
    private fun throwHarmlessTurn(e: MatchEngine<X01State>): MatchDartResult<X01State> {
        e.applyDart(Dart.single(1))
        e.applyDart(Dart.single(1))
        return e.applyDart(Dart.single(1))
    }

    /**
     * Laesst den Spieler mit [winnerIndex] das aktuelle Leg gewinnen.
     *
     * Voraussetzung: Start 40 (Checkout per Double 20) und der Gewinner hat im
     * laufenden Leg noch nicht geworfen (Rest = 40) -- gilt zu Leg-Beginn fuer
     * alle Spieler. Andere Spieler werfen harmlos, bis der Gewinner am Zug ist.
     */
    private fun winLegBy(e: MatchEngine<X01State>, winnerIndex: Int): MatchDartResult<X01State> {
        while (e.currentPlayerIndex != winnerIndex) {
            throwHarmlessTurn(e)
        }
        return e.applyDart(Dart.double(20))
    }

    // --- Spielerwechsel-Reihenfolge & Rest-Isolation (2 Spieler) -------------

    @Test
    fun zweiSpieler_restIstProSpielerIsoliert() {
        val e = engine(start = 501)

        // A wirft drei volle Triple 20 (501 - 180 = 321), Aufnahme endet -> B dran.
        e.applyDart(Dart.triple(20))
        e.applyDart(Dart.triple(20))
        val last = e.applyDart(Dart.triple(20))
        assertTrue(last.turnEnded)
        assertEquals(playerB, e.currentPlayerId)

        // A's Wuerfe duerfen B's Rest NICHT veraendern.
        assertEquals(X01State(321), e.playerStates[0].state)
        assertEquals(X01State(501), e.playerStates[1].state)
    }

    // --- 3 Spieler: Reihenfolge & Bust-Wechsel ------------------------------

    @Test
    fun dreiSpieler_reihenfolgeAbCAUndEigenerRest() {
        val e = engine(start = 501, players = listOf(playerA, playerB, playerC))

        assertEquals(playerA, e.currentPlayerId)
        assertEquals(0, e.currentPlayerIndex)

        throwHarmlessTurn(e)
        assertEquals(playerB, e.currentPlayerId)
        assertEquals(1, e.currentPlayerIndex)

        throwHarmlessTurn(e)
        assertEquals(playerC, e.currentPlayerId)
        assertEquals(2, e.currentPlayerIndex)

        throwHarmlessTurn(e)
        assertEquals(playerA, e.currentPlayerId)
        assertEquals(0, e.currentPlayerIndex)

        // Jeder hat seine eigene Aufnahme gehabt: 501 - 3 = 498 fuer alle drei.
        assertEquals(X01State(498), e.playerStates[0].state)
        assertEquals(X01State(498), e.playerStates[1].state)
        assertEquals(X01State(498), e.playerStates[2].state)
    }

    @Test
    fun dreiSpieler_bustWechseltZumNaechstenSpieler() {
        // Start 100: A wirft T20 (->40), dann T20 ueberwirft -> Bust -> B (nicht C).
        val e = engine(start = 100, players = listOf(playerA, playerB, playerC))
        e.applyDart(Dart.triple(20))
        val r = e.applyDart(Dart.triple(20))

        assertTrue(r.bust)
        assertTrue(r.turnEnded)
        assertEquals(playerA, r.playerId)
        assertEquals(playerB, r.nextPlayerId)
        assertEquals(playerB, e.currentPlayerId)
        assertEquals(1, e.currentPlayerIndex)
        // A's Rest auf Aufnahme-Start (100) zurueck.
        assertEquals(X01State(100), e.playerStates[0].state)
    }

    @Test
    fun dreiSpieler_legRotationRotiertStartspielerUmEins() {
        // legsToWin gross genug, dass drei Leg-Gewinne kein Set abschliessen.
        val e = engine(start = 40, legsToWin = 5, players = listOf(playerA, playerB, playerC))

        // Leg 1: A beginnt.
        assertEquals(0, e.currentPlayerIndex)
        assertEquals(1, e.currentLegNumber)
        winLegBy(e, winnerIndex = 0)

        // Leg 2: Startspieler rotiert -> B.
        assertEquals(1, e.currentPlayerIndex)
        assertEquals(playerB, e.currentPlayerId)
        assertEquals(2, e.currentLegNumber)
        assertEquals(1, e.currentSetNumber)
        winLegBy(e, winnerIndex = 1)

        // Leg 3: Startspieler rotiert -> C.
        assertEquals(2, e.currentPlayerIndex)
        assertEquals(playerC, e.currentPlayerId)
        assertEquals(3, e.currentLegNumber)
        winLegBy(e, winnerIndex = 2)

        // Leg 4: Rotation wickelt zurueck -> A.
        assertEquals(0, e.currentPlayerIndex)
        assertEquals(playerA, e.currentPlayerId)
        assertEquals(4, e.currentLegNumber)

        // Jeder hat genau ein Leg im Set gewonnen, kein Set abgeschlossen.
        assertEquals(1, e.playerStates[0].legsWonInSet)
        assertEquals(1, e.playerStates[1].legsWonInSet)
        assertEquals(1, e.playerStates[2].legsWonInSet)
        assertFalse(e.isMatchWon)
    }

    // --- Legs ohne Set-Abschluss & Reset bei Set-Wechsel --------------------

    @Test
    fun legsToWinDrei_sammeltLegsUndResettetBeiSetWechsel() {
        // legsToWin=3, setsToWin=2: A gewinnt 3 Legs -> Set 1, kein Match.
        val e = engine(start = 40, legsToWin = 3, setsToWin = 2)

        // Leg 1: A startet und gewinnt.
        winLegBy(e, winnerIndex = 0)
        assertEquals(1, e.playerStates[0].legsWonInSet)
        assertEquals(2, e.currentLegNumber)
        assertEquals(1, e.currentSetNumber)

        // Leg 2: B startet (Rotation), A gewinnt erneut.
        assertEquals(playerB, e.currentPlayerId)
        winLegBy(e, winnerIndex = 0)
        // Noch KEIN Set: legsWonInSet[A] == 2, kein Reset.
        assertEquals(2, e.playerStates[0].legsWonInSet)
        assertEquals(3, e.currentLegNumber)
        assertEquals(1, e.currentSetNumber)
        assertEquals(0, e.playerStates[0].setsWon)

        // Leg 3: A gewinnt das dritte Leg -> Set 1.
        val rSet = winLegBy(e, winnerIndex = 0)
        assertTrue(rSet.setWon)
        assertFalse(rSet.matchWon)

        // Set-Wechsel: legsWonInSet ALLER Spieler zurueck auf 0, Set hoch, Leg auf 1.
        assertEquals(1, e.playerStates[0].setsWon)
        assertEquals(0, e.playerStates[0].legsWonInSet)
        assertEquals(0, e.playerStates[1].legsWonInSet)
        assertEquals(2, e.currentSetNumber)
        assertEquals(1, e.currentLegNumber)
        assertFalse(e.isMatchWon)
    }

    // --- Vollstaendiger Set-/Match-Pfad -------------------------------------

    @Test
    fun setsToWinZwei_legsToWinZwei_vollerMatchPfad() {
        // A gewinnt jedes Leg: 2 Legs -> Set 1, 2 Legs -> Set 2 -> Match.
        val e = engine(start = 40, legsToWin = 2, setsToWin = 2)

        // --- Set 1 ---
        // Leg 1 (A startet, A gewinnt).
        winLegBy(e, winnerIndex = 0)
        assertEquals(1, e.playerStates[0].legsWonInSet)
        assertEquals(0, e.playerStates[0].setsWon)
        assertEquals(1, e.currentSetNumber)
        assertEquals(2, e.currentLegNumber)
        assertEquals(playerB, e.currentPlayerId) // Rotation -> B startet Leg 2.

        // Leg 2 (B startet, A gewinnt) -> Set 1 komplett.
        val rSet1 = winLegBy(e, winnerIndex = 0)
        assertTrue(rSet1.setWon)
        assertFalse(rSet1.matchWon)
        assertEquals(1, e.playerStates[0].setsWon)
        assertEquals(0, e.playerStates[0].legsWonInSet) // Reset bei Set-Gewinn.
        assertEquals(2, e.currentSetNumber)
        assertEquals(1, e.currentLegNumber)
        // Rotation laeuft ueber die Set-Grenze weiter (B-Start -> A-Start).
        assertEquals(playerA, e.currentPlayerId)

        // --- Set 2 ---
        // Leg 1 (A startet, A gewinnt).
        winLegBy(e, winnerIndex = 0)
        assertEquals(1, e.playerStates[0].legsWonInSet)
        assertEquals(2, e.currentLegNumber)
        assertEquals(2, e.currentSetNumber)
        assertEquals(playerB, e.currentPlayerId)

        // Leg 2 (B startet, A gewinnt) -> Set 2 -> Match.
        val rMatch = winLegBy(e, winnerIndex = 0)
        assertTrue(rMatch.legWon)
        assertTrue(rMatch.setWon)
        assertTrue(rMatch.matchWon)
        assertEquals(playerA, rMatch.matchWinnerId)
        assertTrue(e.isMatchWon)
        assertEquals(playerA, e.matchWinnerId)
        assertEquals(2, e.playerStates[0].setsWon)
        assertEquals(0, e.playerStates[0].legsWonInSet)
    }

    // --- undoLastDart -------------------------------------------------------

    @Test
    fun undo_stelltRestUndDartsInTurnWiederHer_ohneSpielerwechsel() {
        val e = engine(start = 180)
        e.applyDart(Dart.triple(20)) // 60 -> 120
        e.applyDart(Dart.single(20)) // 20 -> 100
        assertEquals(X01State(100), e.playerStates[0].state)
        assertEquals(2, e.playerStates[0].legSnapshot.dartsInTurn)

        assertTrue(e.undoLastDart())
        assertEquals(X01State(120), e.playerStates[0].state)
        assertEquals(1, e.playerStates[0].legSnapshot.dartsInTurn)
        // Kein Spielerwechsel durch undo.
        assertEquals(playerA, e.currentPlayerId)
        assertEquals(0, e.currentPlayerIndex)

        // Erneutes undo zurueck auf den Aufnahme-Start.
        assertTrue(e.undoLastDart())
        assertEquals(X01State(180), e.playerStates[0].state)
        assertEquals(0, e.playerStates[0].legSnapshot.dartsInTurn)
    }

    @Test
    fun undo_istNoOpBeiLeererAufnahme() {
        val e = engine(start = 501)
        // Frisch, noch kein Dart geworfen.
        assertFalse(e.undoLastDart())
        assertEquals(X01State(501), e.playerStates[0].state)
        assertEquals(playerA, e.currentPlayerId)
    }

    @Test
    fun undo_kannNichtUeberAufnahmeGrenzeZurueck() {
        val e = engine(start = 501)
        // Volle Aufnahme von A -> Wechsel zu B; B's Aufnahme ist leer.
        throwHarmlessTurn(e)
        assertEquals(playerB, e.currentPlayerId)

        // undo zielt auf den AKTUELLEN Spieler (B), dessen Aufnahme leer ist -> No-op.
        assertFalse(e.undoLastDart())
        assertEquals(playerB, e.currentPlayerId)
        // A's beendete Aufnahme bleibt unveraendert (498).
        assertEquals(X01State(498), e.playerStates[0].state)
    }

    // --- Match-Ende-No-op ---------------------------------------------------

    @Test
    fun matchEnde_applyDartUndUndoSindNoOps_zustandUnveraendert() {
        val e = engine(start = 40)
        e.applyDart(Dart.double(20))
        assertTrue(e.isMatchWon)

        val before = e.snapshot()

        val noop = e.applyDart(Dart.triple(20))
        assertFalse(noop.accepted)
        assertNull(noop.dartResult)
        assertTrue(noop.matchWon)
        assertFalse(e.undoLastDart())

        val after = e.snapshot()
        assertEquals(before, after)
    }

    // --- legSnapshot beim Leg-Wechsel ---------------------------------------

    @Test
    fun legSnapshot_beimLegWonDart_traegtAbgeschlossenenLegStand() {
        // legsToWin=2: Leg gewonnen, aber Match laeuft weiter -> neues Leg startet.
        val e = engine(start = 40, legsToWin = 2)
        val r = e.applyDart(Dart.double(20))

        assertTrue(r.legWon)
        assertFalse(r.matchWon)
        // legSnapshot erfasst den abgeschlossenen Leg-Stand (gewinnender Dart):
        assertTrue(r.legSnapshot.isLegWon)
        assertEquals(X01State(0), r.legSnapshot.state)
        assertEquals(listOf(Dart.double(20)), r.legSnapshot.turnDarts)
        assertEquals(1, r.legSnapshot.dartsInTurn)

        // Die LegEngine wurde danach ersetzt: aktueller Spieler-State ist frisch (40).
        assertEquals(X01State(40), e.playerStates[0].state)
        assertEquals(2, e.currentLegNumber)
    }

    // --- Konstruktor-Guard --------------------------------------------------

    @Test
    fun konstruktor_einSpieler_wirftIllegalArgument() {
        assertThrows(IllegalArgumentException::class.java) {
            MatchEngine(X01Mode(), GameConfig(startScore = 501), playerIds = listOf(playerA))
        }
    }

    @Test
    fun konstruktor_keineSpieler_wirftIllegalArgument() {
        assertThrows(IllegalArgumentException::class.java) {
            MatchEngine(X01Mode(), GameConfig(startScore = 501), playerIds = emptyList())
        }
    }

    // --- Invarianten ueber einen vollstaendigen Match-Verlauf ----------------

    @Test
    fun invarianten_ueberVollerMatch_legWonUndBustNieGleichzeitig() {
        val e = engine(start = 40, legsToWin = 2, setsToWin = 2)
        val results = mutableListOf<MatchDartResult<X01State>>()

        // Spiele bis zum Match-Ende; sammle jeden akzeptierten Dart.
        var guard = 0
        while (!e.isMatchWon && guard < 1000) {
            results += winLegBy(e, winnerIndex = 0)
            guard++
        }
        assertTrue(e.isMatchWon)

        for (r in results.filter { it.accepted }) {
            // Nie gleichzeitig Leg-Gewinn und Bust.
            assertFalse("legWon && bust gleichzeitig", r.legWon && r.bust)
            // matchWon impliziert legWon && setWon (gilt fuer akzeptierte Darts).
            if (r.matchWon) {
                assertTrue("matchWon ohne legWon", r.legWon)
                assertTrue("matchWon ohne setWon", r.setWon)
            }
            // setWon impliziert legWon.
            if (r.setWon) {
                assertTrue("setWon ohne legWon", r.legWon)
            }
        }
    }
}
