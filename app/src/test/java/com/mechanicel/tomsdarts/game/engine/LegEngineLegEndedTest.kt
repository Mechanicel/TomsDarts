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
 * Test-Gate-Haertung fuer [LegEngine] und die Vertragserweiterung "Leg-Ende ohne
 * Werfer-Sieg" (`legEnded`), ergaenzt [LegEngineTest]/[LegEngineEdgeCasesTest]
 * (die ausschliesslich den `legWon`-Pfad ueber [com.mechanicel.tomsdarts.game.X01Mode]
 * abdecken) um die EINZELSPIELER-Sicht der [LegEngine] auf `legEnded`:
 *
 * - [LegEngine.isLegEnded] wird gesetzt, [LegEngine.isLegWon] bleibt `false`,
 * - [DartResult.legEnded] wird durchgereicht, [DartResult.legWon] bleibt `false`,
 * - die Aufnahme endet SOFORT bei `legEnded`, unabhaengig davon, ob es der 1.,
 *   2. oder 3. Dart der Aufnahme ist,
 * - `applyDart`/`startNewTurn`/`undoLastDart` sind nach `legEnded` PERMANENTE
 *   No-ops - analog zum bereits gehaerteten `legWon`-Verhalten.
 *
 * Als Modus dient der geteilte [RoundLimitFakeMode] (Test-Fixture): jeder Spieler
 * hat ein festes Dart-Kontingent; ist es bei ALLEN Spielern (inkl. der
 * `opponents`-Liste) aufgebraucht, meldet der Modus `legEnded`. Die [LegEngine]
 * kennt dabei nur EINEN Spieler - die Gewinner-Ermittlung ist Aufgabe der
 * [MatchEngine] (siehe [MatchEngineLegEndedTest]).
 *
 * Reines JUnit, kein Robolectric, deterministisch.
 */
class LegEngineLegEndedTest {

    private val config = GameConfig()

    /** [LegEngine] mit einem einzelnen fixen Gegner-Zustand fuer die Bewertung. */
    private fun engine(
        dartLimit: Int,
        opponent: RoundLimitState,
    ): LegEngine<RoundLimitState> = LegEngine(
        mode = RoundLimitFakeMode(dartLimit = dartLimit),
        config = config,
        opponents = { listOf(opponent) },
    )

    // --- legEnded setzt isLegEnded, nicht isLegWon ----------------------------

    @Test
    fun legEnded_setztIsLegEndedUndDartResultLegEnded_nichtLegWon() {
        // dartLimit=2, Gegner bereits fertig (2 Darts) -> der eigene 2. Dart
        // entscheidet das Leg fuer DIESE Engine (Gewinner offen, siehe Vertrag).
        val e = engine(dartLimit = 2, opponent = RoundLimitState(darts = 2, points = 99))
        e.applyDart(Dart.single(5))
        val r = e.applyDart(Dart.single(5))

        assertTrue(r.accepted)
        assertTrue("DartResult.legEnded gesetzt", r.legEnded)
        assertFalse("DartResult.legWon bleibt false", r.legWon)
        assertFalse("kein Bust", r.bust)
        assertTrue(r.turnEnded)

        assertTrue("LegEngine.isLegEnded gesetzt", e.isLegEnded)
        assertFalse("LegEngine.isLegWon bleibt false", e.isLegWon)
        assertTrue(e.isTurnEnded)
        assertEquals(RoundLimitState(darts = 2, points = 10), e.state)
    }

    // --- Sofort-Ende unabhaengig von der Dart-Position in der Aufnahme --------

    @Test
    fun legEnded_beimErstenDartDerAufnahme_beendetSofortMitEinemDart() {
        // dartLimit=1, Gegner bereits fertig -> der ALLERERSTE Dart dieses
        // Spielers beendet das Leg sofort (dartsInTurn bleibt bei 1).
        val e = engine(dartLimit = 1, opponent = RoundLimitState(darts = 1, points = 0))
        val r = e.applyDart(Dart.single(20))

        assertTrue(r.legEnded)
        assertTrue(r.turnEnded)
        assertEquals(0, r.dartIndex)
        assertEquals(1, e.dartsInTurn)
        assertEquals(RoundLimitState(darts = 1, points = 20), e.state)
    }

    @Test
    fun legEnded_beimZweitenDartDerAufnahme_beendetMitZweiDarts() {
        val e = engine(dartLimit = 2, opponent = RoundLimitState(darts = 2, points = 0))
        val r1 = e.applyDart(Dart.single(1))
        assertFalse("Erster Dart laesst das Leg noch offen", r1.legEnded)
        assertFalse(r1.turnEnded)

        val r2 = e.applyDart(Dart.single(1))
        assertTrue(r2.legEnded)
        assertTrue(r2.turnEnded)
        assertEquals(2, e.dartsInTurn)
    }

    @Test
    fun legEnded_beimDrittenDartDerAufnahme_faelltMitDemRegulaerenAufnahmeEndeZusammen() {
        // dartLimit=3 richtet das legEnded-Signal exakt auf den natuerlichen
        // 3-Dart-Aufnahme-Cap aus - dieselbe Stelle, an der sonst ein regulaeres
        // Aufnahme-Ende ausgeloest wuerde.
        val e = engine(dartLimit = 3, opponent = RoundLimitState(darts = 3, points = 0))
        e.applyDart(Dart.single(1))
        e.applyDart(Dart.single(1))
        val r3 = e.applyDart(Dart.single(1))

        assertTrue("legEnded ueberholt das regulaere 3-Dart-Cap-Ende", r3.legEnded)
        assertTrue(r3.turnEnded)
        assertEquals(3, e.dartsInTurn)
        assertEquals(2, r3.dartIndex)
    }

    // --- Permanente No-ops nach legEnded (analog legWon) ----------------------

    @Test
    fun nachLegEnded_applyDartIstPermanentNoOp() {
        val e = engine(dartLimit = 1, opponent = RoundLimitState(darts = 1, points = 0))
        e.applyDart(Dart.single(20))
        assertTrue(e.isLegEnded)
        val stateAfterEnd = e.state

        val r = e.applyDart(Dart.single(5))
        assertFalse(r.accepted)
        assertNull(r.outcome)
        assertEquals(-1, r.dartIndex)
        assertEquals(stateAfterEnd, e.state)

        // Ein zweiter Versuch bleibt ebenfalls No-op (permanent, kein Reset).
        assertFalse(e.applyDart(Dart.single(5)).accepted)
        assertEquals(stateAfterEnd, e.state)
        assertTrue(e.isLegEnded)
    }

    @Test
    fun nachLegEnded_startNewTurnIstPermanentNoOp() {
        val e = engine(dartLimit = 1, opponent = RoundLimitState(darts = 1, points = 0))
        e.applyDart(Dart.single(20))
        assertTrue(e.isLegEnded)

        assertFalse(e.startNewTurn())
        assertTrue(e.isTurnEnded)
        assertTrue(e.isLegEnded)
        // Ein zweiter Versuch bleibt ebenfalls No-op.
        assertFalse(e.startNewTurn())
    }

    @Test
    fun nachLegEnded_undoLastDartIstPermanentNoOp() {
        val e = engine(dartLimit = 1, opponent = RoundLimitState(darts = 1, points = 0))
        e.applyDart(Dart.single(20))
        assertTrue(e.isLegEnded)
        val stateAfterEnd = e.state
        val dartsInTurnAfterEnd = e.dartsInTurn

        assertFalse(e.undoLastDart())
        assertEquals(stateAfterEnd, e.state)
        assertEquals(dartsInTurnAfterEnd, e.dartsInTurn)
        assertTrue(e.isLegEnded)

        // Ein zweiter Versuch bleibt ebenfalls No-op.
        assertFalse(e.undoLastDart())
        assertEquals(stateAfterEnd, e.state)
    }

    // --- legEnded schliesst legWon aus (Vertrag auf DartResult-Ebene) --------

    @Test
    fun legEnded_undLegWon_schliessenSichAufDartResultEbeneAus() {
        val e = engine(dartLimit = 1, opponent = RoundLimitState(darts = 1, points = 0))
        val r = e.applyDart(Dart.single(20))

        assertFalse("legEnded und legWon nie gleichzeitig", r.legEnded && r.legWon)
        assertTrue(r.legEnded)
    }
}
