package com.mechanicel.tomsdarts.data.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Lesender Datenzugriff fuer die Analytics-Schicht (Phase 5, ADR-0005/ADR-0034).
 *
 * Liefert die throw-level-Daten als **flache** Join-Zeilen ([StatsThrowRow]):
 * Turn -> Leg -> Match per INNER JOIN, Throw per LEFT JOIN (eine Aufnahme ohne
 * persistierte Wuerfe faellt so nicht still heraus). Die Gruppierung zu Legs/
 * Aufnahmen/Darts uebernimmt die pure Kotlin-Funktion
 * `com.mechanicel.tomsdarts.analytics.toAnalyticsLegs`; Kennzahlen werden als
 * pure Funktionen darauf berechnet — hier steckt bewusst KEINE Spiellogik.
 *
 * Sortierung der Zeilen-Queries: Match chronologisch (startedAt, id), dann
 * Set (null zuerst), Leg (legNumber, id), Aufnahme (turnIndex, id), Dart
 * (dartIndex, id). Rein lesend, keine Schemaaenderung.
 */
@Dao
interface StatsDao {

    /**
     * Alle Wurf-Zeilen der Aufnahmen des Spielers [playerId] (nur dessen eigene
     * Turns, Gegner-Aufnahmen sind nicht enthalten). Optional auf einen
     * Spielmodus [modeType] (GameModeCatalog-Key) eingeschraenkt; `null` = alle
     * Modi. Bust-Aufnahmen sind enthalten (inkl. aller real geworfenen Darts).
     *
     * Aufnahmen eines geloeschten Spielers (playerId = null, SET_NULL) fallen
     * hier heraus, bleiben aber in [getThrowRowsForMatch] erhalten.
     */
    @Query(
        """
        SELECT m.id AS matchId, m.modeType AS modeType, m.startScore AS startScore,
               m.doubleOut AS doubleOut, m.startedAt AS matchStartedAt,
               m.endedAt AS matchEndedAt,
               l.id AS legId, l.setNumber AS setNumber, l.legNumber AS legNumber,
               l.winnerId AS legWinnerId, l.endedAt AS legEndedAt,
               t.id AS turnId, t.turnIndex AS turnIndex, t.playerId AS playerId,
               t.bust AS bust, t.totalScored AS totalScored,
               th.dartIndex AS dartIndex, th.segment AS segment,
               th.multiplier AS multiplier, th.value AS value,
               th.timestamp AS timestamp
        FROM turns t
        INNER JOIN legs l ON l.id = t.legId
        INNER JOIN matches m ON m.id = l.matchId
        LEFT JOIN throws th ON th.turnId = t.id
        WHERE t.playerId = :playerId
          AND (:modeType IS NULL OR m.modeType = :modeType)
        ORDER BY m.startedAt, m.id, l.setNumber, l.legNumber, l.id,
                 t.turnIndex, t.id, th.dartIndex, th.id
        """,
    )
    suspend fun getThrowRowsForPlayer(playerId: Long, modeType: String? = null): List<StatsThrowRow>

    /**
     * Alle Wurf-Zeilen des Matches [matchId] ueber alle Spieler (inkl.
     * anonymisierter Aufnahmen geloeschter Spieler mit playerId = null).
     */
    @Query(
        """
        SELECT m.id AS matchId, m.modeType AS modeType, m.startScore AS startScore,
               m.doubleOut AS doubleOut, m.startedAt AS matchStartedAt,
               m.endedAt AS matchEndedAt,
               l.id AS legId, l.setNumber AS setNumber, l.legNumber AS legNumber,
               l.winnerId AS legWinnerId, l.endedAt AS legEndedAt,
               t.id AS turnId, t.turnIndex AS turnIndex, t.playerId AS playerId,
               t.bust AS bust, t.totalScored AS totalScored,
               th.dartIndex AS dartIndex, th.segment AS segment,
               th.multiplier AS multiplier, th.value AS value,
               th.timestamp AS timestamp
        FROM turns t
        INNER JOIN legs l ON l.id = t.legId
        INNER JOIN matches m ON m.id = l.matchId
        LEFT JOIN throws th ON th.turnId = t.id
        WHERE m.id = :matchId
        ORDER BY l.setNumber, l.legNumber, l.id, t.turnIndex, t.id, th.dartIndex, th.id
        """,
    )
    suspend fun getThrowRowsForMatch(matchId: Long): List<StatsThrowRow>

    /**
     * Matches, an denen der Spieler [playerId] laut `match_players` teilnimmt,
     * absteigend nach Startzeit (neueste zuerst, bei Gleichstand hoehere ID
     * zuerst). Jedes Match erscheint genau einmal.
     */
    @Query(
        """
        SELECT m.id AS id, m.modeType AS modeType, m.startedAt AS startedAt,
               m.endedAt AS endedAt, m.winnerId AS winnerId
        FROM matches m
        WHERE m.id IN (SELECT mp.matchId FROM match_players mp WHERE mp.playerId = :playerId)
        ORDER BY m.startedAt DESC, m.id DESC
        """,
    )
    suspend fun getMatchesForPlayer(playerId: Long): List<StatsMatchRow>
}
