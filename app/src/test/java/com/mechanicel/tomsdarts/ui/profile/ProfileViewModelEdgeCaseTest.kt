package com.mechanicel.tomsdarts.ui.profile

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Edge-Case-Absicherung fuer [ProfileViewModel]: uiState-Uebergaenge,
 * Dialog-State-Maschine, Trim/Validierung, [ProfileViewModel.updatePlayer]
 * (Persistenz von id/createdAt) sowie [ProfileViewModel.retry].
 *
 * Verwendet wie der Happy-Path-Test das echte [PlayerRepository] ueber eine
 * In-Memory-Room-DB und das `backgroundScope`-Collector-Muster, um den
 * `WhileSubscribed`-StateFlow waehrend des Tests hot zu halten.
 *
 * Laeuft host-seitig unter Robolectric (SDK 34 gepinnt).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileViewModelEdgeCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: TomsDartsDatabase
    private lateinit var repository: PlayerRepository
    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TomsDartsDatabase::class.java,
        ).build()
        repository = PlayerRepository(db.playerDao())
        viewModel = ProfileViewModel(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- uiState-Uebergaenge -------------------------------------------------

    @Test
    fun startStateIsEmpty() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        backgroundScope.launch { viewModel.uiState.collect {} }

        val state = viewModel.uiState.first { it !is ProfileUiState.Loading }
        assertTrue(state is ProfileUiState.Empty)
    }

    @Test
    fun deletingLastPlayerReturnsToEmpty() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.uiState.first { it is ProfileUiState.Empty }

        viewModel.addPlayer("Tom")
        val content = viewModel.uiState.first { it is ProfileUiState.Content }
        content as ProfileUiState.Content
        assertEquals(1, content.players.size)

        viewModel.deletePlayer(content.players.first())

        val afterDelete = viewModel.uiState.first { it is ProfileUiState.Empty }
        assertTrue(afterDelete is ProfileUiState.Empty)
    }

    @Test
    fun multiplePlayersAreContentSortedByName() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.uiState.first { it is ProfileUiState.Empty }

        // Bewusst unsortiert eingefuegt; das DAO sortiert per ORDER BY name.
        viewModel.addPlayer("Charlie")
        viewModel.addPlayer("Anna")
        viewModel.addPlayer("Bert")

        val content = viewModel.uiState.first {
            it is ProfileUiState.Content && it.players.size == 3
        }
        content as ProfileUiState.Content
        assertEquals(
            listOf("Anna", "Bert", "Charlie"),
            content.players.map { it.name },
        )
    }

    // --- Dialog-State-Maschine ----------------------------------------------

    @Test
    fun onEditClickOpensEditDialogWithPlayer() {
        val player = Player(id = 7, name = "Tom", createdAt = 123L)

        viewModel.onEditClick(player)

        val dialog = viewModel.dialog.value
        assertTrue(dialog is ProfileDialog.Edit)
        assertEquals(player, (dialog as ProfileDialog.Edit).player)
    }

    @Test
    fun onDeleteClickOpensConfirmDeleteWithPlayer() {
        val player = Player(id = 9, name = "Jana", createdAt = 456L)

        viewModel.onDeleteClick(player)

        val dialog = viewModel.dialog.value
        assertTrue(dialog is ProfileDialog.ConfirmDelete)
        assertEquals(player, (dialog as ProfileDialog.ConfirmDelete).player)
    }

    @Test
    fun dialogTransitionsBetweenStates() {
        assertTrue(viewModel.dialog.value is ProfileDialog.None)

        viewModel.onAddClick()
        assertTrue(viewModel.dialog.value is ProfileDialog.Add)

        viewModel.onEditClick(Player(id = 1, name = "A", createdAt = 1L))
        assertTrue(viewModel.dialog.value is ProfileDialog.Edit)

        viewModel.onDeleteClick(Player(id = 1, name = "A", createdAt = 1L))
        assertTrue(viewModel.dialog.value is ProfileDialog.ConfirmDelete)

        viewModel.onDismissDialog()
        assertTrue(viewModel.dialog.value is ProfileDialog.None)
    }

    @Test
    fun dialogResetsToNoneAfterAdd() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onAddClick()
        assertTrue(viewModel.dialog.value is ProfileDialog.Add)

        viewModel.addPlayer("Tom")

        // Dialog wird erst nach Abschluss der (suspend) Repository-Aktion
        // geschlossen; deterministisch auf den None-Zustand warten.
        viewModel.dialog.first { it is ProfileDialog.None }
        assertTrue(viewModel.dialog.value is ProfileDialog.None)
    }

    @Test
    fun dialogResetsToNoneAfterUpdate() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.addPlayer("Tom")
        val player = (viewModel.uiState.first { it is ProfileUiState.Content }
                as ProfileUiState.Content).players.first()

        viewModel.onEditClick(player)
        assertTrue(viewModel.dialog.value is ProfileDialog.Edit)

        viewModel.updatePlayer(player.copy(name = "Tommy"))

        viewModel.dialog.first { it is ProfileDialog.None }
        assertTrue(viewModel.dialog.value is ProfileDialog.None)
    }

    @Test
    fun dialogResetsToNoneAfterDelete() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.addPlayer("Tom")
        val player = (viewModel.uiState.first { it is ProfileUiState.Content }
                as ProfileUiState.Content).players.first()

        viewModel.onDeleteClick(player)
        assertTrue(viewModel.dialog.value is ProfileDialog.ConfirmDelete)

        viewModel.deletePlayer(player)

        viewModel.dialog.first { it is ProfileDialog.None }
        assertTrue(viewModel.dialog.value is ProfileDialog.None)
    }

    // --- Trim / Validierung --------------------------------------------------

    @Test
    fun addPlayerTrimsName() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.uiState.first { it is ProfileUiState.Empty }

        viewModel.addPlayer("  Tom  ")

        val content = viewModel.uiState.first { it is ProfileUiState.Content }
        content as ProfileUiState.Content
        assertEquals(1, content.players.size)
        assertEquals("Tom", content.players.first().name)
    }

    @Test
    fun addPlayerWithEmptyNameDoesNothing() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.uiState.first { it is ProfileUiState.Empty }

        viewModel.addPlayer("")

        assertTrue(repository.observePlayers().first().isEmpty())
        assertTrue(viewModel.uiState.value is ProfileUiState.Empty)
    }

    @Test
    fun addPlayerWithBlankNameDoesNothing() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.uiState.first { it is ProfileUiState.Empty }

        viewModel.addPlayer("   ")

        assertTrue(repository.observePlayers().first().isEmpty())
        assertTrue(viewModel.uiState.value is ProfileUiState.Empty)
    }

    @Test
    fun addPlayerWithBlankNameClosesDialog() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        viewModel.onAddClick()
        assertTrue(viewModel.dialog.value is ProfileDialog.Add)

        viewModel.addPlayer("   ")

        assertTrue(viewModel.dialog.value is ProfileDialog.None)
    }

    @Test
    fun updatePlayerWithBlankNameDoesNotPersist() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.addPlayer("Tom")
        val player = (viewModel.uiState.first { it is ProfileUiState.Content }
                as ProfileUiState.Content).players.first()

        viewModel.updatePlayer(player.copy(name = "   "))

        val stored = repository.getPlayer(player.id)
        assertNotNull(stored)
        assertEquals("Tom", stored!!.name)
        assertTrue(viewModel.dialog.value is ProfileDialog.None)
    }

    // --- updatePlayer (Persistenz) ------------------------------------------

    @Test
    fun updatePlayerChangesNameAndKeepsIdAndCreatedAt() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.addPlayer("Tom")
        val original = (viewModel.uiState.first { it is ProfileUiState.Content }
                as ProfileUiState.Content).players.first()

        viewModel.updatePlayer(original.copy(name = "Tommy"))

        val content = viewModel.uiState.first {
            it is ProfileUiState.Content && it.players.first().name == "Tommy"
        }
        content as ProfileUiState.Content
        val updated = content.players.first()
        assertEquals(original.id, updated.id)
        assertEquals(original.createdAt, updated.createdAt)
        assertEquals("Tommy", updated.name)

        // Auch persistent via Repository abrufbar.
        val stored = repository.getPlayer(original.id)
        assertNotNull(stored)
        assertEquals("Tommy", stored!!.name)
        assertEquals(original.createdAt, stored.createdAt)
    }

    @Test
    fun updatePlayerTrimsName() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.addPlayer("Tom")
        val original = (viewModel.uiState.first { it is ProfileUiState.Content }
                as ProfileUiState.Content).players.first()

        viewModel.updatePlayer(original.copy(name = "  Tommy  "))

        val stored = repository.getPlayer(original.id)
        assertNotNull(stored)
        assertEquals("Tommy", stored!!.name)
    }

    // --- retry ---------------------------------------------------------------

    @Test
    fun retryKeepsExistingPlayersVisible() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.addPlayer("Tom")
        viewModel.uiState.first { it is ProfileUiState.Content }

        viewModel.retry()

        val afterRetry = viewModel.uiState.first {
            it is ProfileUiState.Content && it.players.any { p -> p.name == "Tom" }
        }
        afterRetry as ProfileUiState.Content
        assertEquals(1, afterRetry.players.size)
        assertEquals("Tom", afterRetry.players.first().name)
    }
}
