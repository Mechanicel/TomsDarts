package com.mechanicel.tomsdarts.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.dao.PlayerDao
import com.mechanicel.tomsdarts.data.entity.Player
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Happy-Path-Test fuer den Room-Stack: In-Memory-DB bauen, einen [Player]
 * einfuegen und ueber [PlayerDao.getAll]/[PlayerDao.getById] zuruecklesen.
 *
 * Laeuft host-seitig (JVM) unter Robolectric, damit der Test im `./gradlew test`
 * Gate ohne Geraet/Emulator ausgefuehrt wird. Die Test-SDK ist auf 34 gepinnt,
 * da Robolectric die compileSdk (36) noch nicht unterstuetzt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerDaoTest {

    private lateinit var db: TomsDartsDatabase
    private lateinit var dao: PlayerDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TomsDartsDatabase::class.java,
        ).build()
        dao = db.playerDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndReadBack() = runBlocking {
        val id = dao.insert(Player(name = "Tom", createdAt = 1_000L))

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("Tom", all.first().name)
        assertEquals(1_000L, all.first().createdAt)
        assertEquals(id, all.first().id)

        val byId = dao.getById(id)
        assertNotNull(byId)
        assertEquals("Tom", byId!!.name)
    }
}
