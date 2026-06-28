package com.mechanicel.tomsdarts.ui.input

import com.mechanicel.tomsdarts.game.Dart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reine JUnit-Tests fuer den [DartInputState]-Holder. Keine Android-/Compose-/
 * Robolectric-Abhaengigkeit - laeuft direkt auf der JVM (Happy-Path-Basis).
 */
class DartInputStateTest {

    @Test
    fun default_istLeerUndSingle() {
        val state = DartInputState()
        assertEquals(DartModifier.SINGLE, state.modifier)
        assertTrue(state.darts.isEmpty())
        assertFalse(state.isComplete)
        assertFalse(state.canUndo)
        assertTrue(state.inputEnabled)
        assertTrue(state.bullEnabled)
        assertEquals(0, state.turnSum)
    }

    @Test
    fun toggleDouble_dannPressNumber_autoResetAufSingle() {
        val state = DartInputState().toggleDouble().pressNumber(20)
        assertEquals(DartModifier.SINGLE, state.modifier)
        assertEquals(listOf(Dart(20, 2)), state.darts)
    }

    @Test
    fun toggleTriple_dannPressNumber_autoResetAufSingle() {
        val state = DartInputState().toggleTriple().pressNumber(20)
        assertEquals(DartModifier.SINGLE, state.modifier)
        assertEquals(listOf(Dart(20, 3)), state.darts)
    }

    @Test
    fun pressNumber_singleHaengtSingleDartAn() {
        val state = DartInputState().pressNumber(19)
        assertEquals(listOf(Dart.single(19)), state.darts)
    }

    @Test
    fun pressNumber_ausserhalb1bis20_istNoOp() {
        assertEquals(DartInputState(), DartInputState().pressNumber(0))
        assertEquals(DartInputState(), DartInputState().pressNumber(21))
        assertEquals(DartInputState(), DartInputState().pressNumber(-5))
    }

    @Test
    fun dreiDarts_machenZugKomplett_undWeitereEingabeNoOp() {
        val state = DartInputState()
            .pressNumber(20)
            .pressNumber(19)
            .pressNumber(18)
        assertTrue(state.isComplete)
        assertFalse(state.inputEnabled)
        assertEquals(3, state.darts.size)

        // pressNumber bei isComplete = No-op.
        val after = state.pressNumber(17)
        assertEquals(state, after)
    }

    @Test
    fun toggleDouble_undToggleTriple_sindExklusiv() {
        val doubleState = DartInputState().toggleDouble()
        assertEquals(DartModifier.DOUBLE, doubleState.modifier)

        // Triple aktivieren deaktiviert Double.
        val tripleState = doubleState.toggleTriple()
        assertEquals(DartModifier.TRIPLE, tripleState.modifier)

        // Double aktivieren deaktiviert Triple.
        val backToDouble = tripleState.toggleDouble()
        assertEquals(DartModifier.DOUBLE, backToDouble.modifier)
    }

    @Test
    fun erneutesTogglen_schaltetZurueckAufSingle() {
        assertEquals(DartModifier.SINGLE, DartInputState().toggleDouble().toggleDouble().modifier)
        assertEquals(DartModifier.SINGLE, DartInputState().toggleTriple().toggleTriple().modifier)
    }

    @Test
    fun pressBull_beiTriple_istNoOp() {
        val state = DartInputState().toggleTriple()
        assertFalse(state.bullEnabled)
        assertEquals(state, state.pressBull())
    }

    @Test
    fun pressBull_beiSingle_haengtBullAn() {
        val state = DartInputState().pressBull()
        assertEquals(listOf(Dart.bull()), state.darts)
        assertEquals(25, state.turnSum)
        assertEquals(DartModifier.SINGLE, state.modifier)
    }

    @Test
    fun pressBull_beiDouble_haengtDoubleBullAn() {
        val state = DartInputState().toggleDouble().pressBull()
        assertEquals(listOf(Dart.doubleBull()), state.darts)
        assertEquals(50, state.turnSum)
        assertEquals(DartModifier.SINGLE, state.modifier)
    }

    @Test
    fun pressOut_haengtMissAn_undResetted() {
        val state = DartInputState().toggleTriple().pressOut()
        assertEquals(listOf(Dart.miss()), state.darts)
        assertEquals(0, state.turnSum)
        assertEquals(DartModifier.SINGLE, state.modifier)
    }

    @Test
    fun undo_entferntLetztenDart() {
        val state = DartInputState().pressNumber(20).pressNumber(19)
        val undone = state.undo()
        assertEquals(listOf(Dart.single(20)), undone.darts)
    }

    @Test
    fun undo_beiLeer_istUnveraendert() {
        val state = DartInputState()
        assertEquals(state, state.undo())
    }

    @Test
    fun undo_aenderModifikatorNicht() {
        val state = DartInputState().toggleDouble().pressNumber(20).toggleTriple()
        assertEquals(DartModifier.TRIPLE, state.modifier)
        val undone = state.undo()
        assertEquals(DartModifier.TRIPLE, undone.modifier)
        assertTrue(undone.darts.isEmpty())
    }

    @Test
    fun undo_beiKomplettemZug_machtWiederEingabeMoeglich() {
        val complete = DartInputState().pressNumber(20).pressNumber(20).pressNumber(20)
        assertTrue(complete.isComplete)
        val undone = complete.undo()
        assertFalse(undone.isComplete)
        assertTrue(undone.inputEnabled)
        assertEquals(2, undone.darts.size)
    }

    @Test
    fun startNewTurn_leertDartsUndResetted() {
        val state = DartInputState().toggleDouble().pressNumber(20).pressNumber(19)
        val fresh = state.startNewTurn()
        assertTrue(fresh.darts.isEmpty())
        assertEquals(DartModifier.SINGLE, fresh.modifier)
    }

    @Test
    fun turnSum_summiertPunktwerte() {
        val state = DartInputState()
            .toggleTriple().pressNumber(20)   // 60
            .toggleDouble().pressNumber(19)   // 38
            .pressNumber(5)                   // 5
        assertEquals(103, state.turnSum)
    }

    @Test
    fun modifierMultiplier_mapping() {
        assertEquals(1, DartModifier.SINGLE.multiplier)
        assertEquals(2, DartModifier.DOUBLE.multiplier)
        assertEquals(3, DartModifier.TRIPLE.multiplier)
    }
}
