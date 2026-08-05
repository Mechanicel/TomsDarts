package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.CountUpMode
import com.mechanicel.tomsdarts.game.CountUpState
import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Match-Integrationstests fuer [CountUpMode] auf [MatchEngine]-Ebene, analog zu
 * [ShanghaiMatchIntegrationTest]: echte Count-Up-Logik (rundenbasierte
 * Punkte-Akkumulation, Sudden Death) durch die volle Engine-Maschinerie -
 * Aufnahme-Buendelung, Spielerwechsel, Leg-/Set-/Match-Aggregation und
 * Undo/Replay.
 *
 * Reines JUnit, kein Robolectric, deterministisch.
 */
class CountUpMatchIntegrationTest {

    private val tom = 10L
    private val anna = 20L
    private val bjoern = 30L

    private fun engine(
        players: List<Long> = listOf(tom, anna),
        legsToWin: Int = 3,
        setsToWin: Int = 1,
    ): MatchEngine<CountUpState> = MatchEngine(
        mode = CountUpMode(),
        config = GameConfig(legsToWin = legsToWin, setsToWin = setsToWin),
        playerIds = players,
    )

    /** Wirft [count] Darts desselben Werts fuer den gerade aktiven Spieler. */
    private fun MatchEngine<CountUpState>.throwRepeated(dart: Dart, count: Int) {
        repeat(count) { applyDart(dart) }
    }

    private fun stateOf(e: MatchEngine<CountUpState>, playerId: Long): CountUpState =
        e.playerStates.first { it.playerId == playerId }.state

    private fun legsWonOf(e: MatchEngine<CountUpState>, playerId: Long): Int =
        e.playerStates.first { it.playerId == playerId }.legsWonInSet

    // --- Volles Leg ueber 8 Runden: legEnded, Gewinner != letzter Werfer -------

    @Test
    fun vollesLeg_achtRunden_gewinnerIstNichtDerLetzteWerfer() {
        val e = engine()
        // Tom (immer Startspieler der Runde) trifft 3xT20 = 180/Runde, Anna
        // verfehlt komplett. Bei 2 Spielern ist Anna in JEDER Runde automatisch
        // der letzte Werfer - ihr 3. Dart der 8. Runde beendet das Leg, doch
        // Tom (mit deutlich mehr Punkten) gewinnt per Rangvergleich.
        for (round in 1 until CountUpState.ROUNDS) {
            e.throwRepeated(Dart.triple(20), 3) // Tom
            e.throwRepeated(Dart.miss(), 3) // Anna
        }
        // Runde 8: Toms Aufnahme beendet die Runde noch nicht (Anna hinkt hinterher).
        e.applyDart(Dart.triple(20)); e.applyDart(Dart.triple(20))
        val tomsLastDart = e.applyDart(Dart.triple(20))
        assertFalse("Anna hat Runde 8 noch nicht gespielt", tomsLastDart.dartResult?.legEnded == true)
        e.applyDart(Dart.miss()); e.applyDart(Dart.miss())
        val end = e.applyDart(Dart.miss()) // Annas 3. Dart -> legEnded

        assertTrue("Leg-Ende ohne Werfer-Sieg", end.dartResult?.legEnded == true)
        assertFalse(end.legWon)
        assertEquals("Werfer ist Anna (letzte der Runde)", anna, end.playerId)
        assertEquals("Gewinner ist Tom (mehr Punkte)", tom, end.legWinnerId)
        assertEquals(8 * 180, stateOf(e, tom).points)
        assertEquals(0, stateOf(e, anna).points)
        assertEquals(1, legsWonOf(e, tom))
        assertFalse(e.isMatchWon)
    }

    // --- Sudden Death ueber die Engine: Gleichstand nach Runde 8, Entscheidung in Runde 9 ---

    @Test
    fun suddenDeath_ueberDieEngine_gleichstandNachRundeAcht_entscheidungInRundeNeun() {
        val e = engine()
        var lastRound8Result: MatchDartResult<CountUpState>? = null
        // Runden 1..8: beide Spieler treffen exakt denselben Wert (Single 20) -
        // exakter Gleichstand (480:480) nach der regulaeren Phase, kein legEnded.
        for (round in 1..CountUpState.ROUNDS) {
            e.throwRepeated(Dart.single(20), 3) // Tom: +60
            e.applyDart(Dart.single(20)); e.applyDart(Dart.single(20))
            val afterAnna = e.applyDart(Dart.single(20)) // Anna: +60
            if (round == CountUpState.ROUNDS) lastRound8Result = afterAnna
        }
        assertFalse("Gleichstand nach Runde 8 -> kein legEnded", lastRound8Result?.dartResult?.legEnded == true)
        assertEquals(480, stateOf(e, tom).points)
        assertEquals(480, stateOf(e, anna).points)
        assertEquals(9, stateOf(e, tom).round)

        // Runde 9: Tom zieht mit Triple(20) davon, Anna bleibt bei Single(20) ->
        // eindeutige Entscheidung.
        e.throwRepeated(Dart.triple(20), 3) // Tom: +180
        e.applyDart(Dart.single(20)); e.applyDart(Dart.single(20))
        val decisive = e.applyDart(Dart.single(20)) // Annas 3. Dart -> legEnded

        assertTrue(decisive.dartResult?.legEnded == true)
        assertEquals(tom, decisive.legWinnerId)
        assertEquals(660, stateOf(e, tom).points) // 480 + 180
        assertEquals(540, stateOf(e, anna).points) // 480 + 60
        assertEquals(1, legsWonOf(e, tom))
    }

    // --- Rotation ueber mehrere Count-Up-Legs -----------------------------------

    @Test
    fun rotationUeberZweiCountUpLegs_legStartWechseltUndZaehlerBleibenKorrekt() {
        val e = engine(legsToWin = 5)
        // Leg 1 (Tom startet): Tom trifft hoch, Anna verfehlt -> Tom gewinnt.
        for (round in 1 until CountUpState.ROUNDS) {
            e.throwRepeated(Dart.triple(20), 3)
            e.throwRepeated(Dart.miss(), 3)
        }
        e.throwRepeated(Dart.triple(20), 3)
        e.applyDart(Dart.miss()); e.applyDart(Dart.miss())
        val leg1End = e.applyDart(Dart.miss())
        assertEquals(tom, leg1End.legWinnerId)
        assertEquals("Rotation: Leg 2 startet bei Anna", anna, e.currentPlayerId)
        assertTrue(e.commitLegTransition())
        assertEquals(CountUpState.initial(), stateOf(e, tom))
        assertEquals(CountUpState.initial(), stateOf(e, anna))

        // Leg 2 (Anna startet): dasselbe Muster mit vertauschten Rollen -> Anna gewinnt.
        for (round in 1 until CountUpState.ROUNDS) {
            e.throwRepeated(Dart.triple(20), 3) // Anna (jetzt Startspielerin)
            e.throwRepeated(Dart.miss(), 3) // Tom
        }
        e.throwRepeated(Dart.triple(20), 3)
        e.applyDart(Dart.miss()); e.applyDart(Dart.miss())
        val leg2End = e.applyDart(Dart.miss())

        assertEquals(anna, leg2End.legWinnerId)
        assertEquals("Rotation: Leg 3 startet wieder bei Tom", tom, e.currentPlayerId)
        assertEquals(1, legsWonOf(e, tom))
        assertEquals(1, legsWonOf(e, anna))
        assertEquals(3, e.currentLegNumber)
        assertFalse(e.isMatchWon)
    }

    // --- Undo nach einem Count-Up-Rundenende (legEnded) --------------------------

    @Test
    fun undoLastDart_nachLegEnded_stelltRundeUndPunkteDesWerfersWiederHer() {
        val e = engine()
        for (round in 1 until CountUpState.ROUNDS) {
            e.throwRepeated(Dart.triple(20), 3) // Tom
            e.throwRepeated(Dart.miss(), 3) // Anna
        }
        e.throwRepeated(Dart.triple(20), 3) // Tom: Runde 8 fertig, 1440 Punkte
        e.applyDart(Dart.miss()) // Anna: 1. Dart Runde 8
        val partial = e.applyDart(Dart.miss()) // Anna: 2. Dart (noch offen)
        assertFalse(partial.dartResult?.legEnded == true)
        val before = e.snapshot() // Zustand unmittelbar VOR dem entscheidenden 3. Dart
        val end = e.applyDart(Dart.miss()) // Anna: 3. Dart -> legEnded

        assertTrue(end.dartResult?.legEnded == true)
        assertEquals(tom, end.legWinnerId)
        assertEquals(0, stateOf(e, anna).points)
        assertEquals(9, stateOf(e, anna).round) // State-Vertrag: bereits die naechste Runde

        assertTrue(e.undoLastDart())
        assertEquals(before, e.snapshot())
        assertEquals(8, stateOf(e, anna).round) // Aufnahme wieder offen: zurueck in Runde 8
        assertEquals(0, legsWonOf(e, tom))
        assertEquals(anna, e.currentPlayerId)

        // Erneut werfen -> dieselbe Entscheidung faellt wieder.
        val endAgain = e.applyDart(Dart.miss())
        assertTrue(endAgain.dartResult?.legEnded == true)
        assertEquals(tom, endAgain.legWinnerId)
        assertEquals(1, legsWonOf(e, tom))
    }

    // --- Cross-Turn-Undo mitten im Leg -------------------------------------------

    @Test
    fun crossTurnUndo_mittenImLeg_stelltPunkteUndRundeSchrittweiseWiederHer() {
        val e = engine()
        // Runde 1: Tom wirft eine volle Aufnahme (Triple 20 + Single 5 + Miss).
        e.applyDart(Dart.triple(20))
        e.applyDart(Dart.single(5))
        e.applyDart(Dart.miss())
        assertEquals(anna, e.currentPlayerId)
        assertEquals(2, stateOf(e, tom).round)
        assertEquals(65, stateOf(e, tom).points) // 60 + 5 + 0

        // Anna: volle Aufnahme in Runde 1 (nur Fehlwuerfe).
        e.throwRepeated(Dart.miss(), 3)
        assertEquals(tom, e.currentPlayerId)

        // Tom beginnt Runde 2: 2 Darts, dann Cross-Turn-Undo.
        e.applyDart(Dart.single(18))
        e.applyDart(Dart.double(18))
        assertEquals(2, stateOf(e, tom).round)
        assertEquals(119, stateOf(e, tom).points) // 65 + 18 + 36

        // Schritt 1: den 2. Dart der laufenden Aufnahme zurueck.
        assertTrue(e.undoLastDart())
        assertEquals(2, stateOf(e, tom).round)
        assertEquals(83, stateOf(e, tom).points) // 65 + 18
        assertEquals(tom, e.currentPlayerId)

        // Schritt 2: den 1. Dart der Aufnahme zurueck -> Runde 2 mit leerer Aufnahme.
        assertTrue(e.undoLastDart())
        assertEquals(2, stateOf(e, tom).round)
        assertEquals(65, stateOf(e, tom).points)

        // Schritt 3: ueber die Spielerwechsel-Grenze zurueck zu Annas 3. Dart
        // der Runde 1 - ihre Aufnahme wird wieder geoeffnet.
        assertTrue(e.undoLastDart())
        assertEquals(anna, e.currentPlayerId)
        assertEquals(1, stateOf(e, anna).round)
        assertEquals(2, e.playerStates.first { it.playerId == anna }.legSnapshot.dartsInTurn)

        // Wieder vorspielen: derselbe Verlauf stellt sich exakt wieder her.
        e.applyDart(Dart.miss())
        assertEquals(tom, e.currentPlayerId)
        assertEquals(2, stateOf(e, tom).round)
        e.applyDart(Dart.single(18))
        e.applyDart(Dart.double(18))
        assertEquals(119, stateOf(e, tom).points)
    }

    // --- 3+ Spieler: der Fuehrende ist NICHT der letzte Werfer der Runde --------

    @Test
    fun dreiSpieler_ueberDieEngine_fuehrenderIstNichtDerLetzteWerferDerRunde() {
        val e = engine(players = listOf(tom, anna, bjoern), legsToWin = 3)
        // Reihenfolge: Tom, Anna, Bjoern. Anna (die MITTLERE) fuehrt klar,
        // Bjoern (der LETZTE Werfer jeder Runde) entscheidet das Leg trotzdem
        // nicht fuer sich.
        for (round in 1 until CountUpState.ROUNDS) {
            e.throwRepeated(Dart.miss(), 3) // Tom: 0
            e.throwRepeated(Dart.triple(20), 3) // Anna: fuehrt
            e.throwRepeated(Dart.single(1), 3) // Bjoern: wenig
        }
        // Runde 8: gleiches Muster, Bjoerns 3. Dart beendet das Leg.
        e.throwRepeated(Dart.miss(), 3)
        e.throwRepeated(Dart.triple(20), 3)
        e.applyDart(Dart.single(1)); e.applyDart(Dart.single(1))
        val end = e.applyDart(Dart.single(1))

        assertTrue(end.dartResult?.legEnded == true)
        assertEquals("Werfer ist Bjoern (letzter der Runde)", bjoern, end.playerId)
        assertEquals("Gewinner ist Anna (fuehrt, steht in der Mitte)", anna, end.legWinnerId)
        assertEquals(8 * 180, stateOf(e, anna).points)
        assertEquals(8 * 3, stateOf(e, bjoern).points)
        assertEquals(0, stateOf(e, tom).points)
        assertEquals(1, legsWonOf(e, anna))
        assertEquals(0, legsWonOf(e, tom))
        assertEquals(0, legsWonOf(e, bjoern))
    }
}
