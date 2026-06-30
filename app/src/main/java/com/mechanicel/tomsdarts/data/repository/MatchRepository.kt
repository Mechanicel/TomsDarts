package com.mechanicel.tomsdarts.data.repository

import com.mechanicel.tomsdarts.data.dao.LegDao
import com.mechanicel.tomsdarts.data.dao.MatchDao
import com.mechanicel.tomsdarts.data.dao.MatchPlayerDao
import com.mechanicel.tomsdarts.data.dao.ThrowDao
import com.mechanicel.tomsdarts.data.dao.TurnDao
import com.mechanicel.tomsdarts.data.entity.Leg
import com.mechanicel.tomsdarts.data.entity.Match
import com.mechanicel.tomsdarts.data.entity.MatchPlayer
import com.mechanicel.tomsdarts.data.entity.Throw
import com.mechanicel.tomsdarts.data.entity.Turn

/**
 * Repository-Schicht ueber den Match-bezogenen DAOs ([MatchDao], [LegDao],
 * [TurnDao], [ThrowDao], [MatchPlayerDao]). Kapselt den Datenzugriff fuer
 * Matches und bleibt rein lokal (offline-first, keine Cloud/Backend).
 *
 * Bewusst **duenn und regelfrei**: reines Durchreichen an die DAOs, keinerlei
 * Spiellogik (kein Bust/Checkout/Average). Solche Regeln gehoeren in eine
 * spaetere Domain-/ViewModel-Schicht, nicht hierher.
 *
 * Die suspend-DAO-Methoden laufen bereits auf Rooms eigenem Executor, daher
 * wird hier bewusst kein zusaetzliches `withContext(Dispatchers.IO)` verwendet.
 */
class MatchRepository(
    private val matchDao: MatchDao,
    private val legDao: LegDao,
    private val turnDao: TurnDao,
    private val throwDao: ThrowDao,
    private val matchPlayerDao: MatchPlayerDao,
) {

    // --- Schreiben ---

    /** Legt ein neues [Match] an und gibt dessen neue ID zurueck. */
    suspend fun createMatch(match: Match): Long = matchDao.insert(match)

    /**
     * Aktualisiert ein bestehendes [Match] (z.B. zum Setzen von endedAt/winnerId
     * beim Match-Abschluss). Im Gegensatz zu einem Re-Insert mit REPLACE loescht
     * dies KEINE abhaengigen Legs/Turns/Throws (kein CASCADE).
     */
    suspend fun updateMatch(match: Match) = matchDao.update(match)

    /** Verknuepft einen Spieler ueber [MatchPlayer] mit einem Match. */
    suspend fun addPlayerToMatch(mp: MatchPlayer): Long = matchPlayerDao.insert(mp)

    /** Fuegt ein [Leg] hinzu und gibt dessen neue ID zurueck. */
    suspend fun addLeg(leg: Leg): Long = legDao.insert(leg)

    /**
     * Aktualisiert ein bestehendes [Leg] (z.B. zum Setzen von endedAt/winnerId
     * beim Leg-Abschluss). Im Gegensatz zu einem Re-Insert mit REPLACE loescht
     * dies KEINE abhaengigen Turns/Throws (kein CASCADE).
     */
    suspend fun updateLeg(leg: Leg) = legDao.update(leg)

    /** Fuegt eine Aufnahme ([Turn]) hinzu und gibt deren neue ID zurueck. */
    suspend fun addTurn(turn: Turn): Long = turnDao.insert(turn)

    /** Fuegt einen [Throw] hinzu und gibt dessen neue ID zurueck. */
    suspend fun addThrow(t: Throw): Long = throwDao.insert(t)

    // --- Lesen ---

    /** Liefert alle Matches. */
    suspend fun getMatches(): List<Match> = matchDao.getAll()

    /** Liefert die Legs eines Matches. */
    suspend fun getLegs(matchId: Long): List<Leg> = legDao.getByMatch(matchId)

    /** Liefert die Aufnahmen eines Legs. */
    suspend fun getTurns(legId: Long): List<Turn> = turnDao.getByLeg(legId)

    /** Liefert die Wuerfe einer Aufnahme. */
    suspend fun getThrows(turnId: Long): List<Throw> = throwDao.getByTurn(turnId)

    /** Liefert die Teilnehmer eines Matches. */
    suspend fun getMatchPlayers(matchId: Long): List<MatchPlayer> =
        matchPlayerDao.getByMatch(matchId)

    // --- Loeschen ---

    /** Entfernt das uebergebene Match (CASCADE entfernt abhaengige Daten). */
    suspend fun deleteMatch(match: Match) = matchDao.delete(match)
}
