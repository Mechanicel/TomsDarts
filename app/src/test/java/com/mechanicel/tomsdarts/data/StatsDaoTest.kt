package com.mechanicel.tomsdarts.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.dao.StatsDao
import com.mechanicel.tomsdarts.data.entity.Leg
import com.mechanicel.tomsdarts.data.entity.Match
import com.mechanicel.tomsdarts.data.entity.MatchPlayer
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.entity.Throw
import com.mechanicel.tomsdarts.data.entity.Turn
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests fuer [StatsDao] (flache Analytics-Join-Queries, ADR-0034).
 *
 * Setup analog zu [ThrowDaoTest]: host-seitig unter Robolectric mit
 * In-Memory-Room (FK-Enforcement an), Test-SDK auf 34 gepinnt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsDaoTest {

    private lateinit var db: TomsDartsDatabase
    private lateinit var dao: StatsDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TomsDartsDatabase::class.java,
        ).build()
        dao = db.statsDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    // --- Seed-Helper ---------------------------------------------------------

    private suspend fun player(name: String): Long =
        db.playerDao().insert(Player(name = name, createdAt = 1L))

    private suspend fun match(
        modeType: String = "501",
        startScore: Int = 501,
        startedAt: Long,
        endedAt: Long? = null,
        winnerId: Long? = null,
        players: List<Long> = emptyList(),
    ): Long {
        val id = db.matchDao().insert(
            Match(
                modeType = modeType,
                startScore = startScore,
                doubleOut = true,
                legsToWin = 2,
                setsToWin = 1,
                startedAt = startedAt,
                endedAt = endedAt,
                winnerId = winnerId,
            ),
        )
        players.forEachIndexed { i, p ->
            db.matchPlayerDao().insert(MatchPlayer(matchId = id, playerId = p, position = i))
        }
        return id
    }

    private suspend fun leg(
        matchId: Long,
        legNumber: Int,
        setNumber: Int? = null,
        winnerId: Long? = null,
        endedAt: Long? = null,
    ): Long = db.legDao().insert(
        Leg(
            matchId = matchId,
            setNumber = setNumber,
            legNumber = legNumber,
            winnerId = winnerId,
            startedAt = 1L,
            endedAt = endedAt,
        ),
    )

    /** Legt eine Aufnahme mit den Darts (segment to multiplier) an. */
    private suspend fun turn(
        legId: Long,
        playerId: Long?,
        turnIndex: Int,
        darts: List<Pair<Int, Int>>,
        bust: Boolean = false,
    ): Long {
        val total = if (bust) 0 else darts.sumOf { it.first * it.second }
        val turnId = db.turnDao().insert(
            Turn(
                legId = legId,
                playerId = playerId,
                turnIndex = turnIndex,
                bust = bust,
                totalScored = total,
            ),
        )
        darts.forEachIndexed { i, (segment, multiplier) ->
            db.throwDao().insert(
                Throw(
                    turnId = turnId,
                    dartIndex = i + 1,
                    segment = segment,
                    multiplier = multiplier,
                    value = segment * multiplier,
                    timestamp = 100L + i,
                ),
            )
        }
        return turnId
    }

    // --- getThrowRowsForPlayer -----------------------------------------------

    @Test
    fun playerRowsJoinAllColumnsCorrectly() = runBlocking {
        val a = player("A")
        val m = match(startedAt = 10L, endedAt = 20L, winnerId = a, players = listOf(a))
        val l = leg(m, legNumber = 1, setNumber = 2, winnerId = a, endedAt = 19L)
        val t = turn(l, a, 0, listOf(20 to 3, 19 to 1, 25 to 2))

        val rows = dao.getThrowRowsForPlayer(a)

        assertEquals(3, rows.size)
        val r = rows.first()
        assertEquals(m, r.matchId)
        assertEquals("501", r.modeType)
        assertEquals(501, r.startScore)
        assertTrue(r.doubleOut)
        assertEquals(10L, r.matchStartedAt)
        assertEquals(20L, r.matchEndedAt)
        assertEquals(l, r.legId)
        assertEquals(2, r.setNumber)
        assertEquals(1, r.legNumber)
        assertEquals(a, r.legWinnerId)
        assertEquals(19L, r.legEndedAt)
        assertEquals(t, r.turnId)
        assertEquals(0, r.turnIndex)
        assertEquals(a, r.playerId)
        assertFalse(r.bust)
        assertEquals(129, r.totalScored)
        assertEquals(1, r.dartIndex)
        assertEquals(20, r.segment)
        assertEquals(3, r.multiplier)
        assertEquals(60, r.value)
        assertEquals(100L, r.timestamp)
        assertEquals(listOf(60, 19, 50), rows.map { it.value })
    }

    @Test
    fun playerRowsContainOnlyOwnTurns() = runBlocking {
        val a = player("A")
        val b = player("B")
        val m = match(startedAt = 1L, players = listOf(a, b))
        val l = leg(m, 1)
        turn(l, a, 0, listOf(20 to 1, 20 to 1, 20 to 1))
        turn(l, b, 1, listOf(1 to 1, 1 to 1, 1 to 1))
        turn(l, a, 2, listOf(5 to 1))

        val rowsA = dao.getThrowRowsForPlayer(a)
        assertEquals(4, rowsA.size)
        assertTrue(rowsA.all { it.playerId == a })
        assertEquals(listOf(0, 0, 0, 2), rowsA.map { it.turnIndex })

        val rowsB = dao.getThrowRowsForPlayer(b)
        assertEquals(3, rowsB.size)
        assertTrue(rowsB.all { it.playerId == b })
    }

    @Test
    fun playerRowsFilterByModeTypeAndNullMeansAll() = runBlocking {
        val a = player("A")
        val m501 = match(modeType = "501", startedAt = 1L)
        val mCricket = match(modeType = "cricket", startScore = 0, startedAt = 2L)
        turn(leg(m501, 1), a, 0, listOf(20 to 1))
        turn(leg(mCricket, 1), a, 0, listOf(20 to 3))

        assertEquals(listOf(m501), dao.getThrowRowsForPlayer(a, "501").map { it.matchId })
        assertEquals(
            listOf(mCricket),
            dao.getThrowRowsForPlayer(a, "cricket").map { it.matchId },
        )
        assertEquals(
            listOf(m501, mCricket),
            dao.getThrowRowsForPlayer(a, null).map { it.matchId },
        )
        assertEquals(2, dao.getThrowRowsForPlayer(a).size)
        assertTrue(dao.getThrowRowsForPlayer(a, "301").isEmpty())
    }

    @Test
    fun playerRowsSortedByMatchSetLegTurnDart() = runBlocking {
        val a = player("A")
        // Match 2 wird zuerst angelegt, startet aber spaeter -> muss hinten liegen.
        val late = match(startedAt = 50L)
        val early = match(startedAt = 10L)

        // Legs/Turns bewusst in "falscher" Reihenfolge eingefuegt.
        val lateLeg = leg(late, 1)
        turn(lateLeg, a, 0, listOf(1 to 1))
        val earlySet2Leg1 = leg(early, legNumber = 1, setNumber = 2)
        val earlySet1Leg2 = leg(early, legNumber = 2, setNumber = 1)
        val earlySet1Leg1 = leg(early, legNumber = 1, setNumber = 1)
        turn(earlySet2Leg1, a, 0, listOf(4 to 1))
        turn(earlySet1Leg2, a, 2, listOf(3 to 1, 3 to 2))
        turn(earlySet1Leg2, a, 0, listOf(2 to 1))
        turn(earlySet1Leg1, a, 0, listOf(1 to 1))

        val rows = dao.getThrowRowsForPlayer(a)

        assertEquals(
            listOf(earlySet1Leg1, earlySet1Leg2, earlySet1Leg2, earlySet1Leg2, earlySet2Leg1, lateLeg),
            rows.map { it.legId },
        )
        assertEquals(listOf(0, 0, 2, 2, 0, 0), rows.map { it.turnIndex })
        assertEquals(listOf(1, 1, 1, 2, 1, 1), rows.map { it.dartIndex })
        assertEquals(listOf(early, early, early, early, early, late), rows.map { it.matchId })
    }

    @Test
    fun playerRowsIncludeBustTurnsWithAllThrownDarts() = runBlocking {
        val a = player("A")
        val m = match(startedAt = 1L)
        val l = leg(m, 1)
        turn(l, a, 0, listOf(20 to 3, 20 to 3), bust = true)

        val rows = dao.getThrowRowsForPlayer(a)

        assertEquals(2, rows.size)
        assertTrue(rows.all { it.bust })
        assertTrue(rows.all { it.totalScored == 0 })
        assertEquals(listOf(60, 60), rows.map { it.value })
    }

    @Test
    fun turnWithoutThrowsAppearsAsSingleRowWithNullDartFields() = runBlocking {
        val a = player("A")
        val l = leg(match(startedAt = 1L), 1)
        turn(l, a, 0, emptyList())

        val rows = dao.getThrowRowsForPlayer(a)

        assertEquals(1, rows.size)
        assertNull(rows[0].dartIndex)
        assertNull(rows[0].segment)
        assertNull(rows[0].multiplier)
        assertNull(rows[0].value)
        assertNull(rows[0].timestamp)
    }

    @Test
    fun deletedPlayerDropsOutOfPlayerQueryButStaysInMatchQuery() = runBlocking {
        val a = player("A")
        val b = player("B")
        val m = match(startedAt = 1L, players = listOf(a, b))
        val l = leg(m, 1)
        turn(l, a, 0, listOf(20 to 1))
        turn(l, b, 1, listOf(19 to 1))

        db.playerDao().delete(db.playerDao().getById(a)!!)

        assertTrue(dao.getThrowRowsForPlayer(a).isEmpty())
        val matchRows = dao.getThrowRowsForMatch(m)
        assertEquals(2, matchRows.size)
        assertNull(matchRows[0].playerId)
        assertEquals(b, matchRows[1].playerId)
    }

    @Test
    fun playerRowsEmptyForUnknownPlayer() = runBlocking {
        assertTrue(dao.getThrowRowsForPlayer(4711L).isEmpty())
    }

    // --- getThrowRowsForMatch ------------------------------------------------

    @Test
    fun matchRowsContainAllPlayersOfOnlyThatMatchSorted() = runBlocking {
        val a = player("A")
        val b = player("B")
        val m = match(startedAt = 1L, players = listOf(a, b))
        val other = match(startedAt = 2L, players = listOf(a))
        val leg2 = leg(m, 2)
        val leg1 = leg(m, 1)
        turn(leg2, b, 0, listOf(5 to 1))
        turn(leg1, b, 1, listOf(3 to 1, 3 to 1))
        turn(leg1, a, 0, listOf(1 to 1))
        turn(leg(other, 1), a, 0, listOf(20 to 1))

        val rows = dao.getThrowRowsForMatch(m)

        assertTrue(rows.all { it.matchId == m })
        assertEquals(listOf(leg1, leg1, leg1, leg2), rows.map { it.legId })
        assertEquals(listOf(a, b, b, b), rows.map { it.playerId })
        assertEquals(listOf(0, 1, 1, 0), rows.map { it.turnIndex })
    }

    @Test
    fun matchRowsEmptyForMatchWithoutTurns() = runBlocking {
        val m = match(startedAt = 1L)
        leg(m, 1)

        assertTrue(dao.getThrowRowsForMatch(m).isEmpty())
        assertTrue(dao.getThrowRowsForMatch(999L).isEmpty())
    }

    // --- getMatchesForPlayer -------------------------------------------------

    @Test
    fun matchesForPlayerDescendingByDateOnlyOwnMatches() = runBlocking {
        val a = player("A")
        val b = player("B")
        val old = match(modeType = "301", startScore = 301, startedAt = 10L, endedAt = 11L, winnerId = a, players = listOf(a, b))
        val new = match(modeType = "cricket", startScore = 0, startedAt = 30L, players = listOf(a))
        val mid = match(startedAt = 20L, players = listOf(a, b))
        val foreign = match(startedAt = 40L, players = listOf(b))

        val list = dao.getMatchesForPlayer(a)

        assertEquals(listOf(new, mid, old), list.map { it.id })
        val oldRow = list.last()
        assertEquals("301", oldRow.modeType)
        assertEquals(10L, oldRow.startedAt)
        assertEquals(11L, oldRow.endedAt)
        assertEquals(a, oldRow.winnerId)
        assertNull(list.first().endedAt)
        assertFalse(list.any { it.id == foreign })
    }

    @Test
    fun matchesForPlayerDistinctAndTieBrokenByIdDesc() = runBlocking {
        val a = player("A")
        val first = match(startedAt = 5L, players = listOf(a))
        // Doppelte Teilnahme-Zeile darf kein Duplikat erzeugen.
        db.matchPlayerDao().insert(MatchPlayer(matchId = first, playerId = a, position = 1))
        val second = match(startedAt = 5L, players = listOf(a))

        assertEquals(listOf(second, first), dao.getMatchesForPlayer(a).map { it.id })
        assertTrue(dao.getMatchesForPlayer(999L).isEmpty())
    }
}
