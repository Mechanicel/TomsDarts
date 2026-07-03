package com.mechanicel.tomsdarts.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.dao.MatchDao
import com.mechanicel.tomsdarts.data.entity.Match
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Edge-Case-/Fehlerpfad-/Regressionstests fuer [MatchDao].
 *
 * Setup analog zu [PlayerDaoTest]: host-seitig (JVM) unter Robolectric mit
 * In-Memory-Room (FK-Enforcement standardmaessig an), Test-SDK auf 34 gepinnt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MatchDaoTest {

    private lateinit var db: TomsDartsDatabase
    private lateinit var dao: MatchDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TomsDartsDatabase::class.java,
        ).build()
        dao = db.matchDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    // --- Feld-Persistenz inkl. nullable-Felder ------------------------------

    @Test
    fun persistsAllFieldsForRunningMatch() = runBlocking {
        // endedAt/winnerId null -> laufendes Match ohne Gewinner.
        val id = dao.insert(
            Match(
                modeType = "501",
                startScore = 501,
                doubleOut = true,
                legsToWin = 3,
                setsToWin = 2,
                startedAt = 1_234L,
                endedAt = null,
                winnerId = null,
            ),
        )

        val m = dao.getById(id)!!
        assertEquals("501", m.modeType)
        assertEquals(501, m.startScore)
        assertTrue(m.doubleOut)
        assertEquals(3, m.legsToWin)
        assertEquals(2, m.setsToWin)
        assertEquals(1_234L, m.startedAt)
        assertNull("endedAt muss null bleiben", m.endedAt)
        assertNull("winnerId muss null bleiben", m.winnerId)
    }

    @Test
    fun persistsEndedAtAndWinnerWhenSet() = runBlocking {
        val winnerId = db.playerDao().insert(
            com.mechanicel.tomsdarts.data.entity.Player(name = "Tom", createdAt = 1L),
        )
        val id = dao.insert(
            Match(
                modeType = "301",
                startScore = 301,
                doubleOut = false,
                legsToWin = 1,
                setsToWin = 1,
                startedAt = 10L,
                endedAt = 99L,
                winnerId = winnerId,
            ),
        )

        val m = dao.getById(id)!!
        assertFalse(m.doubleOut)
        assertEquals(99L, m.endedAt)
        assertEquals(winnerId, m.winnerId)
    }

    @Test
    fun persistsLargeTimestamps() = runBlocking {
        val id = dao.insert(
            Match(
                modeType = "cricket",
                startScore = 0,
                doubleOut = false,
                legsToWin = 1,
                setsToWin = 1,
                startedAt = Long.MAX_VALUE,
                endedAt = Long.MAX_VALUE,
            ),
        )

        val m = dao.getById(id)!!
        assertEquals(Long.MAX_VALUE, m.startedAt)
        assertEquals(Long.MAX_VALUE, m.endedAt)
        assertEquals(0, m.startScore)
    }

    // --- getAll / getById ----------------------------------------------------

    @Test
    fun getAllReturnsEmptyListOnEmptyDb() = runBlocking {
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun getByIdReturnsNullForNonexistentId() = runBlocking {
        assertNull(dao.getById(777L))
    }

    @Test
    fun getAllReturnsAllMatchesOrderedById() = runBlocking {
        val id1 = dao.insert(newMatch("501"))
        val id2 = dao.insert(newMatch("301"))
        val id3 = dao.insert(newMatch("cricket"))

        val all = dao.getAll()
        assertEquals(listOf(id1, id2, id3), all.map { it.id })
        assertEquals(listOf("501", "301", "cricket"), all.map { it.modeType })
    }

    // --- delete --------------------------------------------------------------

    @Test
    fun deleteRemovesOnlyTargetMatch() = runBlocking {
        val keep = dao.insert(newMatch("keep"))
        val drop = dao.insert(newMatch("drop"))

        dao.delete(dao.getById(drop)!!)

        assertNull(dao.getById(drop))
        assertNotNull(dao.getById(keep))
        assertEquals(1, dao.getAll().size)
    }

    private fun newMatch(mode: String) = Match(
        modeType = mode,
        startScore = 501,
        doubleOut = true,
        legsToWin = 1,
        setsToWin = 1,
        startedAt = 1L,
    )
}
