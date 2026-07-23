package com.mechanicel.tomsdarts.data

import android.database.sqlite.SQLiteConstraintException
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Constraint-Tests fuer das relationale Schema: CASCADE-Tiefenloeschung sowie
 * SET_NULL bei Spieler-Loeschung (Match-/Leg-Gewinner und, seit Schema v2, auch
 * Turn-/MatchPlayer-Referenz -> Spieler mit Historie bleibt loeschbar).
 * Zusaetzlich FK-Verletzungen beim Insert.
 *
 * Setup analog zu [PlayerDaoTest]: host-seitig (JVM) unter Robolectric mit
 * In-Memory-Room (FK-Enforcement standardmaessig an), Test-SDK auf 34 gepinnt.
 *
 * PlayerDao hat kein delete(); fuer SET_NULL wird daher ein direkter DELETE
 * ueber den SupportSQLite-Helper ausgefuehrt (test-only Rohzugriff).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForeignKeyConstraintsTest {

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

    /** Direkter DELETE eines Spielers (PlayerDao bietet kein delete). */
    private fun deletePlayerRaw(playerId: Long) {
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM players WHERE id = ?",
            arrayOf<Any>(playerId),
        )
    }

    private suspend fun newMatch(winnerId: Long? = null) = db.matchDao().insert(
        Match(
            modeType = "501",
            startScore = 501,
            doubleOut = true,
            legsToWin = 1,
            setsToWin = 1,
            startedAt = 1L,
            winnerId = winnerId,
        ),
    )

    // --- CASCADE -------------------------------------------------------------

    @Test
    fun deletingMatchCascadesToLegsTurnsThrowsAndMatchPlayers() = runBlocking {
        val player = db.playerDao().insert(Player(name = "P", createdAt = 1L))
        val matchId = newMatch()
        val otherMatchId = newMatch()

        db.matchPlayerDao().insert(MatchPlayer(matchId = matchId, playerId = player, position = 0))
        val legId = db.legDao().insert(Leg(matchId = matchId, legNumber = 1, startedAt = 1L))
        val turnId = db.turnDao().insert(
            Turn(legId = legId, playerId = player, turnIndex = 0, bust = false, totalScored = 60),
        )
        db.throwDao().insert(
            Throw(turnId = turnId, dartIndex = 0, segment = 20, multiplier = 3, value = 60, timestamp = 1L),
        )

        // Daten am anderen Match, die NICHT verschwinden duerfen.
        val otherLeg = db.legDao().insert(Leg(matchId = otherMatchId, legNumber = 1, startedAt = 1L))
        val otherTurn = db.turnDao().insert(
            Turn(legId = otherLeg, playerId = player, turnIndex = 0, bust = false, totalScored = 26),
        )
        val otherThrow = db.throwDao().insert(
            Throw(turnId = otherTurn, dartIndex = 0, segment = 13, multiplier = 2, value = 26, timestamp = 1L),
        )
        db.matchPlayerDao().insert(MatchPlayer(matchId = otherMatchId, playerId = player, position = 0))

        db.matchDao().delete(db.matchDao().getById(matchId)!!)

        // Alles unter matchId weg.
        assertTrue(db.matchPlayerDao().getByMatch(matchId).isEmpty())
        assertTrue(db.legDao().getByMatch(matchId).isEmpty())
        assertNull(db.turnDao().getById(turnId))
        assertTrue(db.turnDao().getByLeg(legId).isEmpty())
        assertTrue(db.throwDao().getByTurn(turnId).isEmpty())

        // Anderes Match unberuehrt.
        assertNotNull(db.matchDao().getById(otherMatchId))
        assertEquals(1, db.legDao().getByMatch(otherMatchId).size)
        assertNotNull(db.turnDao().getById(otherTurn))
        assertEquals(1, db.throwDao().getByTurn(otherTurn).size)
        assertNotNull(db.throwDao().getById(otherThrow))
        assertEquals(1, db.matchPlayerDao().getByMatch(otherMatchId).size)

        // Spieler bleibt (kein CASCADE auf players).
        assertNotNull(db.playerDao().getById(player))
    }

    @Test
    fun deletingLegCascadesToTurnsAndThrowsOnly() = runBlocking {
        val player = db.playerDao().insert(Player(name = "P", createdAt = 1L))
        val matchId = newMatch()
        val legA = db.legDao().insert(Leg(matchId = matchId, legNumber = 1, startedAt = 1L))
        val legB = db.legDao().insert(Leg(matchId = matchId, legNumber = 2, startedAt = 1L))

        val turnA = db.turnDao().insert(
            Turn(legId = legA, playerId = player, turnIndex = 0, bust = false, totalScored = 60),
        )
        db.throwDao().insert(
            Throw(turnId = turnA, dartIndex = 0, segment = 20, multiplier = 3, value = 60, timestamp = 1L),
        )
        val turnB = db.turnDao().insert(
            Turn(legId = legB, playerId = player, turnIndex = 0, bust = false, totalScored = 40),
        )

        // Leg A loeschen -> Turn/Throw von A weg; Match und Leg B bleiben.
        db.legDao().getByMatch(matchId).first { it.id == legA }
        db.openHelper.writableDatabase.execSQL("DELETE FROM legs WHERE id = ?", arrayOf<Any>(legA))

        assertTrue(db.turnDao().getByLeg(legA).isEmpty())
        assertNull(db.turnDao().getById(turnA))
        assertTrue(db.throwDao().getByTurn(turnA).isEmpty())

        assertNotNull(db.matchDao().getById(matchId))
        assertNotNull(db.legDao().getById(legB))
        assertNotNull(db.turnDao().getById(turnB))
    }

    // --- SET_NULL ------------------------------------------------------------

    @Test
    fun deletingMatchWinnerSetsWinnerIdNull() = runBlocking {
        // Reiner Gewinner-Spieler ohne Turn/MatchPlayer -> nicht durch RESTRICT blockiert.
        val winner = db.playerDao().insert(Player(name = "Winner", createdAt = 1L))
        val matchId = newMatch(winnerId = winner)
        assertEquals(winner, db.matchDao().getById(matchId)!!.winnerId)

        deletePlayerRaw(winner)

        val match = db.matchDao().getById(matchId)
        assertNotNull("Match bleibt bestehen", match)
        assertNull("winnerId muss auf null gesetzt sein", match!!.winnerId)
        assertNull("Spieler ist geloescht", db.playerDao().getById(winner))
    }

    @Test
    fun deletingLegWinnerSetsWinnerIdNull() = runBlocking {
        val winner = db.playerDao().insert(Player(name = "LegWinner", createdAt = 1L))
        val matchId = newMatch()
        val legId = db.legDao().insert(
            Leg(matchId = matchId, legNumber = 1, winnerId = winner, startedAt = 1L),
        )
        assertEquals(winner, db.legDao().getById(legId)!!.winnerId)

        deletePlayerRaw(winner)

        val leg = db.legDao().getById(legId)
        assertNotNull("Leg bleibt bestehen", leg)
        assertNull("winnerId muss auf null gesetzt sein", leg!!.winnerId)
    }

    // --- SET_NULL (Spieler mit Historie loeschbar) ---------------------------

    @Test
    fun deletingPlayerReferencedByTurnSetsPlayerIdNull() = runBlocking {
        val player = db.playerDao().insert(Player(name = "Active", createdAt = 1L))
        val matchId = newMatch()
        val legId = db.legDao().insert(Leg(matchId = matchId, legNumber = 1, startedAt = 1L))
        val turnId = db.turnDao().insert(
            Turn(legId = legId, playerId = player, turnIndex = 0, bust = false, totalScored = 60),
        )
        val throwId = db.throwDao().insert(
            Throw(turnId = turnId, dartIndex = 0, segment = 20, multiplier = 3, value = 60, timestamp = 1L),
        )

        // Loeschen ist erlaubt (kein RESTRICT mehr).
        deletePlayerRaw(player)

        // Spieler weg, Historie bleibt anonymisiert erhalten.
        assertNull("Spieler ist geloescht", db.playerDao().getById(player))
        val turn = db.turnDao().getById(turnId)
        assertNotNull("Turn bleibt bestehen", turn)
        assertNull("Turn.playerId muss auf null gesetzt sein", turn!!.playerId)
        assertNotNull("Leg bleibt bestehen", db.legDao().getById(legId))
        assertNotNull("Match bleibt bestehen", db.matchDao().getById(matchId))
        assertNotNull("Throw bleibt bestehen", db.throwDao().getById(throwId))
    }

    @Test
    fun deletingPlayerReferencedByMatchPlayerSetsPlayerIdNull() = runBlocking {
        val player = db.playerDao().insert(Player(name = "Participant", createdAt = 1L))
        val other = db.playerDao().insert(Player(name = "Other", createdAt = 1L))
        val matchId = newMatch()
        val mpId = db.matchPlayerDao().insert(
            MatchPlayer(matchId = matchId, playerId = player, position = 0),
        )
        val otherMpId = db.matchPlayerDao().insert(
            MatchPlayer(matchId = matchId, playerId = other, position = 1),
        )

        deletePlayerRaw(player)

        assertNull("Spieler ist geloescht", db.playerDao().getById(player))
        val mp = db.matchPlayerDao().getById(mpId)
        assertNotNull("MatchPlayer bleibt bestehen", mp)
        assertNull("MatchPlayer.playerId muss auf null gesetzt sein", mp!!.playerId)
        // Uebrige Teilnehmer bleiben unberuehrt.
        assertNotNull("Anderer Spieler bleibt bestehen", db.playerDao().getById(other))
        assertEquals(other, db.matchPlayerDao().getById(otherMpId)!!.playerId)
        assertNotNull("Match bleibt bestehen", db.matchDao().getById(matchId))
    }

    // --- FK-Verletzung beim Insert ------------------------------------------

    @Test
    fun insertingLegForUnknownMatchFails() {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                db.legDao().insert(Leg(matchId = 999L, legNumber = 1, startedAt = 1L))
            }
        }
    }

    @Test
    fun insertingTurnForUnknownLegFails() {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                val player = db.playerDao().insert(Player(name = "P", createdAt = 1L))
                db.turnDao().insert(
                    Turn(legId = 999L, playerId = player, turnIndex = 0, bust = false, totalScored = 0),
                )
            }
        }
    }

    @Test
    fun insertingTurnForUnknownPlayerFails() {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                val matchId = newMatch()
                val legId = db.legDao().insert(Leg(matchId = matchId, legNumber = 1, startedAt = 1L))
                db.turnDao().insert(
                    Turn(legId = legId, playerId = 888L, turnIndex = 0, bust = false, totalScored = 0),
                )
            }
        }
    }

    @Test
    fun insertingThrowForUnknownTurnFails() {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                db.throwDao().insert(Throw(turnId = 777L, dartIndex = 0, segment = 20, multiplier = 1, value = 20, timestamp = 1L))
            }
        }
    }

    @Test
    fun insertingMatchPlayerForUnknownPlayerFails() {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                val matchId = newMatch()
                db.matchPlayerDao().insert(MatchPlayer(matchId = matchId, playerId = 666L, position = 0))
            }
        }
    }

    @Test
    fun insertingMatchWithUnknownWinnerFails() {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                newMatch(winnerId = 555L)
            }
        }
    }
}
