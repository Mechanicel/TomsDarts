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
 * Smoke-Test fuer [PlayerRepository]: In-Memory-Room-DB, Repository ueber
 * [TomsDartsDatabase.playerDao]. Deckt den Happy Path von add/get/observe/
 * update/delete ab. Laeuft host-seitig unter Robolectric (SDK 34 gepinnt).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerRepositoryTest {

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
    fun addGetObserveUpdateDelete() = runBlocking {
        val id = repository.addPlayer("Tom")

        val byId = repository.getPlayer(id)
        assertNotNull(byId)
        assertEquals("Tom", byId!!.name)

        val observed = repository.observePlayers().first()
        assertEquals(1, observed.size)
        assertEquals("Tom", observed.first().name)
        assertEquals(id, observed.first().id)

        repository.updatePlayer(byId.copy(name = "Thomas"))
        assertEquals("Thomas", repository.getPlayer(id)!!.name)

        repository.deletePlayer(repository.getPlayer(id)!!)
        assertNull(repository.getPlayer(id))
        assertTrue(repository.observePlayers().first().isEmpty())
    }
}
