package com.mechanicel.tomsdarts.ui.profile

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Happy-Path-Test fuer [ProfileViewModel]: In-Memory-Room-DB ueber das echte
 * [PlayerRepository]. Prueft die Ableitung von [ProfileUiState] aus dem
 * Spieler-Stream sowie das Dialog-Handling. Edge-Cases deckt das Test-Gate ab.
 *
 * Laeuft host-seitig unter Robolectric (SDK 34 gepinnt).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var db: TomsDartsDatabase
    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TomsDartsDatabase::class.java,
        ).build()
        viewModel = ProfileViewModel(PlayerRepository(db.playerDao()))
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun emptyThenContentAfterAdd() = runTest {
        // Hot halten, damit WhileSubscribed-StateFlow aktiv emittiert.
        backgroundScope.launch { viewModel.uiState.collect {} }

        val initial = viewModel.uiState.first { it !is ProfileUiState.Loading }
        assertTrue(initial is ProfileUiState.Empty)

        viewModel.addPlayer("Tom")

        val content = viewModel.uiState.first { it is ProfileUiState.Content }
        content as ProfileUiState.Content
        assertEquals(1, content.players.size)
        assertEquals("Tom", content.players.first().name)
    }

    @Test
    fun dialogOpensOnAddAndClosesOnDismiss() = runTest {
        assertTrue(viewModel.dialog.value is ProfileDialog.None)

        viewModel.onAddClick()
        assertTrue(viewModel.dialog.value is ProfileDialog.Add)

        viewModel.onDismissDialog()
        assertTrue(viewModel.dialog.value is ProfileDialog.None)
    }
}
