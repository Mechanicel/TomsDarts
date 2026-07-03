package com.mechanicel.tomsdarts.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Edge-Case-Absicherung fuer [PlayerRepository] ueber den Happy-Path-Smoke-Test
 * hinaus: createdAt-Plausibilitaet, Sortierung von [PlayerRepository.observePlayers],
 * unbekannte IDs, selektives Loeschen, Reaktivitaet des Flows und Randwerte beim
 * Namen (leer, Whitespace, Unicode).
 *
 * Setup analog zu [PlayerRepositoryTest]: host-seitig unter Robolectric mit
 * In-Memory-Room (SDK 34 gepinnt), Repository ueber die DAOs der In-Memory-DB.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerRepositoryEdgeCaseTest {

    private lateinit var db: TomsDartsDatabase
    private lateinit var repository: PlayerRepository

    @Before
    fun createRepository() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TomsDartsDatabase::class.java,
        ).build()
        repository = PlayerRepository(db.playerDao())
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun addPlayerSetsPlausibleCreatedAtAndReturnsPositiveId() = runBlocking {
        val before = System.currentTimeMillis()
        val id = repository.addPlayer("Tom")
        val after = System.currentTimeMillis()

        assertTrue("ID muss positiv sein", id > 0)

        val player = repository.getPlayer(id)
        assertNotNull(player)
        assertTrue("createdAt muss > 0 sein", player!!.createdAt > 0)
        assertTrue(
            "createdAt muss im Zeitfenster des Inserts liegen",
            player.createdAt in before..after,
        )
    }

    @Test
    fun observePlayersReturnsAllSortedByName() = runBlocking {
        // Bewusst nicht alphabetisch eingefuegt, um die ORDER-BY-name-Sortierung zu pruefen.
        repository.addPlayer("Charlie")
        repository.addPlayer("Alice")
        repository.addPlayer("Bob")

        val names = repository.observePlayers().first().map { it.name }
        assertEquals(listOf("Alice", "Bob", "Charlie"), names)
    }

    @Test
    fun getPlayerForUnknownIdReturnsNull() = runBlocking {
        assertNull(repository.getPlayer(4711L))
    }

    @Test
    fun updatePlayerPersistsChangedFields() = runBlocking {
        val id = repository.addPlayer("Tom")
        val original = repository.getPlayer(id)!!

        repository.updatePlayer(original.copy(name = "Thomas", createdAt = 12345L))

        val reloaded = repository.getPlayer(id)!!
        assertEquals(id, reloaded.id)
        assertEquals("Thomas", reloaded.name)
        assertEquals(12345L, reloaded.createdAt)
    }

    @Test
    fun deletePlayerRemovesOnlyTarget() = runBlocking {
        val keepId = repository.addPlayer("Keep")
        val dropId = repository.addPlayer("Drop")

        repository.deletePlayer(repository.getPlayer(dropId)!!)

        assertNull(repository.getPlayer(dropId))
        assertNotNull(repository.getPlayer(keepId))
        val remaining = repository.observePlayers().first()
        assertEquals(1, remaining.size)
        assertEquals("Keep", remaining.first().name)
    }

    @Test
    fun observePlayersReflectsInsertAndDelete() = runBlocking {
        assertTrue(repository.observePlayers().first().isEmpty())

        val id = repository.addPlayer("Tom")
        assertEquals(1, repository.observePlayers().first().size)

        repository.deletePlayer(repository.getPlayer(id)!!)
        assertTrue(repository.observePlayers().first().isEmpty())
    }

    @Test
    fun addPlayerAcceptsEmptyName() = runBlocking {
        val id = repository.addPlayer("")
        assertTrue(id > 0)
        assertEquals("", repository.getPlayer(id)!!.name)
    }

    @Test
    fun addPlayerPreservesWhitespaceAndUnicodeName() = runBlocking {
        val whitespaceName = "  Tom  "
        val unicodeName = "Tøm 🎯 Джон" // mit Emoji + kyrillisch

        val wsId = repository.addPlayer(whitespaceName)
        val uniId = repository.addPlayer(unicodeName)

        assertEquals(whitespaceName, repository.getPlayer(wsId)!!.name)
        assertEquals(unicodeName, repository.getPlayer(uniId)!!.name)
    }
}
