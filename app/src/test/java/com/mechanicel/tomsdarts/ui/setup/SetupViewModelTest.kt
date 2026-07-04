package com.mechanicel.tomsdarts.ui.setup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import com.mechanicel.tomsdarts.data.repository.PlayerRepository
import com.mechanicel.tomsdarts.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Happy-Path-Test fuer [SetupViewModel]: In-Memory-Room-DB ueber das echte
 * [PlayerRepository]. Prueft die Namensaufloesung zu den uebergebenen
 * playerIds (inkl. Erhalt der Eingangsreihenfolge) sowie die Verkabelung der
 * Reorder-/Remove-Aktionen an die geordnete Teilnehmerliste. Edge-Cases deckt
 * das Test-Gate ab.
 *
 * Laeuft host-seitig unter Robolectric (SDK 34 gepinnt).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SetupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var db: TomsDartsDatabase
    private lateinit var repository: PlayerRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TomsDartsDatabase::class.java,
        ).build()
        repository = PlayerRepository(db.playerDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun aufloesung_liefertNamenInEingangsReihenfolge() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tomId = repository.addPlayer("Tom")
            val annaId = repository.addPlayer("Anna")
            val bjoernId = repository.addPlayer("Bjoern")

            // Bewusst eine andere Reihenfolge als die Einfuege-Reihenfolge, um
            // zu pruefen, dass die uebergebene Eingangsreihenfolge erhalten bleibt.
            val viewModel = SetupViewModel(repository, listOf(bjoernId, tomId, annaId))

            val resolved = viewModel.participants.first { it.isNotEmpty() }
            assertEquals(
                listOf(
                    SetupPlayer(bjoernId, "Bjoern"),
                    SetupPlayer(tomId, "Tom"),
                    SetupPlayer(annaId, "Anna"),
                ),
                resolved,
            )
            assertEquals(listOf(bjoernId, tomId, annaId), viewModel.orderedPlayerIds())
        }

    @Test
    fun movePlayerUp_aktualisiertGeordneteIdListe() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tomId = repository.addPlayer("Tom")
            val annaId = repository.addPlayer("Anna")
            val bjoernId = repository.addPlayer("Bjoern")
            val viewModel = SetupViewModel(repository, listOf(tomId, annaId, bjoernId))
            viewModel.participants.first { it.isNotEmpty() }

            viewModel.movePlayerUp(1)

            assertEquals(listOf(annaId, tomId, bjoernId), viewModel.orderedPlayerIds())
        }

    @Test
    fun removePlayer_entferntUndReduziertIdListe() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tomId = repository.addPlayer("Tom")
            val annaId = repository.addPlayer("Anna")
            val bjoernId = repository.addPlayer("Bjoern")
            val viewModel = SetupViewModel(repository, listOf(tomId, annaId, bjoernId))
            viewModel.participants.first { it.isNotEmpty() }

            viewModel.removePlayer(1)

            assertEquals(listOf(tomId, bjoernId), viewModel.orderedPlayerIds())
        }

    @Test
    fun removePlayer_beiMinimumBleibtUnveraendert() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val tomId = repository.addPlayer("Tom")
            val annaId = repository.addPlayer("Anna")
            val viewModel = SetupViewModel(repository, listOf(tomId, annaId))
            viewModel.participants.first { it.isNotEmpty() }

            viewModel.removePlayer(0)

            assertEquals(listOf(tomId, annaId), viewModel.orderedPlayerIds())
        }
}
