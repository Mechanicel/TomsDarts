package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.KillerMode
import com.mechanicel.tomsdarts.game.KillerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Match-Integrationstests fuer [KillerMode] auf [MatchEngine]-Ebene, analog zu
 * [ShanghaiMatchIntegrationTest]/[CountUpMatchIntegrationTest]: echte
 * Killer-Logik (Killer-Werdung, Fremdwirkung, Eliminierung, Sieg) durch die volle
 * Engine-Maschinerie - Aufnahme-Buendelung, Spielerwechsel MIT
 * Eliminierungs-Skip und Leg-Aggregation.
 *
 * Killer ist der erste PRODUKTIVE Nutzer der Eliminierungs-Infrastruktur
 * (`MatchEngine.nextActiveIndex`), die bislang nur ueber den Test-Fake
 * [com.mechanicel.tomsdarts.testing.EliminationFakeMode] abgesichert war (siehe
 * [MatchEngineEliminationTest]/[MatchEngineEliminationHardeningTest]). Hier laeuft
 * echte Killer-Logik durch dieselbe Maschinerie - der Schwerpunkt dieser Datei.
 *
 * Die Zielzahlen werden bewusst NICHT hartcodiert, sondern ueber die einzige
 * Formel-Quelle [KillerState.numberFor] aus einem festen [SEED] pro Sitzplatz
 * abgeleitet - identisch zu dem, was [KillerMode.initialState] intern tut. Das
 * haelt die Tests robust gegen eine spaetere Aenderung der Mischung.
 *
 * Undo-Rundreisen und Leg-Wechsel deckt [KillerMatchUndoHardeningTest] ab.
 * Reines JUnit, kein Robolectric, deterministisch.
 */
class KillerMatchIntegrationTest {

    private val tom = 10L
    private val anna = 20L
    private val bjoern = 30L

    private val mode = KillerMode()

    private fun engine(players: List<Long>, legsToWin: Int = 1): MatchEngine<KillerState> =
        MatchEngine(
            mode = mode,
            config = GameConfig(legsToWin = legsToWin, setsToWin = 1, killerSeed = SEED),
            playerIds = players,
        )

    /** Zielzahl des Sitzplatzes [index] fuer den festen [SEED] - dieselbe Quelle wie der Modus selbst. */
    private fun numberOf(index: Int): Int = KillerState.numberFor(SEED, index)

    private fun MatchEngine<KillerState>.stateOf(playerId: Long): KillerState =
        playerStates.first { it.playerId == playerId }.state

    /** Eliminierungs-Frage aus Sicht der Engine: alle ANDEREN Spieler als Gegner. */
    private fun MatchEngine<KillerState>.isEliminated(playerId: Long): Boolean {
        val target = stateOf(playerId)
        val opponents = playerStates.filter { it.playerId != playerId }.map { it.state }
        return mode.isEliminated(target, opponents)
    }

    // --- Volles Leg: Killer werden, Eliminierungs-Skip in der Rotation, Sieg ---

    @Test
    fun dreiSpieler_vollesLeg_killerWerdenEliminierungsSkipInDerRotationUndLegSieg() {
        val e = engine(listOf(tom, anna, bjoern))
        val n0 = numberOf(0) // Tom
        val n1 = numberOf(1) // Anna
        val n2 = numberOf(2) // Bjoern

        // Turn 1 (Tom): wird Killer, trifft Anna zweimal (Leben 3 -> 1).
        e.applyDart(Dart.double(n0))
        e.applyDart(Dart.double(n1))
        e.applyDart(Dart.double(n1))
        assertEquals("Rotation ohne Eliminierung: regulaer zu Anna", anna, e.currentPlayerId)
        assertEquals(KillerState.LIVES - 2, KillerState.livesOf(n1, listOf(e.stateOf(tom), e.stateOf(bjoern))))

        // Turn 2 (Anna) und Turn 3 (Bjoern): harmlos, niemand eliminiert.
        repeat(3) { e.applyDart(Dart.miss()) }
        assertEquals(bjoern, e.currentPlayerId)
        repeat(3) { e.applyDart(Dart.miss()) }
        assertEquals(tom, e.currentPlayerId)

        // Turn 4 (Tom): der DRITTE Treffer auf Anna eliminiert sie SOFORT (mitten
        // in der Aufnahme, nicht erst am 3. Dart) - noch kein Sieg (Bjoern lebt).
        val eliminatingDart = e.applyDart(Dart.double(n1))
        assertFalse(eliminatingDart.legWon)
        assertTrue("Anna ist ab jetzt eliminiert", e.isEliminated(anna))

        // Zwei weitere Darts auf Bjoern (Leben 3 -> 1); die Aufnahme endet nach 3 Darts.
        e.applyDart(Dart.double(n2))
        val turnEnd = e.applyDart(Dart.double(n2))
        assertTrue(turnEnd.turnEnded)

        // KERN: die Rotation ueberspringt die eliminierte Anna und geht direkt zu
        // Bjoern - der erste produktive Beweis von nextActiveIndex mit echtem Modus.
        assertEquals("Anna ist eliminiert -> uebersprungen", bjoern, e.currentPlayerId)

        // Turn 5 (Bjoern, noch mit einem Leben): harmlos.
        repeat(3) { e.applyDart(Dart.miss()) }
        assertEquals(
            "nextIndex(Bjoern) ueberspringt die weiterhin tote Anna direkt zu Tom",
            tom,
            e.currentPlayerId,
        )

        // Turn 6 (Tom): letzter Treffer auf Bjoern eliminiert ihn -> alle Gegner
        // aus -> Tom gewinnt das Leg.
        val win = e.applyDart(Dart.double(n2))
        assertTrue(win.legWon)
        assertEquals(tom, win.legWinnerId)
        assertTrue(e.isEliminated(bjoern))
    }

    // --- 2-Spieler-Duell: ein Treffer-Weg bis legWon ----------------------------

    @Test
    fun zweiSpieler_duell_derDritteTrefferBeendetDasLegSofortOhneWeiterenGegner() {
        val e = engine(listOf(tom, anna))
        val n0 = numberOf(0)
        val n1 = numberOf(1)

        // Turn 1 (Tom): wird Killer, trifft Anna zweimal.
        e.applyDart(Dart.double(n0))
        e.applyDart(Dart.double(n1))
        e.applyDart(Dart.double(n1))
        assertEquals(anna, e.currentPlayerId)

        // Turn 2 (Anna): harmlos.
        repeat(3) { e.applyDart(Dart.miss()) }
        assertEquals(tom, e.currentPlayerId)

        // Turn 3 (Tom): der DRITTE Treffer auf Anna ist zugleich der einzige noch
        // lebende Gegner -> sofortiger Leg-Sieg nach nur einem Dart dieser Aufnahme.
        val win = e.applyDart(Dart.double(n1))
        assertTrue(win.legWon)
        assertTrue(win.turnEnded)
        assertEquals(tom, win.legWinnerId)
        assertEquals(1, e.playerStates.first { it.playerId == tom }.legSnapshot.dartsInTurn)
    }

    // --- Zwei Killer jagen dasselbe Opfer: Summen-Inversion ueber Gegner-States -

    @Test
    fun zweiKillerJagenDasselbeOpfer_treffervonZweiVerschiedenenWerfernSummierenSichZurEliminierung() {
        val e = engine(listOf(tom, anna, bjoern))
        val n0 = numberOf(0)
        val n1 = numberOf(1)
        val n2 = numberOf(2)

        // Turn 1 (Tom): wird Killer, trifft Bjoern zweimal (Leben 3 -> 1).
        e.applyDart(Dart.double(n0))
        e.applyDart(Dart.double(n2))
        e.applyDart(Dart.double(n2))
        assertEquals(anna, e.currentPlayerId)

        // Turn 2 (Anna): wird selbst Killer; ihr Treffer auf Bjoern ist der
        // DRITTE insgesamt - aus ZWEI verschiedenen Werfern summiert - und
        // eliminiert ihn. Tom lebt weiter -> aus Annas Sicht noch kein Sieg.
        e.applyDart(Dart.double(n1))
        val eliminating = e.applyDart(Dart.double(n2))
        assertFalse("Tom lebt weiterhin -> kein Leg-Sieg", eliminating.legWon)
        e.applyDart(Dart.miss())

        assertEquals(0, KillerState.livesOf(n2, listOf(e.stateOf(tom), e.stateOf(anna))))
        assertTrue(e.isEliminated(bjoern))

        // Rotation ueberspringt den eliminierten Bjoern (Beweis, dass die
        // Summen-Inversion auch in der Eliminierungs-Abfrage der Engine greift,
        // nicht nur im Wurf selbst).
        assertEquals(tom, e.currentPlayerId)
    }

    // --- Werfer eliminiert zwei Gegner in EINER Aufnahme ------------------------

    @Test
    fun werferEliminiertZweiGegnerInEinerAufnahme_zweiterKillGewinntImSelbenDartDasLeg() {
        val e = engine(listOf(tom, anna, bjoern))
        val n0 = numberOf(0)
        val n1 = numberOf(1)
        val n2 = numberOf(2)

        // Turn 1 (Tom): wird Killer, je ein Treffer auf Anna UND Bjoern (Leben 3 -> 2 je).
        e.applyDart(Dart.double(n0))
        e.applyDart(Dart.double(n1))
        e.applyDart(Dart.double(n2))
        assertEquals(anna, e.currentPlayerId)
        repeat(3) { e.applyDart(Dart.miss()) } // Turn 2 (Anna)
        assertEquals(bjoern, e.currentPlayerId)
        repeat(3) { e.applyDart(Dart.miss()) } // Turn 3 (Bjoern)
        assertEquals(tom, e.currentPlayerId)

        // Turn 4 (Tom): je ein weiterer Treffer (Leben 2 -> 1 je), dritter Dart fuellt die Aufnahme.
        e.applyDart(Dart.double(n1))
        e.applyDart(Dart.double(n2))
        e.applyDart(Dart.miss())
        assertEquals(anna, e.currentPlayerId)
        repeat(3) { e.applyDart(Dart.miss()) } // Turn 5 (Anna)
        assertEquals(bjoern, e.currentPlayerId)
        repeat(3) { e.applyDart(Dart.miss()) } // Turn 6 (Bjoern)
        assertEquals(tom, e.currentPlayerId)

        // Turn 7 (Tom): dart1 eliminiert Anna (noch kein Sieg, Bjoern lebt);
        // dart2 eliminiert Bjoern UND gewinnt im SELBEN Dart das Leg - die
        // Aufnahme endet sofort nach nur zwei statt drei Darts.
        val firstKill = e.applyDart(Dart.double(n1))
        assertFalse("Bjoern lebt noch -> kein Sieg beim ersten Kill", firstKill.legWon)
        assertFalse(firstKill.turnEnded)

        val secondKillAndWin = e.applyDart(Dart.double(n2))
        assertTrue("zweiter Kill gewinnt im selben Dart das Leg", secondKillAndWin.legWon)
        assertTrue(secondKillAndWin.turnEnded)
        assertEquals(tom, secondKillAndWin.legWinnerId)
        assertEquals(2, e.playerStates.first { it.playerId == tom }.legSnapshot.dartsInTurn)
    }

    // --- Kappung: hitsOn waechst nicht ueber den Eliminierungspunkt hinaus ------

    @Test
    fun mehrTrefferAlsLeben_hitsOnWaechstNichtUeberDenEliminierungspunktHinaus_bleibtWirkungslos() {
        val e = engine(listOf(tom, anna, bjoern))
        val n0 = numberOf(0)
        val n1 = numberOf(1)

        e.applyDart(Dart.double(n0)) // Tom wird Killer.
        e.applyDart(Dart.double(n1)) // Treffer 1 (Leben 2).
        e.applyDart(Dart.double(n1)) // Treffer 2 (Leben 1); Aufnahme endet (3 Darts).
        repeat(3) { e.applyDart(Dart.miss()) } // Anna
        repeat(3) { e.applyDart(Dart.miss()) } // Bjoern

        // Turn (Tom): der DRITTE Treffer eliminiert Anna.
        e.applyDart(Dart.double(n1))
        assertEquals(KillerState.LIVES, e.stateOf(tom).hitsOn.getValue(n1))
        assertTrue(e.isEliminated(anna))

        // Ein VIERTER "Treffer" auf die bereits ausgeschiedene Anna bleibt
        // wirkungslos: hitsOn waechst NICHT ueber LIVES hinaus - genau das
        // dokumentierte Verhalten "Double eines Eliminierten ist wirkungslos".
        val stateBefore = e.stateOf(tom)
        val wirkungslos = e.applyDart(Dart.double(n1))
        assertEquals("kein weiterer Effekt auf Toms Zustand", stateBefore, e.stateOf(tom))
        assertEquals(KillerState.LIVES, e.stateOf(tom).hitsOn.getValue(n1))
        assertFalse(wirkungslos.bust)
        assertFalse(wirkungslos.legWon)
    }

    private companion object {
        /** Fester Seed fuer alle Tests dieser Datei - beliebig, aber stabil. */
        const val SEED: Long = 918_273_645L
    }
}
