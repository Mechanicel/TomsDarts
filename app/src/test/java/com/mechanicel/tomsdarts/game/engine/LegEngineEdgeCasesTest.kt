package com.mechanicel.tomsdarts.game.engine

import com.mechanicel.tomsdarts.game.Dart
import com.mechanicel.tomsdarts.game.GameConfig
import com.mechanicel.tomsdarts.game.X01Mode
import com.mechanicel.tomsdarts.game.X01State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Abhaertungs-/Regressionstests fuer [LegEngine] (ergaenzt die Happy-Path-Tests in
 * [LegEngineTest]). Reines JUnit, kein Robolectric, deterministisch.
 *
 * Schwerpunkte:
 * - Aufnahme-Zyklus ueber mehrere Aufnahmen (turnStart/dartsInTurn-Reset),
 * - Bust beim 1./2./3. Dart inkl. Revert auf den Aufnahme-Startzustand NACH
 *   vorherigen Aufnahmen (nicht auf den Leg-Startwert),
 * - Checkout-Varianten (1. Dart, 3. Dart) und permanenter No-op nach Leg-Gewinn,
 * - No-op-Faelle (3 Darts ohne startNewTurn, nach Bust ohne startNewTurn),
 * - startNewTurn mitten in der Aufnahme als IST-Verhalten dokumentiert,
 * - dartIndex/scored/totalScored ueber regulaere und bust-behaftete Aufnahmen,
 * - doubleOut=false-Konfiguration,
 * - verschiedene Startscores (301) bis Checkout.
 */
class LegEngineEdgeCasesTest {

    private val config = GameConfig(startScore = 501, doubleOut = true)

    private fun engine(start: Int = 501, doubleOut: Boolean = true): LegEngine<X01State> =
        LegEngine(X01Mode(), config.copy(startScore = start, doubleOut = doubleOut))

    // --- Aufnahme-Zyklus ueber mehrere Aufnahmen -----------------------------

    @Test
    fun aufnahmeZyklus_zweiAufnahmen_restLaeuftKorrektWeiter() {
        val e = engine(start = 300)

        // Aufnahme 1: 3x Triple20 -> 300 - 180 = 120.
        e.applyDart(Dart.triple(20))
        e.applyDart(Dart.triple(20))
        e.applyDart(Dart.triple(20))
        assertEquals(X01State(120), e.state)
        assertTrue(e.isTurnEnded)
        assertEquals(3, e.dartsInTurn)

        assertTrue(e.startNewTurn())
        // Reset: dartsInTurn 0, Aufnahme nicht beendet, neuer turnStart == aktueller Rest.
        assertEquals(0, e.dartsInTurn)
        assertFalse(e.isTurnEnded)
        assertEquals(X01State(120), e.snapshot().turnStartState)
        assertEquals(X01State(120), e.state)

        // Aufnahme 2: ein Single20 -> Rest 100, Aufnahme laeuft weiter.
        val r = e.applyDart(Dart.single(20))
        assertTrue(r.accepted)
        assertEquals(0, r.dartIndex)
        assertEquals(20, r.scored)
        assertEquals(20, r.totalScored)
        assertFalse(r.turnEnded)
        assertEquals(X01State(100), e.state)
        assertEquals(1, e.dartsInTurn)
        // turnStart der zweiten Aufnahme bleibt der Rest zu deren Beginn (120).
        assertEquals(X01State(120), e.snapshot().turnStartState)
    }

    // --- Bust-Details: 1./2./3. Dart, Revert nach Vor-Aufnahmen --------------

    @Test
    fun bust_beimErstenDart_revertAufAufnahmeStartNachVorAufnahme() {
        val e = engine(start = 120)
        // Vor-Aufnahme: 3x Single20 -> 60.
        e.applyDart(Dart.single(20))
        e.applyDart(Dart.single(20))
        e.applyDart(Dart.single(20))
        assertTrue(e.startNewTurn())
        assertEquals(X01State(60), e.snapshot().turnStartState)

        // 1. Dart der neuen Aufnahme: Triple20 -> 60 - 60 = 0, aber kein Double -> Bust.
        val r = e.applyDart(Dart.triple(20))
        assertTrue(r.accepted)
        assertTrue(r.bust)
        assertFalse(r.legWon)
        assertTrue(r.turnEnded)
        assertEquals(0, r.scored)
        assertEquals(0, r.totalScored)
        // Revert auf den Aufnahme-Startzustand 60 (NICHT 120, NICHT 501).
        assertEquals(X01State(60), e.state)
        val snap = e.snapshot()
        assertEquals(1, snap.dartsInTurn)
        assertEquals(listOf(Dart.triple(20)), snap.turnDarts)
        assertTrue(snap.turnBust)
        assertEquals(0, snap.turnScored)
        assertEquals(X01State(60), snap.turnStartState)

        // Nach startNewTurn: turnDarts leer, State = Rest vor der Bust-Aufnahme (60).
        assertTrue(e.startNewTurn())
        assertTrue(e.snapshot().turnDarts.isEmpty())
        assertEquals(X01State(60), e.state)
        assertEquals(X01State(60), e.snapshot().turnStartState)
    }

    @Test
    fun bust_beimZweitenDart_revertAufAufnahmeStartNachVorAufnahme() {
        val e = engine(start = 160)
        // Vor-Aufnahme: 3x Single20 -> 100.
        e.applyDart(Dart.single(20))
        e.applyDart(Dart.single(20))
        e.applyDart(Dart.single(20))
        assertTrue(e.startNewTurn())

        // Aufnahme: T20 -> 40, dann T20 -> 40 - 60 < 0 -> Bust beim 2. Dart.
        e.applyDart(Dart.triple(20))
        assertEquals(X01State(40), e.state)
        val r = e.applyDart(Dart.triple(20))
        assertTrue(r.bust)
        assertTrue(r.turnEnded)
        assertEquals(0, r.totalScored)
        // Revert auf 100, nicht auf 40 und nicht auf 160.
        assertEquals(X01State(100), e.state)
        val snap = e.snapshot()
        assertEquals(2, snap.dartsInTurn)
        assertEquals(listOf(Dart.triple(20), Dart.triple(20)), snap.turnDarts)
        assertEquals(X01State(100), snap.turnStartState)

        assertTrue(e.startNewTurn())
        assertTrue(e.snapshot().turnDarts.isEmpty())
        assertEquals(X01State(100), e.state)
    }

    @Test
    fun bust_beimDrittenDart_revertAufAufnahmeStartNachVorAufnahme() {
        val e = engine(start = 130)
        // Vor-Aufnahme: 3x Single20 -> 70.
        e.applyDart(Dart.single(20))
        e.applyDart(Dart.single(20))
        e.applyDart(Dart.single(20))
        assertTrue(e.startNewTurn())

        // Aufnahme: S20 -> 50, S20 -> 30, T20 -> 30 - 60 < 0 -> Bust beim 3. Dart.
        e.applyDart(Dart.single(20))
        e.applyDart(Dart.single(20))
        assertEquals(X01State(30), e.state)
        val r = e.applyDart(Dart.triple(20))
        assertTrue(r.bust)
        assertTrue(r.turnEnded)
        assertEquals(0, r.scored)
        assertEquals(0, r.totalScored)
        // Revert auf 70.
        assertEquals(X01State(70), e.state)
        val snap = e.snapshot()
        assertEquals(3, snap.dartsInTurn)
        assertEquals(
            listOf(Dart.single(20), Dart.single(20), Dart.triple(20)),
            snap.turnDarts,
        )
        assertTrue(snap.turnBust)
        assertEquals(0, snap.turnScored)
        assertEquals(X01State(70), snap.turnStartState)

        assertTrue(e.startNewTurn())
        assertTrue(e.snapshot().turnDarts.isEmpty())
        assertEquals(X01State(70), e.state)
    }

    // --- Checkout-Varianten --------------------------------------------------

    @Test
    fun checkout_beimDrittenDart_gewinntLeg() {
        val e = engine(start = 110)
        val r1 = e.applyDart(Dart.triple(20)) // 110 -> 50
        val r2 = e.applyDart(Dart.single(10)) // 50 -> 40
        assertFalse(r1.legWon)
        assertFalse(r2.legWon)
        assertFalse(r2.turnEnded)
        assertEquals(X01State(40), e.state)

        val r3 = e.applyDart(Dart.double(20)) // 40 -> 0 mit Double -> legWon
        assertTrue(r3.accepted)
        assertTrue(r3.legWon)
        assertFalse(r3.bust)
        assertTrue(r3.turnEnded)
        assertEquals(2, r3.dartIndex)
        assertEquals(40, r3.scored)
        assertEquals(110, r3.totalScored)
        assertEquals(X01State(0), e.state)
        assertTrue(e.isLegWon)
        assertEquals(3, e.dartsInTurn)
    }

    @Test
    fun nachLegGewinn_applyDartUndStartNewTurnSindPermanentNoOp() {
        val e = engine(start = 40)
        e.applyDart(Dart.double(20)) // legWon
        assertTrue(e.isLegWon)

        // applyDart bleibt No-op und veraendert nichts.
        val a = e.applyDart(Dart.triple(20))
        assertFalse(a.accepted)
        assertNull(a.outcome)
        assertEquals(-1, a.dartIndex)
        assertEquals(X01State(0), e.state)

        // startNewTurn bleibt No-op (false) und veraendert nichts.
        assertFalse(e.startNewTurn())
        assertEquals(X01State(0), e.state)
        assertTrue(e.isLegWon)
        assertTrue(e.isTurnEnded)

        // Auch ein zweiter Versuch bleibt No-op (permanent).
        assertFalse(e.applyDart(Dart.single(1)).accepted)
        assertFalse(e.startNewTurn())
        assertEquals(X01State(0), e.state)
    }

    // --- No-op-Faelle --------------------------------------------------------

    @Test
    fun applyDart_nachBustOhneStartNewTurn_istNoOp() {
        // Bust beendet die Aufnahme; ohne startNewTurn ist der naechste Dart No-op.
        val e = engine(start = 20)
        val bustR = e.applyDart(Dart.triple(20)) // 20 - 60 < 0 -> Bust beim 1. Dart
        assertTrue(bustR.bust)
        assertTrue(e.isTurnEnded)
        assertEquals(X01State(20), e.state)

        val r = e.applyDart(Dart.single(1))
        assertFalse(r.accepted)
        assertNull(r.outcome)
        assertEquals(-1, r.dartIndex)
        // State und Aufnahme-Daten unveraendert (weiterhin der Bust-Dart erfasst).
        assertEquals(X01State(20), e.state)
        assertEquals(1, e.dartsInTurn)
        assertTrue(e.snapshot().turnBust)
    }

    @Test
    fun startNewTurn_mittenInAufnahme_verwirftLaufendeAufnahme_istVerhalten() {
        // IST-Verhalten (kein Soll-Urteil): Solange das Leg nicht gewonnen ist,
        // setzt startNewTurn die laufende Aufnahme zurueck und setzt turnStart auf
        // den aktuellen Rest. Ein bereits geworfener, noch nicht gewerteter Dart
        // bleibt in der Wertung (currentState wurde regulaer reduziert), aber die
        // Aufnahme-Darts werden verworfen.
        val e = engine(start = 100)
        e.applyDart(Dart.triple(20)) // 100 -> 40, 1 Dart, Aufnahme nicht beendet
        assertEquals(X01State(40), e.state)
        assertEquals(1, e.dartsInTurn)
        assertFalse(e.isTurnEnded)

        assertTrue(e.startNewTurn())
        // turnStart auf aktuellen Rest (40) rebaselined, Darts geleert.
        assertEquals(0, e.dartsInTurn)
        assertFalse(e.isTurnEnded)
        assertEquals(X01State(40), e.state)
        assertEquals(X01State(40), e.snapshot().turnStartState)
        assertTrue(e.snapshot().turnDarts.isEmpty())
        assertEquals(0, e.snapshot().turnScored)
    }

    // --- dartIndex / scored / totalScored ------------------------------------

    @Test
    fun throwDaten_busterDartHatScored0_aberWirdInTurnDartsErfasst() {
        val e = engine(start = 50)
        val r1 = e.applyDart(Dart.single(20)) // 50 -> 30
        val r2 = e.applyDart(Dart.single(20)) // 30 -> 10
        assertEquals(listOf(0, 1), listOf(r1.dartIndex, r2.dartIndex))
        assertEquals(listOf(20, 20), listOf(r1.scored, r2.scored))
        assertEquals(listOf(20, 40), listOf(r1.totalScored, r2.totalScored))

        val r3 = e.applyDart(Dart.triple(20)) // 10 - 60 < 0 -> Bust beim 3. Dart
        assertTrue(r3.bust)
        // dartIndex bleibt korrekt fortgezaehlt (2), aber scored = 0 und
        // totalScored der verworfenen Aufnahme = 0.
        assertEquals(2, r3.dartIndex)
        assertEquals(0, r3.scored)
        assertEquals(0, r3.totalScored)
        // Bust-Dart bleibt fuer die Persistenz in turnDarts erhalten.
        assertEquals(3, e.snapshot().turnDarts.size)
        assertEquals(Dart.triple(20), e.snapshot().turnDarts.last())
    }

    // --- doubleOut = false ---------------------------------------------------

    @Test
    fun doubleOutFalse_checkoutMitBeliebigemDart() {
        val e = engine(start = 40, doubleOut = false)
        val r1 = e.applyDart(Dart.single(20)) // 40 -> 20
        assertFalse(r1.legWon)
        // Finish mit Single (kein Double noetig).
        val r2 = e.applyDart(Dart.single(20)) // 20 -> 0 -> legWon ohne Double
        assertTrue(r2.accepted)
        assertTrue(r2.legWon)
        assertFalse(r2.bust)
        assertTrue(r2.turnEnded)
        assertEquals(20, r2.scored)
        assertEquals(40, r2.totalScored)
        assertEquals(X01State(0), e.state)
        assertTrue(e.isLegWon)
        assertEquals(2, e.dartsInTurn)
    }

    @Test
    fun doubleOutFalse_rest1IstRegulaerKeinBust() {
        // Ohne Double-Out ist Rest 1 spielbar (kein Bust), anschliessend Single1 finisht.
        val e = engine(start = 2, doubleOut = false)
        val r1 = e.applyDart(Dart.single(1)) // 2 -> 1, regulaer
        assertTrue(r1.accepted)
        assertFalse(r1.bust)
        assertFalse(r1.legWon)
        assertEquals(X01State(1), e.state)

        val r2 = e.applyDart(Dart.single(1)) // 1 -> 0 -> legWon
        assertTrue(r2.legWon)
        assertEquals(X01State(0), e.state)
        assertTrue(e.isLegWon)
    }

    // --- Verschiedene Startscores --------------------------------------------

    @Test
    fun startScore301_legUeberMehrereAufnahmenBisCheckout() {
        val e = engine(start = 301)

        // Aufnahme 1: 3x Triple20 -> 301 - 180 = 121.
        e.applyDart(Dart.triple(20))
        e.applyDart(Dart.triple(20))
        e.applyDart(Dart.triple(20))
        assertEquals(X01State(121), e.state)
        assertTrue(e.startNewTurn())

        // Aufnahme 2: T20 -> 61, S20 -> 41, S1 -> 40.
        e.applyDart(Dart.triple(20)) // 121 -> 61
        e.applyDart(Dart.single(20)) // 61 -> 41
        e.applyDart(Dart.single(1))  // 41 -> 40
        assertEquals(X01State(40), e.state)
        assertTrue(e.isTurnEnded)
        assertFalse(e.isLegWon)
        assertTrue(e.startNewTurn())

        // Aufnahme 3: D20 -> 0 -> legWon (Checkout).
        val r = e.applyDart(Dart.double(20))
        assertTrue(r.legWon)
        assertEquals(0, r.dartIndex)
        assertEquals(40, r.scored)
        assertEquals(X01State(0), e.state)
        assertTrue(e.isLegWon)
        assertTrue(e.isTurnEnded)
        assertEquals(1, e.dartsInTurn)
    }
}
