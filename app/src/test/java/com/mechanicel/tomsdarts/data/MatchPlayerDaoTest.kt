package com.mechanicel.tomsdarts.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.dao.MatchPlayerDao
import com.mechanicel.tomsdarts.data.entity.Match
import com.mechanicel.tomsdarts.data.entity.MatchPlayer
import com.mechanicel.tomsdarts.data.entity.Player
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Edge-Case-/Fehlerpfad-/Regressionstests fuer [MatchPlayerDao].
 *
 * Setup analog zu [PlayerDaoTest]: host-seitig (JVM) unter Robolectric mit
 * In-Memory-Room (FK-Enforcement standardmaessig an), Test-SDK auf 34 gepinnt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MatchPlayerDaoTest {

    private lateinit var db: TomsDartsDatabase
    private lateinit var dao: MatchPlayerDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TomsDartsDatabase::class.java,
        ).build()
        dao = db.matchPlayerDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private suspend fun newMatch() = db.matchDao().insert(
        Match(
            modeType = "501",
            startScore = 501,
            doubleOut = true,
            legsToWin = 1,
            setsToWin = 1,
            startedAt = 1L,
        ),
    )

    private suspend fun newPlayer(name: String) =
        db.playerDao().insert(Player(name = name, createdAt = 1L))

    // --- Feld-Persistenz -----------------------------------------------------

    @Test
    fun persistsCrossRefFields() = runBlocking {
        val matchId = newMatch()
        val playerId = newPlayer("Tom")
        val id = dao.insert(MatchPlayer(matchId = matchId, playerId = playerId, position = 1))

        val mp = dao.getById(id)!!
        assertEquals(matchId, mp.matchId)
        assertEquals(playerId, mp.playerId)
        assertEquals(1, mp.position)
    }

    // --- getByMatch ----------------------------------------------------------

    @Test
    fun getByMatchReturnsEmptyForMatchWithoutPlayers() = runBlocking {
        val matchId = newMatch()

        assertTrue(dao.getByMatch(matchId).isEmpty())
    }

    @Test
    fun getByMatchReturnsEmptyForUnknownMatch() = runBlocking {
        assertTrue(dao.getByMatch(55555L).isEmpty())
    }

    @Test
    fun getByMatchOrdersByPositionAndExcludesOtherMatches() = runBlocking {
        val matchA = newMatch()
        val matchB = newMatch()
        val p1 = newPlayer("P1")
        val p2 = newPlayer("P2")
        val p3 = newPlayer("P3")

        // In nicht-sortierter Position-Reihenfolge einfuegen.
        dao.insert(MatchPlayer(matchId = matchA, playerId = p3, position = 2))
        dao.insert(MatchPlayer(matchId = matchA, playerId = p1, position = 0))
        dao.insert(MatchPlayer(matchId = matchA, playerId = p2, position = 1))
        // Anderes Match darf nicht in matchA-Resultat auftauchen.
        dao.insert(MatchPlayer(matchId = matchB, playerId = p1, position = 0))

        val playersA = dao.getByMatch(matchA)
        assertEquals(3, playersA.size)
        assertEquals("ORDER BY position", listOf(0, 1, 2), playersA.map { it.position })
        assertEquals(listOf(p1, p2, p3), playersA.map { it.playerId })
        assertTrue("Nur Eintraege von matchA", playersA.all { it.matchId == matchA })

        assertEquals(1, dao.getByMatch(matchB).size)
    }

    @Test
    fun getByIdReturnsNullForNonexistentId() = runBlocking {
        assertNull(dao.getById(999L))
    }
}
