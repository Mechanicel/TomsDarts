package com.mechanicel.tomsdarts.ui.input

import com.mechanicel.tomsdarts.game.Dart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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

    // --- Auto-Reset: Modifikator faellt nach JEDEM darterzeugenden Press auf SINGLE ---

    @Test
    fun autoReset_nachPressNumber_ausDouble() {
        val state = DartInputState().toggleDouble().pressNumber(7)
        assertEquals(DartModifier.SINGLE, state.modifier)
    }

    @Test
    fun autoReset_nachPressNumber_ausTriple() {
        val state = DartInputState().toggleTriple().pressNumber(7)
        assertEquals(DartModifier.SINGLE, state.modifier)
    }

    @Test
    fun autoReset_nachPressBull_ausSingle() {
        val state = DartInputState().pressBull()
        assertEquals(DartModifier.SINGLE, state.modifier)
    }

    @Test
    fun autoReset_nachDoubleBull_ausDouble() {
        // Auch nach doubleBull faellt der Modus zurueck auf SINGLE.
        val state = DartInputState().toggleDouble().pressBull()
        assertEquals(DartModifier.SINGLE, state.modifier)
        assertEquals(listOf(Dart.doubleBull()), state.darts)
    }

    @Test
    fun autoReset_nachPressOut_ausDouble() {
        val state = DartInputState().toggleDouble().pressOut()
        assertEquals(DartModifier.SINGLE, state.modifier)
        assertEquals(listOf(Dart.miss()), state.darts)
    }

    @Test
    fun undo_laesstModifikatorUnveraendert_double() {
        // Ein Dart setzen (Modus faellt auf SINGLE), dann Double aktivieren, dann undo.
        val state = DartInputState().pressNumber(20).toggleDouble()
        assertEquals(DartModifier.DOUBLE, state.modifier)
        val undone = state.undo()
        assertEquals(DartModifier.DOUBLE, undone.modifier)
        assertTrue(undone.darts.isEmpty())
    }

    // --- Exklusivitaet / Toggle ---

    @Test
    fun toggleDouble_dannToggleTriple_nurTriple() {
        val state = DartInputState().toggleDouble().toggleTriple()
        assertEquals(DartModifier.TRIPLE, state.modifier)
    }

    @Test
    fun toggleTriple_zweimal_zurueckAufSingle() {
        val state = DartInputState().toggleDouble().toggleTriple().toggleTriple()
        assertEquals(DartModifier.SINGLE, state.modifier)
    }

    @Test
    fun toggleDouble_zweimal_zurueckAufSingle() {
        val state = DartInputState().toggleDouble().toggleDouble()
        assertEquals(DartModifier.SINGLE, state.modifier)
    }

    @Test
    fun toggleTriple_dannToggleDouble_nurDouble() {
        val state = DartInputState().toggleTriple().toggleDouble()
        assertEquals(DartModifier.DOUBLE, state.modifier)
    }

    // --- Slot-Grenzen / Vollstaendigkeit ---

    @Test
    fun genauZweiDarts_nochNichtKomplett() {
        val state = DartInputState().pressNumber(20).pressNumber(20)
        assertFalse(state.isComplete)
        assertTrue(state.inputEnabled)
        assertEquals(2, state.darts.size)
    }

    @Test
    fun pressNumber_beiKomplett_istNoOp_gleicheReferenz() {
        val complete = DartInputState().pressNumber(20).pressNumber(20).pressNumber(20)
        assertSame(complete, complete.pressNumber(18))
    }

    @Test
    fun pressBull_beiKomplett_istNoOp_gleicheReferenz() {
        val complete = DartInputState().pressNumber(20).pressNumber(20).pressNumber(20)
        assertFalse(complete.bullEnabled)
        assertSame(complete, complete.pressBull())
    }

    @Test
    fun pressOut_beiKomplett_istNoOp_gleicheReferenz() {
        val complete = DartInputState().pressNumber(20).pressNumber(20).pressNumber(20)
        assertSame(complete, complete.pressOut())
    }

    @Test
    fun toggle_beiKomplett_istNoOp_gleicheReferenz() {
        val complete = DartInputState().pressNumber(20).pressNumber(20).pressNumber(20)
        assertSame(complete, complete.toggleDouble())
        assertSame(complete, complete.toggleTriple())
    }

    @Test
    fun startNewTurn_beiKomplett_leertUndMachtEingabefaehig() {
        val complete = DartInputState().pressNumber(20).pressNumber(20).pressNumber(20)
        val fresh = complete.startNewTurn()
        assertTrue(fresh.darts.isEmpty())
        assertFalse(fresh.isComplete)
        assertTrue(fresh.inputEnabled)
        assertEquals(DartModifier.SINGLE, fresh.modifier)
    }

    // --- Bull-Regeln ---

    @Test
    fun pressBull_single_ergibtDart25mal1_value25() {
        val state = DartInputState().pressBull()
        assertEquals(Dart(25, 1), state.darts.single())
        assertEquals(25, state.darts.single().value)
    }

    @Test
    fun pressBull_double_ergibtDart25mal2_value50() {
        val state = DartInputState().toggleDouble().pressBull()
        assertEquals(Dart(25, 2), state.darts.single())
        assertEquals(50, state.darts.single().value)
    }

    @Test
    fun bullEnabled_falseImTriple_pressBullNoOp() {
        val triple = DartInputState().toggleTriple()
        assertFalse(triple.bullEnabled)
        assertSame(triple, triple.pressBull())
    }

    // --- pressNumber Grenzen / Multiplier ---

    @Test
    fun pressNumber_grenze1_ok() {
        val state = DartInputState().pressNumber(1)
        assertEquals(listOf(Dart.single(1)), state.darts)
    }

    @Test
    fun pressNumber_grenze20_ok() {
        val state = DartInputState().pressNumber(20)
        assertEquals(listOf(Dart.single(20)), state.darts)
    }

    @Test
    fun pressNumber_null_istNoOp_gleicheReferenz() {
        val base = DartInputState()
        assertSame(base, base.pressNumber(0))
    }

    @Test
    fun pressNumber_einundzwanzig_istNoOp_gleicheReferenz() {
        val base = DartInputState()
        assertSame(base, base.pressNumber(21))
    }

    @Test
    fun pressNumber_negativ_istNoOp_gleicheReferenz() {
        val base = DartInputState()
        assertSame(base, base.pressNumber(-1))
    }

    @Test
    fun pressNumber_ungueltig_aenderModifikatorNicht() {
        // No-op darf den aktiven Modus nicht versehentlich zuruecksetzen.
        val doubleState = DartInputState().toggleDouble()
        assertSame(doubleState, doubleState.pressNumber(0))
        assertEquals(DartModifier.DOUBLE, doubleState.pressNumber(0).modifier)
    }

    @Test
    fun pressNumber_double_multiplier2() {
        val dart = DartInputState().toggleDouble().pressNumber(13).darts.single()
        assertEquals(2, dart.multiplier)
        assertEquals(26, dart.value)
    }

    @Test
    fun pressNumber_triple_multiplier3() {
        val dart = DartInputState().toggleTriple().pressNumber(13).darts.single()
        assertEquals(3, dart.multiplier)
        assertEquals(39, dart.value)
    }

    // --- turnSum / canUndo / inputEnabled ueber mehrere Schritte ---

    @Test
    fun canUndo_undInputEnabled_ueberSchritte() {
        val s0 = DartInputState()
        assertFalse(s0.canUndo)
        assertTrue(s0.inputEnabled)

        val s1 = s0.pressNumber(20)
        assertTrue(s1.canUndo)
        assertTrue(s1.inputEnabled)
        assertEquals(20, s1.turnSum)

        val s2 = s1.toggleTriple().pressNumber(20)
        assertTrue(s2.canUndo)
        assertTrue(s2.inputEnabled)
        assertEquals(80, s2.turnSum)

        val s3 = s2.pressBull()
        assertTrue(s3.isComplete)
        assertFalse(s3.inputEnabled)
        assertTrue(s3.canUndo)
        assertEquals(105, s3.turnSum)
    }

    @Test
    fun turnSum_mitMissBleibtUnveraendert() {
        val state = DartInputState().pressNumber(20).pressOut().pressNumber(5)
        assertEquals(25, state.turnSum)
        assertEquals(3, state.darts.size)
        assertTrue(state.isComplete)
    }

    @Test
    fun startNewTurn_resettetModifikator_ausTriple() {
        val state = DartInputState().toggleTriple().pressNumber(20).toggleTriple()
        assertEquals(DartModifier.TRIPLE, state.modifier)
        val fresh = state.startNewTurn()
        assertEquals(DartModifier.SINGLE, fresh.modifier)
        assertTrue(fresh.darts.isEmpty())
    }
}
