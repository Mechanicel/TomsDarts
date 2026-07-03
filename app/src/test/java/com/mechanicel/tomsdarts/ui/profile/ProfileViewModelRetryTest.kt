package com.mechanicel.tomsdarts.ui.profile

import com.mechanicel.tomsdarts.data.dao.PlayerDao
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Deckt den ECHTEN Fehlerpfad von [ProfileViewModel] ab: wirft der
 * Spieler-Stream beim ersten Sammeln, muss [ProfileViewModel.uiState] in
 * [ProfileUiState.Error] gehen UND nach [ProfileViewModel.retry] aus dem
 * Fehler herausfinden (Recovery).
 *
 * Verwendet ein minimales Fake-[PlayerDao] (statt der echten Room-DB), dessen
 * `observeAll()` beim ersten Aufruf einen Fehler wirft und ab dem zweiten
 * Aufruf (nach `retry()`) einen normalen Stream liefert. So entsteht der Fehler
 * im inneren Flow und nicht erst im Test-Setup.
 *
 * Reiner Host-Test ohne Android-/Room-Abhaengigkeiten; nutzt wie die uebrigen
 * ViewModel-Tests die gemeinsame [MainDispatcherRule] und das
 * `backgroundScope`-Collector-Muster.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelRetryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /**
     * Minimales Fake-[PlayerDao]: `observeAll()` wirft beim ersten Aufruf,
     * danach liefert es [recovered]. Alle anderen Methoden werden im Test nicht
     * aufgerufen und bleiben daher bewusst unimplementiert.
     */
    private class FakePlayerDao(
        private val recovered: List<Player>,
    ) : PlayerDao {
        private var observeCalls = 0

        override fun observeAll(): Flow<List<Player>> {
            val call = observeCalls++
            return if (call == 0) {
                flow { throw RuntimeException("boom") }
            } else {
                flowOf(recovered)
            }
        }

        override suspend fun insert(player: Player): Long = TODO("not used in test")
        override suspend fun update(player: Player): Unit = TODO("not used in test")
        override suspend fun delete(player: Player): Unit = TODO("not used in test")
        override suspend fun getAll(): List<Player> = TODO("not used in test")
        override suspend fun getById(id: Long): Player? = TODO("not used in test")
    }

    @Test
    fun errorThenRetryRecoversToContent() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val recovered = listOf(Player(id = 1, name = "Tom", createdAt = 123L))
        val viewModel = ProfileViewModel(PlayerRepository(FakePlayerDao(recovered)))
        backgroundScope.launch { viewModel.uiState.collect {} }

        // Erster Stream wirft -> Error.
        val errorState = viewModel.uiState.first { it is ProfileUiState.Error }
        assertTrue(errorState is ProfileUiState.Error)

        // retry() startet einen frischen inneren Flow -> Recovery zu Content.
        viewModel.retry()

        val recoveredState = viewModel.uiState.first { it is ProfileUiState.Content }
        recoveredState as ProfileUiState.Content
        assertEquals(listOf("Tom"), recoveredState.players.map { it.name })
    }

    @Test
    fun errorThenRetryRecoversToEmpty() = runTest(mainDispatcherRule.testDispatcher.scheduler) {
        val viewModel = ProfileViewModel(PlayerRepository(FakePlayerDao(emptyList())))
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.uiState.first { it is ProfileUiState.Error }

        viewModel.retry()

        val recoveredState = viewModel.uiState.first { it is ProfileUiState.Empty }
        assertTrue(recoveredState is ProfileUiState.Empty)
    }
}
