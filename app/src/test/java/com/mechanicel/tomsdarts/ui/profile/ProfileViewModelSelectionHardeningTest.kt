package com.mechanicel.tomsdarts.ui.profile

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Haertungstests fuer den Auswahlmodus des [ProfileViewModel] als Ergaenzung zu
 * [ProfileViewModelSelectionTest]. Deckt defensive Pfade und Idempotenz ab:
 * Toggle ausserhalb des Auswahlmodus, Ersetzen der Vorauswahl, leere Match-Liste
 * und das Wiederherstellen einer Markierung durch dreifaches Toggeln.
 *
 * Laeuft host-seitig unter Robolectric (SDK 34 gepinnt). Offline-first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileViewModelSelectionHardeningTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: TomsDartsDatabase
    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TomsDartsDatabase::class.java,
        ).build()
        viewModel = ProfileViewModel(PlayerRepository(db.playerDao()))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun player(id: Long, name: String) = Player(id = id, name = name, createdAt = 1L)

    @Test
    fun toggleSelect_imNormalmodus_istKeinEffekt() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            assertFalse(viewModel.selection.value.active)

            viewModel.toggleSelect(player(1, "Tom"))

            val state = viewModel.selection.value
            assertFalse(state.active)
            assertTrue(state.selectedIds.isEmpty())
        }

    @Test
    fun enterSelection_ersetztVorhandeneMarkierungDurchEinzelnenSpieler() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.enterSelectionMenu()
            viewModel.toggleSelect(player(1, "Tom"))
            viewModel.toggleSelect(player(2, "Anna"))
            assertEquals(listOf(1L, 2L), viewModel.selection.value.selectedIds)

            // Erneutes enterSelection setzt die Markierung auf genau diesen Spieler.
            viewModel.enterSelection(player(3, "Bjoern"))

            val state = viewModel.selection.value
            assertTrue(state.active)
            assertEquals(listOf(3L), state.selectedIds)
        }

    @Test
    fun toggleSelect_dreifach_laesstSpielerWiederMarkiert() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.enterSelectionMenu()

            viewModel.toggleSelect(player(7, "Eve")) // markiert
            assertEquals(listOf(7L), viewModel.selection.value.selectedIds)
            viewModel.toggleSelect(player(7, "Eve")) // entfernt
            assertTrue(viewModel.selection.value.selectedIds.isEmpty())
            viewModel.toggleSelect(player(7, "Eve")) // erneut markiert
            assertEquals(listOf(7L), viewModel.selection.value.selectedIds)
        }

    @Test
    fun startMatch_mitLeererListe_bleibtImAuswahlmodus() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.enterSelectionMenu()
            viewModel.toggleSelect(player(1, "Tom"))

            viewModel.startMatch(emptyList())

            assertTrue(viewModel.selection.value.active)
            assertEquals(listOf(1L), viewModel.selection.value.selectedIds)
        }

    @Test
    fun startMatch_mitDreiSpielern_verlaesstAuswahlmodus() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.enterSelectionMenu()
            viewModel.toggleSelect(player(1, "Tom"))
            viewModel.toggleSelect(player(2, "Anna"))
            viewModel.toggleSelect(player(3, "Bjoern"))

            viewModel.startMatch(listOf(1L, 2L, 3L))

            assertFalse(viewModel.selection.value.active)
            assertTrue(viewModel.selection.value.selectedIds.isEmpty())
        }
}
