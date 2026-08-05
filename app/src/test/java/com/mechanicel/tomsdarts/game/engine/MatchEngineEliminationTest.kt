package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.DartOutcome
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.GameMode
import com.mechanicel.tomsdarts.testing.EliminationFakeMode
import com.mechanicel.tomsdarts.testing.EliminationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Basistests der Eliminierungs-Infrastruktur in der [MatchEngine] (Vorarbeit fuer
 * Killer, PR A): Spieler-Identitaet ueber [GameMode.initialState] mit Index und
 * das Ueberspringen eliminierter Spieler bei der Aufnahme-Rotation
 * ([GameMode.isEliminated]).
 *
 * Als Modus dient der geteilte [EliminationFakeMode] (Test-Fixture, kein
 * Produktions-Modus): jeder Spieler hat die Zielzahl `Sitzplatz + 1`, ein Treffer
 * auf die Zielzahl eines Gegners kostet diesen ein Leben, bei 0 Leben wirft er
 * nicht mehr. Weil die Leben ausschliesslich aus den GEGNER-Zustaenden abgeleitet
 * werden (Inversions-Muster), pruefen diese Tests zugleich, dass die Engine der
 * Eliminierungs-Frage dieselbe Gegner-Sicht gibt wie dem Wurf.
 *
 * Reines JUnit, kein Robolectric, deterministisch.
 */
class MatchEngineEliminationTest {

    private val playerA = 10L
    private val playerB = 20L
    private val playerC = 30L
    private val playerD = 40L

    private fun engine(
        players: List<Long>,
        lives: Int = EliminationFakeMode.DEFAULT_LIVES,
        legsToWin: Int = 1,
    ): MatchEngine<EliminationState> = MatchEngine(
        mode = EliminationFakeMode(lives = lives),
        config = GameConfig(legsToWin = legsToWin, setsToWin = 1),
        playerIds = players,
    )

    /** Wirft die uebergebenen Darts der Reihe nach gegen die Engine. */
    private fun MatchEngine<EliminationState>.throwAll(darts: List<Dart>) {
        darts.forEach { applyDart(it) }
    }

    /** Zielzahlen aller Spieler in Sitzreihenfolge. */
    private fun MatchEngine<EliminationState>.targets(): List<Int> =
        playerStates.map { it.state.target }

    // --- Spieler-Identitaet ueber den playerIndex --------------------------------

    @Test
    fun initialState_bekommtDenSitzplatzIndex_jederSpielerEigeneZielzahl() {
        val e = engine(listOf(playerA, playerB, playerC))

        // Ohne durchgereichten Index haetten alle dieselbe Zielzahl (1).
        assertEquals(listOf(1, 2, 3), e.targets())
    }

    // --- Rotation ohne Eliminierung (Bestandsverhalten) --------------------------

    @Test
    fun rotation_ohneEliminierung_wechseltWieBisherZumNaechstenSpieler() {
        val e = engine(listOf(playerA, playerB))

        repeat(LegEngine.MAX_DARTS_PER_TURN) { e.applyDart(Dart.miss()) }

        assertEquals(playerB, e.currentPlayerId)
    }

    @Test
    fun rotation_solangeNochEinLebenBleibt_wirdNichtUebersprungen() {
        // Zwei Leben: ein Treffer auf B's Zielzahl (2) reicht noch nicht.
        val e = engine(listOf(playerA, playerB, playerC), lives = 2)

        e.throwAll(listOf(Dart.single(2), Dart.miss(), Dart.miss()))

        assertEquals(playerB, e.currentPlayerId)
    }

    // --- Rotation ueberspringt eliminierte Spieler -------------------------------

    @Test
    fun zweiSpieler_gegnerEliminiert_beendetDasLegStattAufIhnZuRotieren() {
        val e = engine(listOf(playerA, playerB))

        val result = e.applyDart(Dart.single(2))

        // Mit nur einem Gegner ist "alle Gegner eliminiert" zugleich der Leg-Sieg -
        // die Rotation darf gar nicht erst auf einen toten Spieler zeigen.
        assertTrue(result.legWon)
        assertEquals(playerA, result.legWinnerId)
    }

    @Test
    fun dreiSpieler_werferEliminiertDirektenNachfolger_uebernaechsterIstDran() {
        val e = engine(listOf(playerA, playerB, playerC))

        e.applyDart(Dart.single(2)) // B (Zielzahl 2) verliert sein einziges Leben.
        e.applyDart(Dart.miss())
        val result = e.applyDart(Dart.miss()) // Aufnahme-Ende -> Rotation.

        assertTrue(result.turnEnded)
        assertFalse("C lebt noch -> kein Leg-Sieg", result.legWon)
        assertEquals(playerC, result.nextPlayerId)
        assertEquals(playerC, e.currentPlayerId)
    }

    @Test
    fun dreiSpieler_zweiTrefferInEinerAufnahme_eliminierenUndUeberspringen() {
        // Zwei Leben: erst der ZWEITE Treffer auf Zielzahl 2 eliminiert B.
        val e = engine(listOf(playerA, playerB, playerC), lives = 2)

        e.applyDart(Dart.single(2))
        e.applyDart(Dart.single(2))
        e.applyDart(Dart.miss())

        assertEquals(playerC, e.currentPlayerId)
    }

    @Test
    fun vierSpieler_mehrereEliminierteInFolge_werdenAlleUebersprungen() {
        val e = engine(listOf(playerA, playerB, playerC, playerD))

        e.applyDart(Dart.single(2)) // B raus
        e.applyDart(Dart.single(3)) // C raus
        val result = e.applyDart(Dart.miss()) // Aufnahme-Ende -> Rotation ueber B und C.

        assertFalse("D lebt noch -> kein Leg-Sieg", result.legWon)
        assertEquals(playerD, result.nextPlayerId)
        assertEquals(playerD, e.currentPlayerId)
    }

    @Test
    fun vierSpieler_naechsterAktiverIstDerLetzteLebende_auchUeberDieListengrenze() {
        val e = engine(listOf(playerA, playerB, playerC, playerD))

        // D (Index 3) eliminiert B und C; naechster Aktiver nach D ist wieder A
        // (Index 0) - der Skip laeuft ueber die Listengrenze hinweg.
        e.throwAll(List(LegEngine.MAX_DARTS_PER_TURN) { Dart.miss() }) // A
        e.throwAll(List(LegEngine.MAX_DARTS_PER_TURN) { Dart.miss() }) // B
        e.throwAll(List(LegEngine.MAX_DARTS_PER_TURN) { Dart.miss() }) // C
        assertEquals(playerD, e.currentPlayerId)

        e.applyDart(Dart.single(2)) // B raus
        e.applyDart(Dart.single(3)) // C raus
        e.applyDart(Dart.miss())

        assertEquals(playerA, e.currentPlayerId)
    }

    // --- Leg-Sieg, wenn alle Gegner eliminiert sind ------------------------------

    @Test
    fun legWon_sobaldAlleGegnerEliminiertSind() {
        val e = engine(listOf(playerA, playerB, playerC))

        val open = e.applyDart(Dart.single(2)) // B raus, C lebt noch.
        assertFalse(open.legWon)

        val result = e.applyDart(Dart.single(3)) // Auch C raus -> A gewinnt.
        assertTrue(result.legWon)
        assertEquals(playerA, result.legWinnerId)
    }

    // --- Undo-Replay ueber einen Skip hinweg -------------------------------------

    @Test
    fun undoReplay_ueberEinenSkipHinweg_istDeterministisch_rundreise() {
        val e = engine(listOf(playerA, playerB, playerC))
        // A eliminiert B und beendet seine Aufnahme -> C ist dran (Skip), C wirft 2.
        val darts = listOf(
            Dart.single(2), Dart.miss(), Dart.miss(), // A
            Dart.miss(), Dart.miss(), // C (B uebersprungen)
        )
        e.throwAll(darts)
        val statesBefore = e.playerStates.map { it.state }
        assertEquals(playerC, e.currentPlayerId)

        // Ein Dart zurueck: C bleibt am Zug (der Skip wird im Replay reproduziert).
        assertTrue(e.undoLastDart())
        assertEquals(playerC, e.currentPlayerId)
        assertEquals(1, e.playerStates[2].legSnapshot.dartsInTurn)

        // Zwei weitere Darts zurueck: A ist wieder mit 2 Darts in der Aufnahme dran,
        // B bleibt durch A's ersten (replayten) Treffer eliminiert.
        assertTrue(e.undoLastDart())
        assertTrue(e.undoLastDart())
        assertEquals(playerA, e.currentPlayerId)
        assertEquals(2, e.playerStates[0].legSnapshot.dartsInTurn)
        assertEquals(mapOf(2 to 1), e.playerStates[0].state.hitsOn)

        // Rundreise: dieselben Darts erneut -> exakt derselbe Stand wie zuvor.
        e.throwAll(darts.takeLast(3))
        assertEquals(statesBefore, e.playerStates.map { it.state })
        assertEquals(playerC, e.currentPlayerId)
        assertEquals(darts.size, e.dartsThrownInCurrentLeg)
    }

    @Test
    fun undoReplay_stelltDieSpielerIdentitaetWiederHer() {
        val e = engine(listOf(playerA, playerB, playerC))
        e.throwAll(listOf(Dart.single(2), Dart.miss(), Dart.miss(), Dart.miss()))

        repeat(4) { assertTrue(e.undoLastDart()) }

        // Die frisch erzeugten LegEngines muessen denselben Sitzplatz-Index sehen.
        assertEquals(listOf(1, 2, 3), e.targets())
        assertTrue(e.playerStates.all { it.state.hitsOn.isEmpty() })
        assertEquals(0, e.dartsThrownInCurrentLeg)
    }

    @Test
    fun commitLegTransition_neuesLeg_behaeltDieSpielerIdentitaetUndLoeschtTreffer() {
        val e = engine(listOf(playerA, playerB, playerC), legsToWin = 2)

        e.applyDart(Dart.single(2))
        val result = e.applyDart(Dart.single(3))
        assertTrue(result.legWon)

        assertTrue(e.commitLegTransition())

        assertEquals(listOf(1, 2, 3), e.targets())
        assertTrue("frisches Leg ohne Treffer", e.playerStates.all { it.state.hitsOn.isEmpty() })
        // Startspieler rotiert regulaer (kein Skip an der Leg-Grenze noetig -
        // in frischen LegEngines ist niemand eliminiert).
        assertEquals(playerB, e.currentPlayerId)
    }

    // --- Defensiver Iterationsdeckel ---------------------------------------------

    /**
     * Entarteter Fake-Modus, der ALLE Spieler als eliminiert meldet - fachlich
     * unerreichbar (ein Modus beendet das Leg spaetestens beim vorletzten
     * Spieler), aber der Beweis, dass die Skip-Schleife einen Deckel hat.
     */
    private class AlwaysEliminatedFakeMode : GameMode<Int> {
        override val key: String = "ALWAYS_ELIMINATED"
        override val displayName: String = "Immer eliminiert (Fake)"

        override fun initialState(config: GameConfig): Int = 0

        override fun applyDart(
            state: Int,
            dart: Dart,
            config: GameConfig,
            opponents: List<Int>,
        ): DartOutcome<Int> = DartOutcome(
            newState = state + dart.value,
            bust = false,
            legWon = false,
            scored = dart.value,
        )

        override fun isEliminated(state: Int, opponents: List<Int>): Boolean = true
    }

    @Test(timeout = 5_000)
    fun alleEliminiert_rotationLaeuftNichtEndlos_sondernFaelltAufDenNaechstenIndexZurueck() {
        val e = MatchEngine(
            mode = AlwaysEliminatedFakeMode(),
            config = GameConfig(legsToWin = 1, setsToWin = 1),
            playerIds = listOf(playerA, playerB, playerC),
        )

        repeat(LegEngine.MAX_DARTS_PER_TURN) { e.applyDart(Dart.miss()) }

        assertEquals(playerB, e.currentPlayerId)
    }
}
