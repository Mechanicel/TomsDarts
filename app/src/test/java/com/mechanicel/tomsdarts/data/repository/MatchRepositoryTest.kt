package com.mechanicel.tomsdarts.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Happy-Path- und Edge-Abdeckung fuer [MatchRepository]: positive IDs aller
 * Schreib-Ops, korrekte Lese-Ops, Eltern-Kind-Isolation, Sortierung der
 * Teilnehmer nach Position, CASCADE-Loeschung ueber [MatchRepository.deleteMatch]
 * sowie leere Listen fuer kinderlose/unbekannte Eltern.
 *
 * Setup analog zu [PlayerRepositoryTest]: host-seitig unter Robolectric mit
 * In-Memory-Room (FK-Enforcement standardmaessig an, SDK 34 gepinnt). Das
 * Repository wird ueber die DAOs der In-Memory-DB konstruiert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MatchRepositoryTest {

    private lateinit var db: TomsDartsDatabase
    private lateinit var repository: MatchRepository

    @Before
    fun createRepository() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TomsDartsDatabase::class.java,
        ).build()
        repository = MatchRepository(
            matchDao = db.matchDao(),
            legDao = db.legDao(),
            turnDao = db.turnDao(),
            throwDao = db.throwDao(),
            matchPlayerDao = db.matchPlayerDao(),
        )
    }

    @After
    fun closeDb() {
        db.close()
    }

    // --- Test-Helfer ---------------------------------------------------------

    private fun match(winnerId: Long? = null) = Match(
        modeType = "501",
        startScore = 501,
        doubleOut = true,
        legsToWin = 1,
        setsToWin = 1,
        startedAt = 1L,
        winnerId = winnerId,
    )

    private suspend fun newPlayer(name: String = "P"): Long =
        db.playerDao().insert(Player(name = name, createdAt = 1L))

    // --- Schreib-Ops liefern positive IDs ------------------------------------

    @Test
    fun allWriteOpsReturnPositiveIds() = runBlocking {
        val playerId = newPlayer()
        val matchId = repository.createMatch(match())
        assertTrue("createMatch", matchId > 0)

        val mpId = repository.addPlayerToMatch(
            MatchPlayer(matchId = matchId, playerId = playerId, position = 0),
        )
        assertTrue("addPlayerToMatch", mpId > 0)

        val legId = repository.addLeg(Leg(matchId = matchId, legNumber = 1, startedAt = 1L))
        assertTrue("addLeg", legId > 0)

        val turnId = repository.addTurn(
            Turn(legId = legId, playerId = playerId, turnIndex = 0, bust = false, totalScored = 60),
        )
        assertTrue("addTurn", turnId > 0)

        val throwId = repository.addThrow(
            Throw(turnId = turnId, dartIndex = 0, segment = 20, multiplier = 3, value = 60, timestamp = 1L),
        )
        assertTrue("addThrow", throwId > 0)
    }

    // --- Lese-Ops liefern korrekt zurueck ------------------------------------

    @Test
    fun readOpsReturnInsertedEntitiesAlongFullHierarchy() = runBlocking {
        val playerId = newPlayer("Tom")
        val matchId = repository.createMatch(match())
        val mpId = repository.addPlayerToMatch(
            MatchPlayer(matchId = matchId, playerId = playerId, position = 0),
        )
        val legId = repository.addLeg(Leg(matchId = matchId, legNumber = 1, startedAt = 1L))
        val turnId = repository.addTurn(
            Turn(legId = legId, playerId = playerId, turnIndex = 0, bust = false, totalScored = 100),
        )
        val throwId = repository.addThrow(
            Throw(turnId = turnId, dartIndex = 0, segment = 20, multiplier = 5, value = 100, timestamp = 1L),
        )

        val matches = repository.getMatches()
        assertEquals(1, matches.size)
        assertEquals(matchId, matches.first().id)
        assertEquals("501", matches.first().modeType)

        val matchPlayers = repository.getMatchPlayers(matchId)
        assertEquals(1, matchPlayers.size)
        assertEquals(mpId, matchPlayers.first().id)
        assertEquals(playerId, matchPlayers.first().playerId)

        val legs = repository.getLegs(matchId)
        assertEquals(1, legs.size)
        assertEquals(legId, legs.first().id)

        val turns = repository.getTurns(legId)
        assertEquals(1, turns.size)
        assertEquals(turnId, turns.first().id)
        assertEquals(100, turns.first().totalScored)

        val throws = repository.getThrows(turnId)
        assertEquals(1, throws.size)
        assertEquals(throwId, throws.first().id)
        assertEquals(100, throws.first().value)
    }

    // --- Eltern-Kind-Isolation ----------------------------------------------

    @Test
    fun relationshipReadsReturnOnlyChildrenOfRequestedParent() = runBlocking {
        val playerId = newPlayer()

        val matchA = repository.createMatch(match())
        val matchB = repository.createMatch(match())

        repository.addPlayerToMatch(MatchPlayer(matchId = matchA, playerId = playerId, position = 0))
        repository.addPlayerToMatch(MatchPlayer(matchId = matchB, playerId = playerId, position = 0))

        val legA = repository.addLeg(Leg(matchId = matchA, legNumber = 1, startedAt = 1L))
        val legB = repository.addLeg(Leg(matchId = matchB, legNumber = 1, startedAt = 1L))

        val turnA = repository.addTurn(
            Turn(legId = legA, playerId = playerId, turnIndex = 0, bust = false, totalScored = 60),
        )
        val turnB = repository.addTurn(
            Turn(legId = legB, playerId = playerId, turnIndex = 0, bust = false, totalScored = 40),
        )

        repository.addThrow(
            Throw(turnId = turnA, dartIndex = 0, segment = 20, multiplier = 3, value = 60, timestamp = 1L),
        )
        repository.addThrow(
            Throw(turnId = turnB, dartIndex = 0, segment = 20, multiplier = 2, value = 40, timestamp = 1L),
        )

        // Legs nur des angefragten Matches.
        assertEquals(listOf(legA), repository.getLegs(matchA).map { it.id })
        assertEquals(listOf(legB), repository.getLegs(matchB).map { it.id })

        // Turns nur des angefragten Legs.
        assertEquals(listOf(turnA), repository.getTurns(legA).map { it.id })
        assertEquals(listOf(turnB), repository.getTurns(legB).map { it.id })

        // Throws nur der angefragten Turns.
        assertEquals(60, repository.getThrows(turnA).single().value)
        assertEquals(40, repository.getThrows(turnB).single().value)

        // MatchPlayers nur des angefragten Matches.
        assertEquals(1, repository.getMatchPlayers(matchA).size)
        assertEquals(matchA, repository.getMatchPlayers(matchA).single().matchId)
        assertEquals(1, repository.getMatchPlayers(matchB).size)
        assertEquals(matchB, repository.getMatchPlayers(matchB).single().matchId)
    }

    // --- Leere Listen bei kinderlosem/unbekanntem Elternteil -----------------

    @Test
    fun readsReturnEmptyForChildlessOrUnknownParents() = runBlocking {
        // Kinderloses, aber existierendes Match.
        val matchId = repository.createMatch(match())
        assertTrue(repository.getLegs(matchId).isEmpty())
        assertTrue(repository.getMatchPlayers(matchId).isEmpty())

        // Vollstaendig unbekannte Eltern-IDs.
        assertTrue(repository.getLegs(9999L).isEmpty())
        assertTrue(repository.getTurns(9999L).isEmpty())
        assertTrue(repository.getThrows(9999L).isEmpty())
        assertTrue(repository.getMatchPlayers(9999L).isEmpty())
    }

    @Test
    fun getMatchesIsEmptyOnFreshDatabase() = runBlocking {
        assertTrue(repository.getMatches().isEmpty())
    }

    // --- Sortierung der Teilnehmer nach Position -----------------------------

    @Test
    fun getMatchPlayersIsSortedByPosition() = runBlocking {
        val p0 = newPlayer("Anna")
        val p1 = newPlayer("Ben")
        val p2 = newPlayer("Cara")
        val matchId = repository.createMatch(match())

        // Bewusst in verdrehter Reihenfolge einfuegen.
        repository.addPlayerToMatch(MatchPlayer(matchId = matchId, playerId = p2, position = 2))
        repository.addPlayerToMatch(MatchPlayer(matchId = matchId, playerId = p0, position = 0))
        repository.addPlayerToMatch(MatchPlayer(matchId = matchId, playerId = p1, position = 1))

        val positions = repository.getMatchPlayers(matchId).map { it.position }
        assertEquals(listOf(0, 1, 2), positions)
        assertEquals(
            listOf(p0, p1, p2),
            repository.getMatchPlayers(matchId).map { it.playerId },
        )
    }

    // --- deleteMatch: CASCADE + Isolation ------------------------------------

    @Test
    fun deleteMatchCascadesChildrenAndLeavesParallelMatchUntouched() = runBlocking {
        val playerId = newPlayer()

        val target = repository.createMatch(match())
        val survivor = repository.createMatch(match())

        repository.addPlayerToMatch(MatchPlayer(matchId = target, playerId = playerId, position = 0))
        val targetLeg = repository.addLeg(Leg(matchId = target, legNumber = 1, startedAt = 1L))
        val targetTurn = repository.addTurn(
            Turn(legId = targetLeg, playerId = playerId, turnIndex = 0, bust = false, totalScored = 60),
        )
        repository.addThrow(
            Throw(turnId = targetTurn, dartIndex = 0, segment = 20, multiplier = 3, value = 60, timestamp = 1L),
        )

        repository.addPlayerToMatch(MatchPlayer(matchId = survivor, playerId = playerId, position = 0))
        val survivorLeg = repository.addLeg(Leg(matchId = survivor, legNumber = 1, startedAt = 1L))
        val survivorTurn = repository.addTurn(
            Turn(legId = survivorLeg, playerId = playerId, turnIndex = 0, bust = false, totalScored = 26),
        )
        repository.addThrow(
            Throw(turnId = survivorTurn, dartIndex = 0, segment = 13, multiplier = 2, value = 26, timestamp = 1L),
        )

        // Match laden und ueber das Repository loeschen.
        val toDelete = repository.getMatches().first { it.id == target }
        repository.deleteMatch(toDelete)

        // Ziel-Match und seine gesamte Hierarchie sind weg.
        assertTrue(repository.getMatches().none { it.id == target })
        assertTrue(repository.getLegs(target).isEmpty())
        assertTrue(repository.getMatchPlayers(target).isEmpty())
        assertTrue(repository.getTurns(targetLeg).isEmpty())
        assertTrue(repository.getThrows(targetTurn).isEmpty())

        // Paralleles Match bleibt vollstaendig erhalten.
        assertNotNull(repository.getMatches().firstOrNull { it.id == survivor })
        assertEquals(1, repository.getLegs(survivor).size)
        assertEquals(1, repository.getMatchPlayers(survivor).size)
        assertEquals(1, repository.getTurns(survivorLeg).size)
        assertEquals(1, repository.getThrows(survivorTurn).size)

        // Spieler bleibt erhalten (kein CASCADE auf players).
        assertNotNull(db.playerDao().getById(playerId))
    }

    @Test
    fun getMatchesReturnsMultipleMatches() = runBlocking {
        val a = repository.createMatch(match())
        val b = repository.createMatch(match())
        val c = repository.createMatch(match())

        val ids = repository.getMatches().map { it.id }
        assertEquals(3, ids.size)
        assertTrue(ids.containsAll(listOf(a, b, c)))
    }

    @Test
    fun deletingMatchLeavesUnrelatedMatchPlayerReadsEmptyButMatchUnaffected() = runBlocking {
        // Sicherstellen, dass getMatchPlayers fuer ein bereits geloeschtes Match leer ist
        // (CASCADE), und kein Fehler beim Lesen einer geloeschten Eltern-ID entsteht.
        val playerId = newPlayer()
        val matchId = repository.createMatch(match())
        repository.addPlayerToMatch(MatchPlayer(matchId = matchId, playerId = playerId, position = 0))

        repository.deleteMatch(repository.getMatches().single())

        assertTrue(repository.getMatchPlayers(matchId).isEmpty())
        assertNull(db.matchDao().getById(matchId))
    }
}
