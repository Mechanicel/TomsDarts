package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.CricketMode
import com.mechanicel.tomsdarts.game.CricketState
import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integrationstests fuer [CricketMode] durch die echten [LegEngine]/[MatchEngine]
 * hindurch (kein Fake-Modus, siehe Gegenpol [MatchEngineOpponentProviderTest],
 * das die Verdrahtung mit Fakes prueft). Reines JUnit, kein Robolectric,
 * deterministisch.
 *
 * Belegt, dass der lesende `opponents`-Parameter tatsaechlich die reale
 * Cricket-Domaenenlogik beeinflusst (nicht nur strukturell durchgereicht wird):
 * ein Feld, das ein Gegner zwischenzeitlich (in einer eigenen Aufnahme)
 * schliesst, hoert unmittelbar danach auf, fuer den werfenden Spieler zu
 * punkten - inklusive ueber einen echten [MatchEngine]-Spielerwechsel hinweg.
 * Zusaetzlich: Cricket bustet nie, die 3-Darts-Aufnahme-Kadenz und der
 * Spielerwechsel funktionieren mit dem echten Modus wie mit den Fake-Modi.
 */
class CricketMatchIntegrationTest {

    private val playerA = 1L
    private val playerB = 2L
    private val config = GameConfig(legsToWin = 100, setsToWin = 100)

    @Test
    fun matchEngine_gegnerSchliesstFeldInEigenerAufnahme_stopptSofortDieWertungDesAnderenSpielers() {
        val engine = MatchEngine(
            mode = CricketMode(),
            config = config,
            playerIds = listOf(playerA, playerB),
        )

        // A's erste Aufnahme: dreimal Triple 20. 1. Dart schliesst exakt (0->3,
        // kein Overflow), 2./3. Dart punkten je 3*20=60 (B hat Feld 20 noch offen).
        val d1 = engine.applyDart(Dart.triple(20))
        assertEquals(0, d1.dartResult!!.scored)
        val d2 = engine.applyDart(Dart.triple(20))
        assertEquals(60, d2.dartResult!!.scored)
        val d3 = engine.applyDart(Dart.triple(20))
        assertEquals(60, d3.dartResult!!.scored)
        assertTrue("Aufnahme endet nach 3 Darts", d3.turnEnded)
        assertEquals(playerB, engine.currentPlayerId)

        val aAfterFirstTurn = engine.playerStates.first { it.playerId == playerA }
        assertEquals(120, aAfterFirstTurn.state.points)

        // B's Aufnahme: schliesst Feld 20 selbst ueber drei Singles (0->1->2->3),
        // jeweils ohne Overflow -> B bleibt bei 0 Punkten, aber Feld 20 ist jetzt zu.
        engine.applyDart(Dart.single(20))
        engine.applyDart(Dart.single(20))
        val bTurnEnd = engine.applyDart(Dart.single(20))
        assertEquals(0, bTurnEnd.dartResult!!.totalScored)
        assertTrue(bTurnEnd.turnEnded)
        assertEquals(playerA, engine.currentPlayerId)

        val bAfterTurn = engine.playerStates.first { it.playerId == playerB }
        assertTrue(bAfterTurn.state.isClosed(20))

        // A wirft erneut Triple 20: A's eigenes Feld ist laengst zu (Overflow 3),
        // aber der EINZIGE Gegner (B) hat Feld 20 nun ebenfalls geschlossen ->
        // die Live-Gegner-Zustaende aus MatchEngine muessen dies sofort sehen:
        // kein Punkt mehr, obwohl exakt derselbe Dart zuvor 60 Punkte gab.
        val afterOpponentClosed = engine.applyDart(Dart.triple(20))
        assertEquals(0, afterOpponentClosed.dartResult!!.scored)
        val aFinal = engine.playerStates.first { it.playerId == playerA }
        assertEquals(120, aFinal.state.points)
    }

    @Test
    fun matchEngine_cricketBustetNie_undWechseltSpielerNachGenauDreiDarts() {
        val engine = MatchEngine(
            mode = CricketMode(),
            config = config,
            playerIds = listOf(playerA, playerB),
        )

        // 9 Fehlwuerfe (Miss ist in Cricket ein No-Op): erwartete Werferfolge
        // A,A,A,B,B,B,A,A,A - kein Bust, kein Leg-Gewinn, immer 0 Punkte.
        val erwarteteWerfer = listOf(
            playerA, playerA, playerA, playerB, playerB, playerB, playerA, playerA, playerA,
        )
        erwarteteWerfer.forEach { erwarteterWerfer ->
            assertEquals(erwarteterWerfer, engine.currentPlayerId)
            val r = engine.applyDart(Dart.miss())
            assertFalse(r.bust)
            assertFalse(r.legWon)
            assertEquals(0, r.dartResult!!.scored)
        }
        // Nach 9 Darts (A-Aufnahme, B-Aufnahme, A-Aufnahme) ist B fuer die naechste
        // Aufnahme am Zug.
        assertEquals(playerB, engine.currentPlayerId)
    }

    @Test
    fun legEngine_mitEchtemCricketMode_opponentsLambdaBeeinflusstWertungLiveInnerhalbDerAufnahme() {
        // Direkter LegEngine-Test (ohne MatchEngine) mit dem ECHTEN CricketMode,
        // Gegenpol zu MatchEngineOpponentProviderTest.legEngine_applyDart_...
        // (das nur eine Recording-Fake nutzt): hier veraendert sich durch die
        // Live-Lektuere tatsaechlich die gewertete Punktzahl.
        var opponentState = CricketState.initial()
        val leg = LegEngine(
            mode = CricketMode(),
            config = config,
            opponents = { listOf(opponentState) },
        )

        // 1. Dart: schliesst eigenes Feld 20 exakt (kein Overflow).
        val first = leg.applyDart(Dart.triple(20))
        assertEquals(0, first.outcome!!.scored)

        // 2. Dart: eigenes Feld 20 bereits zu, Gegner hat es noch offen -> voller
        // Overflow von 3*20 = 60.
        val second = leg.applyDart(Dart.triple(20))
        assertEquals(60, second.outcome!!.scored)

        // Gegner schliesst Feld 20 "zwischen" den Wuerfen (z.B. waehrend seiner
        // eigenen Aufnahme in der uebergeordneten MatchEngine) - hier direkt am
        // Fake-Zustand simuliert, den das Lambda live liest.
        opponentState = opponentState.copy(marks = opponentState.marks + (20 to 3))

        // 3. Dart (letzter der Aufnahme): derselbe Wurf wie zuvor, aber jetzt OHNE
        // Punkte, weil der einzige Gegner das Feld inzwischen geschlossen hat.
        val third = leg.applyDart(Dart.triple(20))
        assertEquals(0, third.outcome!!.scored)
        assertTrue("Aufnahme endet nach 3 Darts", third.turnEnded)
    }
}
