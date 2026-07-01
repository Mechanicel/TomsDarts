package com.mechanicel.tomsdarts.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.dao.LegDao
import com.mechanicel.tomsdarts.data.dao.MatchDao
import com.mechanicel.tomsdarts.data.entity.Leg
import com.mechanicel.tomsdarts.data.entity.Match
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
 * Edge-Case-/Fehlerpfad-/Regressionstests fuer [LegDao].
 *
 * Setup analog zu [PlayerDaoTest]: host-seitig (JVM) unter Robolectric mit
 * In-Memory-Room (FK-Enforcement standardmaessig an), Test-SDK auf 34 gepinnt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegDaoTest {

    private lateinit var db: TomsDartsDatabase
    private lateinit var dao: LegDao
    private lateinit var matchDao: MatchDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TomsDartsDatabase::class.java,
        ).build()
        dao = db.legDao()
        matchDao = db.matchDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private suspend fun newMatch() = matchDao.insert(
        Match(
            modeType = "501",
            startScore = 501,
            doubleOut = true,
            legsToWin = 1,
            setsToWin = 1,
            startedAt = 1L,
        ),
    )

    // --- Feld-Persistenz inkl. nullable-Felder ------------------------------

    @Test
    fun persistsLegWithoutSetsRunning() = runBlocking {
        val matchId = newMatch()
        // setNumber/winnerId/endedAt null -> ohne Sets, laufend, kein Gewinner.
        val id = dao.insert(
            Leg(
                matchId = matchId,
                setNumber = null,
                legNumber = 1,
                winnerId = null,
                startedAt = 500L,
                endedAt = null,
            ),
        )

        val leg = dao.getById(id)!!
        assertEquals(matchId, leg.matchId)
        assertNull("setNumber muss null bleiben", leg.setNumber)
        assertEquals(1, leg.legNumber)
        assertNull("winnerId muss null bleiben", leg.winnerId)
        assertEquals(500L, leg.startedAt)
        assertNull("endedAt muss null bleiben", leg.endedAt)
    }

    @Test
    fun persistsLegWithSetNumberAndWinnerAndEnd() = runBlocking {
        val matchId = newMatch()
        val winnerId = db.playerDao().insert(
            com.mechanicel.tomsdarts.data.entity.Player(name = "W", createdAt = 1L),
        )
        val id = dao.insert(
            Leg(
                matchId = matchId,
                setNumber = 2,
                legNumber = 3,
                winnerId = winnerId,
                startedAt = 10L,
                endedAt = 20L,
            ),
        )

        val leg = dao.getById(id)!!
        assertEquals(2, leg.setNumber)
        assertEquals(3, leg.legNumber)
        assertEquals(winnerId, leg.winnerId)
        assertEquals(20L, leg.endedAt)
    }

    // --- getByMatch ----------------------------------------------------------

    @Test
    fun getByMatchReturnsEmptyForMatchWithoutLegs() = runBlocking {
        val matchId = newMatch()

        assertTrue(dao.getByMatch(matchId).isEmpty())
    }

    @Test
    fun getByMatchReturnsEmptyForUnknownMatch() = runBlocking {
        assertTrue(dao.getByMatch(12345L).isEmpty())
    }

    @Test
    fun getByMatchReturnsOnlyOwnLegsOrderedByLegNumber() = runBlocking {
        val matchA = newMatch()
        val matchB = newMatch()

        // In gemischter Reihenfolge einfuegen, Query sortiert nach legNumber.
        dao.insert(Leg(matchId = matchA, legNumber = 3, startedAt = 1L))
        dao.insert(Leg(matchId = matchA, legNumber = 1, startedAt = 1L))
        dao.insert(Leg(matchId = matchA, legNumber = 2, startedAt = 1L))
        dao.insert(Leg(matchId = matchB, legNumber = 1, startedAt = 1L))

        val legsA = dao.getByMatch(matchA)
        assertEquals(3, legsA.size)
        assertEquals(listOf(1, 2, 3), legsA.map { it.legNumber })
        assertTrue("Nur Legs von matchA", legsA.all { it.matchId == matchA })

        val legsB = dao.getByMatch(matchB)
        assertEquals(1, legsB.size)
        assertEquals(matchB, legsB.first().matchId)
    }

    @Test
    fun getByIdReturnsNullForNonexistentId() = runBlocking {
        assertNull(dao.getById(999L))
    }
}
