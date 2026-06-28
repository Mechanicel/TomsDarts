package com.mechanicel.tomsdarts.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.dao.ThrowDao
import com.mechanicel.tomsdarts.data.entity.Leg
import com.mechanicel.tomsdarts.data.entity.Match
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.entity.Throw
import com.mechanicel.tomsdarts.data.entity.Turn
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
 * Edge-Case-/Fehlerpfad-/Regressionstests fuer [ThrowDao].
 *
 * Setup analog zu [PlayerDaoTest]: host-seitig (JVM) unter Robolectric mit
 * In-Memory-Room (FK-Enforcement standardmaessig an), Test-SDK auf 34 gepinnt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThrowDaoTest {

    private lateinit var db: TomsDartsDatabase
    private lateinit var dao: ThrowDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TomsDartsDatabase::class.java,
        ).build()
        dao = db.throwDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private suspend fun seedTurn(): Long {
        val playerId = db.playerDao().insert(Player(name = "P", createdAt = 1L))
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
        val legId = db.legDao().insert(Leg(matchId = matchId, legNumber = 1, startedAt = 1L))
        return db.turnDao().insert(
            Turn(legId = legId, playerId = playerId, turnIndex = 0, bust = false, totalScored = 0),
        )
    }

    // --- Feld-Persistenz / Randwerte ----------------------------------------

    @Test
    fun persistsMissThrowSegmentZero() = runBlocking {
        val turnId = seedTurn()
        // segment 0 = daneben/Out, value 0.
        val id = dao.insert(
            Throw(
                turnId = turnId,
                dartIndex = 0,
                segment = 0,
                multiplier = 1,
                value = 0,
                timestamp = 1L,
            ),
        )

        val t = dao.getById(id)!!
        assertEquals(0, t.segment)
        assertEquals(1, t.multiplier)
        assertEquals(0, t.value)
    }

    @Test
    fun persistsBullSegment25() = runBlocking {
        val turnId = seedTurn()
        // segment 25 = Bull, Double-Bull = 50.
        val id = dao.insert(
            Throw(
                turnId = turnId,
                dartIndex = 1,
                segment = 25,
                multiplier = 2,
                value = 50,
                timestamp = 1L,
            ),
        )

        val t = dao.getById(id)!!
        assertEquals(25, t.segment)
        assertEquals(2, t.multiplier)
        assertEquals(50, t.value)
    }

    @Test
    fun persistsAllMultipliersAndLargeTimestamp() = runBlocking {
        val turnId = seedTurn()
        // multiplier 1/2/3 ueber drei Wuerfe; grosser Timestamp.
        val single = dao.insert(throwOf(turnId, 0, 20, 1, 20, 1L))
        val double = dao.insert(throwOf(turnId, 1, 20, 2, 40, 2L))
        val triple = dao.insert(throwOf(turnId, 2, 20, 3, 60, Long.MAX_VALUE))

        assertEquals(1, dao.getById(single)!!.multiplier)
        assertEquals(2, dao.getById(double)!!.multiplier)
        assertEquals(3, dao.getById(triple)!!.multiplier)
        assertEquals(Long.MAX_VALUE, dao.getById(triple)!!.timestamp)
    }

    // --- getByTurn -----------------------------------------------------------

    @Test
    fun getByTurnReturnsEmptyForTurnWithoutThrows() = runBlocking {
        val turnId = seedTurn()

        assertTrue(dao.getByTurn(turnId).isEmpty())
    }

    @Test
    fun getByTurnReturnsEmptyForUnknownTurn() = runBlocking {
        assertTrue(dao.getByTurn(98765L).isEmpty())
    }

    @Test
    fun getByTurnReturnsOnlyOwnThrowsOrderedByDartIndex() = runBlocking {
        val turnA = seedTurn()
        val turnB = seedTurn()

        dao.insert(throwOf(turnA, 2, 5, 1, 5, 1L))
        dao.insert(throwOf(turnA, 0, 20, 3, 60, 1L))
        dao.insert(throwOf(turnA, 1, 19, 1, 19, 1L))
        dao.insert(throwOf(turnB, 0, 1, 1, 1, 1L))

        val throwsA = dao.getByTurn(turnA)
        assertEquals(3, throwsA.size)
        assertEquals(listOf(0, 1, 2), throwsA.map { it.dartIndex })
        assertEquals(listOf(60, 19, 5), throwsA.map { it.value })
        assertTrue("Nur Throws von turnA", throwsA.all { it.turnId == turnA })

        assertEquals(1, dao.getByTurn(turnB).size)
    }

    @Test
    fun getByIdReturnsNullForNonexistentId() = runBlocking {
        assertNull(dao.getById(999L))
    }

    /** Positionaler Helper: [Throw] hat id als ersten (Default-)Parameter. */
    private fun throwOf(
        turnId: Long,
        dartIndex: Int,
        segment: Int,
        multiplier: Int,
        value: Int,
        timestamp: Long,
    ) = Throw(
        turnId = turnId,
        dartIndex = dartIndex,
        segment = segment,
        multiplier = multiplier,
        value = value,
        timestamp = timestamp,
    )
}
