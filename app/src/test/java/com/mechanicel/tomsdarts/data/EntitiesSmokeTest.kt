package com.mechanicel.tomsdarts.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mechanicel.tomsdarts.data.entity.Leg
import com.mechanicel.tomsdarts.data.entity.Match
import com.mechanicel.tomsdarts.data.entity.MatchPlayer
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.entity.Throw
import com.mechanicel.tomsdarts.data.entity.Turn
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke-Test fuer das relationale Schema: legt die gesamte Hierarchie
 * (Player -> Match -> MatchPlayer/Leg -> Turn -> Throw) in FK-konsistenter
 * Reihenfolge an, liest sie ueber die Beziehungs-Queries zurueck und prueft
 * eine CASCADE-Loeschung beim Entfernen eines Matches.
 *
 * Laeuft host-seitig (JVM) unter Robolectric. In-Memory-Room aktiviert
 * FK-Enforcement standardmaessig, daher greifen die Constraints im Test.
 * Die Test-SDK ist auf 34 gepinnt (Robolectric unterstuetzt compileSdk 36 noch nicht).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntitiesSmokeTest {

    private lateinit var db: TomsDartsDatabase

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TomsDartsDatabase::class.java,
        ).build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertHierarchyReadBackAndCascadeDelete() = runBlocking {
        val playerDao = db.playerDao()
        val matchDao = db.matchDao()
        val matchPlayerDao = db.matchPlayerDao()
        val legDao = db.legDao()
        val turnDao = db.turnDao()
        val throwDao = db.throwDao()

        // FK-konsistente Einfuege-Reihenfolge: Player -> Match -> MatchPlayer/Leg -> Turn -> Throw.
        val playerId = playerDao.insert(Player(name = "Tom", createdAt = 1_000L))

        val matchId = matchDao.insert(
            Match(
                modeType = "501",
                startScore = 501,
                doubleOut = true,
                legsToWin = 3,
                setsToWin = 1,
                startedAt = 2_000L,
                winnerId = playerId,
            ),
        )

        val matchPlayerId = matchPlayerDao.insert(
            MatchPlayer(matchId = matchId, playerId = playerId, position = 0),
        )

        val legId = legDao.insert(
            Leg(
                matchId = matchId,
                legNumber = 1,
                winnerId = playerId,
                startedAt = 3_000L,
            ),
        )

        val turnId = turnDao.insert(
            Turn(
                legId = legId,
                playerId = playerId,
                turnIndex = 0,
                bust = false,
                totalScored = 60,
            ),
        )

        val throwId = throwDao.insert(
            Throw(
                turnId = turnId,
                dartIndex = 0,
                segment = 20,
                multiplier = 3,
                value = 60,
                timestamp = 4_000L,
            ),
        )

        // Beziehungs-Queries zuruecklesen und pruefen.
        val matchPlayers = matchPlayerDao.getByMatch(matchId)
        assertEquals(1, matchPlayers.size)
        assertEquals(matchPlayerId, matchPlayers.first().id)
        assertEquals(playerId, matchPlayers.first().playerId)

        val legs = legDao.getByMatch(matchId)
        assertEquals(1, legs.size)
        assertEquals(legId, legs.first().id)

        val turns = turnDao.getByLeg(legId)
        assertEquals(1, turns.size)
        assertEquals(turnId, turns.first().id)
        assertEquals(60, turns.first().totalScored)

        val throws = throwDao.getByTurn(turnId)
        assertEquals(1, throws.size)
        assertEquals(throwId, throws.first().id)
        assertEquals(60, throws.first().value)

        assertNotNull(matchDao.getById(matchId))

        // CASCADE-Probe: Match loeschen -> MatchPlayer/Legs/Turns/Throws verschwinden.
        matchDao.delete(matchDao.getById(matchId)!!)

        assertTrue(matchPlayerDao.getByMatch(matchId).isEmpty())
        assertTrue(legDao.getByMatch(matchId).isEmpty())
        assertTrue(turnDao.getByLeg(legId).isEmpty())
        assertTrue(throwDao.getByTurn(turnId).isEmpty())

        // Player bleibt bestehen (kein CASCADE auf players).
        assertNotNull(playerDao.getById(playerId))
    }
}
