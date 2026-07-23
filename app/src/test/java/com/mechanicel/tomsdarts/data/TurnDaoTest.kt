package com.mechanicel.tomsdarts.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.dao.TurnDao
import com.mechanicel.tomsdarts.data.entity.Leg
import com.mechanicel.tomsdarts.data.entity.Match
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.entity.Throw
import com.mechanicel.tomsdarts.data.entity.Turn
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
 * Edge-Case-/Fehlerpfad-/Regressionstests fuer [TurnDao].
 *
 * Setup analog zu [PlayerDaoTest]: host-seitig (JVM) unter Robolectric mit
 * In-Memory-Room (FK-Enforcement standardmaessig an), Test-SDK auf 34 gepinnt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TurnDaoTest {

    private lateinit var db: TomsDartsDatabase
    private lateinit var dao: TurnDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TomsDartsDatabase::class.java,
        ).build()
        dao = db.turnDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private suspend fun seedLeg(): Pair<Long, Long> {
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
        return legId to playerId
    }

    // --- Feld-Persistenz inkl. Randwerte ------------------------------------

    @Test
    fun persistsTurnFields() = runBlocking {
        val (legId, playerId) = seedLeg()
        val id = dao.insert(
            Turn(
                legId = legId,
                playerId = playerId,
                turnIndex = 5,
                bust = true,
                totalScored = 0,
            ),
        )

        val turn = dao.getById(id)!!
        assertEquals(legId, turn.legId)
        assertEquals(playerId, turn.playerId)
        assertEquals(5, turn.turnIndex)
        assertTrue("bust muss true bleiben", turn.bust)
        assertEquals(0, turn.totalScored)
    }

    @Test
    fun persistsNonBustMaxScore() = runBlocking {
        val (legId, playerId) = seedLeg()
        // 180 = maximale Aufnahme (3x Triple 20).
        val id = dao.insert(
            Turn(legId = legId, playerId = playerId, turnIndex = 0, bust = false, totalScored = 180),
        )

        val turn = dao.getById(id)!!
        assertFalse(turn.bust)
        assertEquals(180, turn.totalScored)
    }

    // --- getByLeg ------------------------------------------------------------

    @Test
    fun getByLegReturnsEmptyForLegWithoutTurns() = runBlocking {
        val (legId, _) = seedLeg()

        assertTrue(dao.getByLeg(legId).isEmpty())
    }

    @Test
    fun getByLegReturnsEmptyForUnknownLeg() = runBlocking {
        assertTrue(dao.getByLeg(424242L).isEmpty())
    }

    @Test
    fun getByLegReturnsOnlyOwnTurnsOrderedByTurnIndex() = runBlocking {
        val (legA, playerId) = seedLeg()
        // Zweites Leg im selben Match anlegen.
        val legB = db.legDao().insert(
            Leg(matchId = db.matchDao().getAll().first().id, legNumber = 2, startedAt = 1L),
        )

        dao.insert(Turn(legId = legA, playerId = playerId, turnIndex = 2, bust = false, totalScored = 20))
        dao.insert(Turn(legId = legA, playerId = playerId, turnIndex = 0, bust = false, totalScored = 60))
        dao.insert(Turn(legId = legA, playerId = playerId, turnIndex = 1, bust = false, totalScored = 40))
        dao.insert(Turn(legId = legB, playerId = playerId, turnIndex = 0, bust = false, totalScored = 5))

        val turnsA = dao.getByLeg(legA)
        assertEquals(3, turnsA.size)
        assertEquals(listOf(0, 1, 2), turnsA.map { it.turnIndex })
        assertEquals(listOf(60, 40, 20), turnsA.map { it.totalScored })
        assertTrue("Nur Turns von legA", turnsA.all { it.legId == legA })

        assertEquals(1, dao.getByLeg(legB).size)
    }

    @Test
    fun getByIdReturnsNullForNonexistentId() = runBlocking {
        assertNull(dao.getById(999L))
    }

    // --- deleteById (inkl. CASCADE auf Throws) -------------------------------

    @Test
    fun deleteByIdRemovesTurnAndCascadesThrows() = runBlocking {
        val (legId, playerId) = seedLeg()
        val turnId = dao.insert(
            Turn(legId = legId, playerId = playerId, turnIndex = 0, bust = false, totalScored = 60),
        )
        // Drei Wuerfe an die Aufnahme haengen.
        repeat(3) { i ->
            db.throwDao().insert(
                Throw(
                    turnId = turnId,
                    dartIndex = i + 1,
                    segment = 20,
                    multiplier = 1,
                    value = 20,
                    timestamp = 1L,
                ),
            )
        }
        assertEquals(3, db.throwDao().getByTurn(turnId).size)

        dao.deleteById(turnId)

        // Turn ist weg und die Throws sind per FK-CASCADE mitgeloescht.
        assertNull(dao.getById(turnId))
        assertTrue(db.throwDao().getByTurn(turnId).isEmpty())
    }

    @Test
    fun deleteByIdIsNoOpForUnknownId() = runBlocking {
        val (legId, playerId) = seedLeg()
        val turnId = dao.insert(
            Turn(legId = legId, playerId = playerId, turnIndex = 0, bust = false, totalScored = 5),
        )

        // Unbekannte ID loeschen laesst die bestehende Aufnahme unberuehrt.
        dao.deleteById(999_999L)

        assertNotNull(dao.getById(turnId))
    }
}
