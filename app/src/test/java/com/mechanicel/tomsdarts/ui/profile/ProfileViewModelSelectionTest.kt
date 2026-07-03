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
 * Happy-Path-Tests fuer den Auswahlmodus (Match-Start) des [ProfileViewModel]:
 * Aktivieren, Markierungs-Reihenfolge, Toggle, Abbrechen und Match-Start.
 *
 * Laeuft host-seitig unter Robolectric (SDK 34 gepinnt).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileViewModelSelectionTest {

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
    fun enterSelection_aktiviertModusUndSelektiertSpieler() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            assertFalse(viewModel.selection.value.active)

            viewModel.enterSelection(player(1, "Tom"))

            val state = viewModel.selection.value
            assertTrue(state.active)
            assertEquals(listOf(1L), state.selectedIds)
        }

    @Test
    fun enterSelectionMenu_aktiviertModusOhneVorauswahl() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.enterSelectionMenu()

            val state = viewModel.selection.value
            assertTrue(state.active)
            assertTrue(state.selectedIds.isEmpty())
        }

    @Test
    fun toggleSelect_haengtInMarkierungsReihenfolgeAnUndEntferntWieder() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.enterSelectionMenu()
            viewModel.toggleSelect(player(3, "Bjoern"))
            viewModel.toggleSelect(player(1, "Tom"))
            viewModel.toggleSelect(player(2, "Anna"))

            assertEquals(listOf(3L, 1L, 2L), viewModel.selection.value.selectedIds)

            // Toms Markierung entfernen: Reihenfolge der uebrigen bleibt erhalten.
            viewModel.toggleSelect(player(1, "Tom"))
            assertEquals(listOf(3L, 2L), viewModel.selection.value.selectedIds)
        }

    @Test
    fun exitSelection_setztModusUndMarkierungZurueck() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.enterSelection(player(1, "Tom"))
            viewModel.toggleSelect(player(2, "Anna"))

            viewModel.exitSelection()

            val state = viewModel.selection.value
            assertFalse(state.active)
            assertTrue(state.selectedIds.isEmpty())
        }

    @Test
    fun startMatch_mitGenugSpielern_verlaesstAuswahlmodus() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.enterSelection(player(1, "Tom"))
            viewModel.toggleSelect(player(2, "Anna"))

            viewModel.startMatch(listOf(1L, 2L))

            assertFalse(viewModel.selection.value.active)
        }

    @Test
    fun startMatch_mitZuWenigSpielern_bleibtImAuswahlmodus() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            viewModel.enterSelection(player(1, "Tom"))

            viewModel.startMatch(listOf(1L))

            assertTrue(viewModel.selection.value.active)
            assertEquals(listOf(1L), viewModel.selection.value.selectedIds)
        }
}
