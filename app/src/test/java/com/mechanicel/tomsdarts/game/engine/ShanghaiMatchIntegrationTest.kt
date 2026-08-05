package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.ShanghaiMode
import com.mechanicel.tomsdarts.game.ShanghaiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Match-Integrationstests fuer [ShanghaiMode] auf [MatchEngine]-Ebene.
 *
 * Shanghai ist der erste PRODUKTIVE Nutzer der `legEnded`-Infrastruktur
 * (ADR-0028), die bislang nur ueber den Test-Fake
 * [com.mechanicel.tomsdarts.testing.RoundLimitFakeMode] abgesichert war (siehe
 * [MatchEngineLegEndedTest]/[MatchEngineLegEndedHardeningTest]). Hier laeuft
 * echte Shanghai-Logik (rotierende Zielzahl, Sudden Death, Shanghai-Sofortsieg)
 * durch die volle Engine-Maschinerie: Aufnahme-Buendelung, Spielerwechsel,
 * Leg-/Set-/Match-Aggregation und Undo/Replay - inklusive der Shanghai-
 * spezifischen abgeleiteten Zustandsfelder (Runde, Ziel, Trefferspur), die ein
 * genereller Fake wie [com.mechanicel.tomsdarts.testing.RoundLimitFakeMode]
 * nicht abdeckt.
 *
 * Reines JUnit, kein Robolectric, deterministisch.
 */
class ShanghaiMatchIntegrationTest {

    private val tom = 10L
    private val anna = 20L
    private val bjoern = 30L

    private fun engine(
        players: List<Long> = listOf(tom, anna),
        legsToWin: Int = 3,
        setsToWin: Int = 1,
    ): MatchEngine<ShanghaiState> = MatchEngine(
        mode = ShanghaiMode(),
        config = GameConfig(legsToWin = legsToWin, setsToWin = setsToWin),
        playerIds = players,
    )

    /** Wirft [count] Darts desselben Werts fuer den gerade aktiven Spieler. */
    private fun MatchEngine<ShanghaiState>.throwRepeated(dart: Dart, count: Int) {
        repeat(count) { applyDart(dart) }
    }

    /** Triple(target) + zwei Fehlwuerfe fuer den gerade aktiven Spieler (3 Darts = 1 Aufnahme). */
    private fun MatchEngine<ShanghaiState>.throwTripleThenTwoMisses(target: Int) {
        applyDart(Dart.triple(target))
        applyDart(Dart.miss())
        applyDart(Dart.miss())
    }

    private fun MatchEngine<ShanghaiState>.throwThreeMisses() {
        throwRepeated(Dart.miss(), 3)
    }

    private fun stateOf(e: MatchEngine<ShanghaiState>, playerId: Long): ShanghaiState =
        e.playerStates.first { it.playerId == playerId }.state

    private fun legsWonOf(e: MatchEngine<ShanghaiState>, playerId: Long): Int =
        e.playerStates.first { it.playerId == playerId }.legsWonInSet

    // --- Volles Leg ueber 7 Runden: legEnded, Gewinner != letzter Werfer -------

    @Test
    fun vollesLeg_siebenRunden_gewinnerIstNichtDerLetzteWerfer() {
        val e = engine()
        // Pro Runde: Tom (startet jede Runde) trifft Triple(Ziel), Anna verfehlt
        // komplett. Bei 2 Spielern ist Anna in JEDER Runde automatisch der
        // letzte Werfer - ihr 3. Dart der 7. Runde beendet das Leg, doch Tom
        // (mit deutlich mehr Punkten) gewinnt per Rangvergleich.
        for (round in 1 until ShanghaiState.ROUNDS) {
            e.throwTripleThenTwoMisses(round)
            e.throwThreeMisses()
        }
        e.throwTripleThenTwoMisses(ShanghaiState.ROUNDS)
        e.applyDart(Dart.miss())
        e.applyDart(Dart.miss())
        val end = e.applyDart(Dart.miss())

        assertTrue("Leg-Ende ohne Werfer-Sieg", end.dartResult?.legEnded == true)
        assertFalse(end.legWon)
        assertEquals("Werfer ist Anna (letzte der Runde)", anna, end.playerId)
        assertEquals("Gewinner ist Tom (mehr Punkte)", tom, end.legWinnerId)
        assertEquals(84, stateOf(e, tom).points) // 3 * (1+2+...+7)
        assertEquals(0, stateOf(e, anna).points)
        assertEquals(1, legsWonOf(e, tom))
        assertFalse(e.isMatchWon)
    }

    // --- Sudden Death ueber die Engine: Gleichstand nach Runde 7, Entscheidung in Runde 8 ---

    @Test
    fun suddenDeath_ueberDieEngine_gleichstandNachRundeSieben_entscheidungInRundeAcht() {
        val e = engine()
        var lastRound7Result: MatchDartResult<ShanghaiState>? = null
        // Runden 1..7: beide Spieler treffen exakt dieselbe Zielzahl mit Single -
        // exakter Gleichstand (28:28) nach der regulaeren Phase, kein legEnded.
        for (round in 1..ShanghaiState.ROUNDS) {
            e.applyDart(Dart.single(round)); e.applyDart(Dart.miss()); e.applyDart(Dart.miss())
            e.applyDart(Dart.single(round)); e.applyDart(Dart.miss())
            val afterRound = e.applyDart(Dart.miss())
            if (round == ShanghaiState.ROUNDS) lastRound7Result = afterRound
        }
        assertFalse("Gleichstand nach Runde 7 -> kein legEnded", lastRound7Result?.dartResult?.legEnded == true)
        assertEquals(28, stateOf(e, tom).points)
        assertEquals(28, stateOf(e, anna).points)
        assertEquals(8, stateOf(e, tom).round)

        // Runde 8 (Ziel 1): Tom zieht mit Triple davon, Anna bleibt bei Single ->
        // eindeutige Entscheidung.
        e.applyDart(Dart.triple(1)); e.applyDart(Dart.miss()); e.applyDart(Dart.miss()) // Tom: +3
        e.applyDart(Dart.single(1)); e.applyDart(Dart.miss())
        val decisive = e.applyDart(Dart.miss()) // Annas 3. Dart -> legEnded

        assertTrue(decisive.dartResult?.legEnded == true)
        assertEquals(tom, decisive.legWinnerId)
        assertEquals(31, stateOf(e, tom).points) // 28 + 3
        assertEquals(29, stateOf(e, anna).points) // 28 + 1
        assertEquals(1, legsWonOf(e, tom))
    }

    // --- Rotation ueber mehrere Shanghai-Legs -----------------------------------

    @Test
    fun rotationUeberMehrereShanghaiLegs_legStartWechseltUndZaehlerBleibenKorrekt() {
        val e = engine(legsToWin = 5)
        // Leg 1 (Tom startet): Tom trifft, Anna verfehlt -> Tom gewinnt.
        for (round in 1 until ShanghaiState.ROUNDS) {
            e.throwTripleThenTwoMisses(round)
            e.throwThreeMisses()
        }
        e.throwTripleThenTwoMisses(ShanghaiState.ROUNDS)
        e.applyDart(Dart.miss()); e.applyDart(Dart.miss())
        val leg1End = e.applyDart(Dart.miss())
        assertEquals(tom, leg1End.legWinnerId)
        assertEquals("Rotation: Leg 2 startet bei Anna", anna, e.currentPlayerId)
        assertTrue(e.commitLegTransition())
        assertEquals(ShanghaiState.initial(), stateOf(e, tom))
        assertEquals(ShanghaiState.initial(), stateOf(e, anna))

        // Leg 2 (Anna startet): dasselbe Muster mit vertauschten Rollen -> Anna gewinnt.
        for (round in 1 until ShanghaiState.ROUNDS) {
            e.throwTripleThenTwoMisses(round) // Anna (jetzt Startspielerin)
            e.throwThreeMisses() // Tom
        }
        e.throwTripleThenTwoMisses(ShanghaiState.ROUNDS)
        e.applyDart(Dart.miss()); e.applyDart(Dart.miss())
        val leg2End = e.applyDart(Dart.miss())

        assertEquals(anna, leg2End.legWinnerId)
        assertEquals("Rotation: Leg 3 startet wieder bei Tom", tom, e.currentPlayerId)
        assertEquals(1, legsWonOf(e, tom))
        assertEquals(1, legsWonOf(e, anna))
        assertEquals(3, e.currentLegNumber)
        assertFalse(e.isMatchWon)
    }

    // --- Undo nach einem Shanghai-Sofortsieg (legWon) ---------------------------

    @Test
    fun undoLastDart_nachShanghaiLegWon_stelltAufnahmeUndZaehlerExaktWiederHer() {
        val e = engine()
        // Tom: Single, Double der 1 (noch kein Sieg) - dann Triple der 1 -> Shanghai.
        e.applyDart(Dart.single(1))
        e.applyDart(Dart.double(1))
        val before = e.snapshot()
        val win = e.applyDart(Dart.triple(1))

        assertTrue(win.legWon)
        assertEquals(tom, win.legWinnerId)
        assertEquals(6, stateOf(e, tom).points)
        assertEquals(1, legsWonOf(e, tom))

        assertTrue(e.undoLastDart())
        assertEquals(before, e.snapshot())
        assertEquals(setOf(1, 2), stateOf(e, tom).visitHits)
        assertEquals(1, stateOf(e, tom).round) // Aufnahme laeuft noch, Runde 1 weiterhin offen
        assertEquals(0, legsWonOf(e, tom))
        assertEquals(tom, e.currentPlayerId)

        // Erneut werfen -> derselbe Shanghai-Sieg stellt sich wieder ein.
        val winAgain = e.applyDart(Dart.triple(1))
        assertTrue(winAgain.legWon)
        assertEquals(tom, winAgain.legWinnerId)
        assertEquals(1, legsWonOf(e, tom))
    }

    // --- Undo nach einem Shanghai-Rundenende (legEnded) --------------------------

    @Test
    fun undoLastDart_nachLegEnded_stelltRundeUndTrefferspurDesWerfersWiederHer() {
        val e = engine()
        for (round in 1 until ShanghaiState.ROUNDS) {
            e.throwTripleThenTwoMisses(round)
            e.throwThreeMisses()
        }
        e.throwTripleThenTwoMisses(ShanghaiState.ROUNDS) // Tom: Runde 7 fertig, 84 Punkte
        e.applyDart(Dart.double(ShanghaiState.ROUNDS)) // Anna: 1. Dart Runde 7 (+14)
        val partial = e.applyDart(Dart.double(ShanghaiState.ROUNDS)) // Anna: 2. Dart (+14, noch offen)
        assertFalse(partial.dartResult?.legEnded == true)
        val before = e.snapshot() // Zustand unmittelbar VOR dem entscheidenden 3. Dart
        val end = e.applyDart(Dart.double(ShanghaiState.ROUNDS)) // Anna: 3. Dart -> legEnded

        assertTrue(end.dartResult?.legEnded == true)
        assertEquals(tom, end.legWinnerId)
        assertEquals(42, stateOf(e, anna).points) // 3 x Double(7) = 42
        // State-Vertrag: der Zustand beschreibt bereits die naechste Runde (8).
        assertEquals(8, stateOf(e, anna).round)
        assertTrue(stateOf(e, anna).visitHits.isEmpty())

        assertTrue(e.undoLastDart())
        assertEquals(before, e.snapshot())
        assertEquals(7, stateOf(e, anna).round) // Aufnahme wieder offen: zurueck in Runde 7
        assertEquals(setOf(2), stateOf(e, anna).visitHits) // zwei Doubles bereits getroffen
        assertEquals(28, stateOf(e, anna).points) // 2 x Double(7)
        assertEquals(0, legsWonOf(e, tom))
        assertEquals(anna, e.currentPlayerId)

        // Erneut werfen -> dieselbe Entscheidung faellt wieder.
        val endAgain = e.applyDart(Dart.double(ShanghaiState.ROUNDS))
        assertTrue(endAgain.dartResult?.legEnded == true)
        assertEquals(tom, endAgain.legWinnerId)
        assertEquals(1, legsWonOf(e, tom))
    }

    // --- Cross-Turn-Undo mitten im Leg -------------------------------------------

    @Test
    fun crossTurnUndo_mittenImLeg_stelltRundeZielUndTrefferspurSchrittweiseWiederHer() {
        val e = engine()
        // Runde 1: Tom Single + Double der 1 + Miss (kein Shanghai). Runde 2
        // beginnt fuer Tom danach.
        e.applyDart(Dart.single(1))
        e.applyDart(Dart.double(1))
        e.applyDart(Dart.miss())
        assertEquals(anna, e.currentPlayerId)
        assertEquals(2, stateOf(e, tom).round)
        assertEquals(3, stateOf(e, tom).points) // 1 + 2

        // Anna: volle Aufnahme in Runde 1 (Ziel 1), regulaer daneben.
        e.applyDart(Dart.miss()); e.applyDart(Dart.miss()); e.applyDart(Dart.miss())
        assertEquals(tom, e.currentPlayerId)

        // Tom beginnt Runde 2 (Ziel 2): 2 Darts, dann Cross-Turn-Undo.
        e.applyDart(Dart.single(2))
        e.applyDart(Dart.double(2))
        assertEquals(setOf(1, 2), stateOf(e, tom).visitHits)
        assertEquals(2, stateOf(e, tom).round)
        assertEquals(9, stateOf(e, tom).points) // 3 + 2 + 4

        // Schritt 1: den 2. Dart der laufenden Aufnahme zurueck.
        assertTrue(e.undoLastDart())
        assertEquals(setOf(1), stateOf(e, tom).visitHits)
        assertEquals(2, stateOf(e, tom).round)
        assertEquals(5, stateOf(e, tom).points) // 3 + 2
        assertEquals(tom, e.currentPlayerId)

        // Schritt 2: den 1. Dart der Aufnahme zurueck -> Runde 2 mit leerer Aufnahme.
        assertTrue(e.undoLastDart())
        assertEquals(emptySet<Int>(), stateOf(e, tom).visitHits)
        assertEquals(2, stateOf(e, tom).round)
        assertEquals(3, stateOf(e, tom).points)

        // Schritt 3: ueber die Spielerwechsel-Grenze zurueck zu Annas 3. Dart
        // der Runde 1 - ihre Aufnahme wird wieder geoeffnet.
        assertTrue(e.undoLastDart())
        assertEquals(anna, e.currentPlayerId)
        assertEquals(1, stateOf(e, anna).round)
        assertEquals(2, e.playerStates.first { it.playerId == anna }.legSnapshot.dartsInTurn)

        // Wieder vorspielen: derselbe Rundenverlauf stellt sich exakt wieder her.
        e.applyDart(Dart.miss())
        assertEquals(tom, e.currentPlayerId)
        assertEquals(2, stateOf(e, tom).round)
        e.applyDart(Dart.single(2))
        e.applyDart(Dart.double(2))
        assertEquals(setOf(1, 2), stateOf(e, tom).visitHits)
        assertEquals(9, stateOf(e, tom).points)
    }

    // --- 3+ Spieler: der Fuehrende ist NICHT der letzte Werfer der Runde --------

    @Test
    fun dreiSpieler_ueberDieEngine_fuehrenderIstNichtDerLetzteWerferDerRunde() {
        val e = engine(players = listOf(tom, anna, bjoern), legsToWin = 3)
        // Reihenfolge: Tom, Anna, Bjoern. Anna (die MITTLERE) fuehrt klar,
        // Bjoern (der LETZTE Werfer jeder Runde) entscheidet das Leg trotzdem
        // nicht fuer sich.
        for (round in 1 until ShanghaiState.ROUNDS) {
            e.throwThreeMisses() // Tom: 0
            e.throwTripleThenTwoMisses(round) // Anna: fuehrt
            e.applyDart(Dart.single(round)); e.applyDart(Dart.miss()); e.applyDart(Dart.miss()) // Bjoern: wenig
        }
        // Runde 7: gleiches Muster, Bjoerns 3. Dart beendet das Leg.
        e.throwThreeMisses()
        e.throwTripleThenTwoMisses(ShanghaiState.ROUNDS)
        e.applyDart(Dart.single(ShanghaiState.ROUNDS)); e.applyDart(Dart.miss())
        val end = e.applyDart(Dart.miss())

        assertTrue(end.dartResult?.legEnded == true)
        assertEquals("Werfer ist Bjoern (letzter der Runde)", bjoern, end.playerId)
        assertEquals("Gewinner ist Anna (fuehrt, steht in der Mitte)", anna, end.legWinnerId)
        assertEquals(84, stateOf(e, anna).points) // 3 * (1+..+7)
        assertEquals(28, stateOf(e, bjoern).points) // 1+2+..+7
        assertEquals(0, stateOf(e, tom).points)
        assertEquals(1, legsWonOf(e, anna))
        assertEquals(0, legsWonOf(e, tom))
        assertEquals(0, legsWonOf(e, bjoern))
    }
}
