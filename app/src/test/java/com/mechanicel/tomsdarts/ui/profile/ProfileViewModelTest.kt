package com.mechanicel.tomsdarts.ui.profile

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.entity.Leg
import com.mechanicel.tomsdarts.data.entity.Match
import com.mechanicel.tomsdarts.data.entity.MatchPlayer
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.entity.Turn
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
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

    @Test
    fun emptyThenContentAfterAdd() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
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
    fun deletePlayerWithHistoryRemovesPlayerAndKeepsAnonymizedHistory() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            backgroundScope.launch { viewModel.uiState.collect {} }

            // Spieler mit vollstaendiger Match-Historie anlegen.
            val playerId = db.playerDao().insert(Player(name = "Tom", createdAt = 1L))
            val matchId = db.matchDao().insert(
                Match(
                    modeType = "501",
                    startScore = 501,
                    doubleOut = true,
                    legsToWin = 1,
                    setsToWin = 1,
                    startedAt = 1L,
                ),
            )
            db.matchPlayerDao().insert(MatchPlayer(matchId = matchId, playerId = playerId, position = 0))
            val legId = db.legDao().insert(Leg(matchId = matchId, legNumber = 1, startedAt = 1L))
            val turnId = db.turnDao().insert(
                Turn(legId = legId, playerId = playerId, turnIndex = 0, bust = false, totalScored = 60),
            )

            viewModel.uiState.first { it is ProfileUiState.Content }
            val player = db.playerDao().getById(playerId)!!

            // Loeschen darf trotz Historie nicht crashen.
            viewModel.onDeleteClick(player)
            viewModel.deletePlayer(player)

            // Spieler entfernt -> Empty, Dialog geschlossen (kein Fehlerdialog).
            assertTrue(viewModel.uiState.first { it is ProfileUiState.Empty } is ProfileUiState.Empty)
            assertTrue(viewModel.dialog.value is ProfileDialog.None)

            // Historie bleibt anonymisiert erhalten.
            val turn = db.turnDao().getById(turnId)
            assertTrue("Turn bleibt bestehen", turn != null)
            assertNull("Turn.playerId ist anonymisiert", turn!!.playerId)
        }

    @Test
    fun dialogOpensOnAddAndClosesOnDismiss() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        assertTrue(viewModel.dialog.value is ProfileDialog.None)

        viewModel.onAddClick()
        assertTrue(viewModel.dialog.value is ProfileDialog.Add)

        viewModel.onDismissDialog()
        assertTrue(viewModel.dialog.value is ProfileDialog.None)
    }
}
