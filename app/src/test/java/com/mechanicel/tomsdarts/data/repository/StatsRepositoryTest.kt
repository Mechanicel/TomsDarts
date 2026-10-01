package com.mechanicel.tomsdarts.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.analytics.AnalyticsDart
import com.mechanicel.tomsdarts.data.TomsDartsDatabase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests fuer [StatsRepository]: DAO-Durchreichen + Mapping auf das
 * Analytics-Domaenenmodell end-to-end ueber In-Memory-Room (ADR-0034).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsRepositoryTest {

    private lateinit var db: TomsDartsDatabase
    private lateinit var repo: StatsRepository

    private var a = 0L
    private var b = 0L
    private var x01Match = 0L
    private var cricketMatch = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TomsDartsDatabase::class.java,
        ).build()
        repo = StatsRepository(db.statsDao())

        a = db.playerDao().insert(Player(name = "A", createdAt = 1L))
        b = db.playerDao().insert(Player(name = "B", createdAt = 1L))

        x01Match = insertMatch("501", startedAt = 10L)
        val leg1 = db.legDao().insert(Leg(matchId = x01Match, legNumber = 1, winnerId = a, startedAt = 10L, endedAt = 11L))
        val leg2 = db.legDao().insert(Leg(matchId = x01Match, legNumber = 2, startedAt = 12L))
        insertTurn(leg1, a, 0, listOf(20 to 3, 20 to 3, 20 to 3))
        insertTurn(leg1, b, 1, listOf(1 to 1))
        insertTurn(leg2, b, 0, listOf(5 to 1))
        insertTurn(leg2, a, 1, listOf(19 to 3, 19 to 3), bust = true)

        cricketMatch = insertMatch("cricket", startedAt = 20L)
        val cLeg = db.legDao().insert(Leg(matchId = cricketMatch, legNumber = 1, startedAt = 20L))
        insertTurn(cLeg, a, 0, listOf(20 to 3))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertMatch(modeType: String, startedAt: Long): Long {
        val id = db.matchDao().insert(
            Match(
                modeType = modeType,
                startScore = if (modeType == "501") 501 else 0,
                doubleOut = modeType == "501",
                legsToWin = 2,
                setsToWin = 1,
                startedAt = startedAt,
            ),
        )
        db.matchPlayerDao().insert(MatchPlayer(matchId = id, playerId = a, position = 0))
        db.matchPlayerDao().insert(MatchPlayer(matchId = id, playerId = b, position = 1))
        return id
    }

    private suspend fun insertTurn(
        legId: Long,
        playerId: Long,
        turnIndex: Int,
        darts: List<Pair<Int, Int>>,
        bust: Boolean = false,
    ) {
        val turnId = db.turnDao().insert(
            Turn(
                legId = legId,
                playerId = playerId,
                turnIndex = turnIndex,
                bust = bust,
                totalScored = if (bust) 0 else darts.sumOf { it.first * it.second },
            ),
        )
        darts.forEachIndexed { i, (s, m) ->
            db.throwDao().insert(
                Throw(turnId = turnId, dartIndex = i + 1, segment = s, multiplier = m, value = s * m, timestamp = 1L),
            )
        }
    }

    @Test
    fun legsForPlayerReturnsOnlyOwnVisitsAcrossModes() = runBlocking {
        val legs = repo.legsForPlayer(a)

        assertEquals(3, legs.size)
        assertEquals(listOf(x01Match, x01Match, cricketMatch), legs.map { it.matchId })
        assertTrue(legs.flatMap { it.visits }.all { it.playerId == a })

        val first = legs[0]
        assertTrue(first.finished)
        assertEquals(a, first.winnerId)
        assertEquals(180, first.visits.single().totalScored)
        assertEquals(
            listOf(AnalyticsDart(1, 20, 3, 60), AnalyticsDart(2, 20, 3, 60), AnalyticsDart(3, 20, 3, 60)),
            first.visits.single().darts,
        )

        val bustVisit = legs[1].visits.single()
        assertFalse(legs[1].finished)
        assertTrue(bustVisit.bust)
        assertEquals(2, bustVisit.darts.size)
    }

    @Test
    fun legsForPlayerFiltersByModeType() = runBlocking {
        val x01 = repo.legsForPlayer(a, "501")
        assertEquals(2, x01.size)
        assertTrue(x01.all { it.modeType == "501" && it.startScore == 501 && it.doubleOut })

        val cricket = repo.legsForPlayer(a, modeType = "cricket")
        assertEquals(listOf(cricketMatch), cricket.map { it.matchId })

        assertTrue(repo.legsForPlayer(a, "301").isEmpty())
    }

    @Test
    fun legsForMatchContainsAllPlayersInTurnOrder() = runBlocking {
        val legs = repo.legsForMatch(x01Match)

        assertEquals(listOf(1, 2), legs.map { it.legNumber })
        assertEquals(listOf(a, b), legs[0].visits.map { it.playerId })
        assertEquals(listOf(b, a), legs[1].visits.map { it.playerId })
        assertEquals(listOf(0, 1), legs[1].visits.map { it.turnIndex })
    }

    @Test
    fun matchesForPlayerNewestFirst() = runBlocking {
        val matches = repo.matchesForPlayer(a)

        assertEquals(listOf(cricketMatch, x01Match), matches.map { it.matchId })
        assertEquals(listOf("cricket", "501"), matches.map { it.modeType })
        assertTrue(repo.matchesForPlayer(999L).isEmpty())
    }
}
