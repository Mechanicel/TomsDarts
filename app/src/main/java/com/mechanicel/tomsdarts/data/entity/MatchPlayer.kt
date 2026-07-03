package com.mechanicel.tomsdarts.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cross-Ref: Teilnahme eines [Player] an einem [Match] inkl. Sitzreihenfolge.
 * Lokal persistiert (offline-first).
 *
 * @param id Auto-generierter Primaerschluessel.
 * @param matchId Zugehoeriges Match (FK -> matches, CASCADE).
 * @param playerId Teilnehmender Spieler (FK -> players, RESTRICT: ein an einem
 *   Match beteiligter Spieler darf nicht geloescht werden, solange er verknuepft ist).
 * @param position Startposition/Reihenfolge des Spielers im Match.
 */
@Entity(
    tableName = "match_players",
    foreignKeys = [
        ForeignKey(
            entity = Match::class,
            parentColumns = ["id"],
            childColumns = ["matchId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Player::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("matchId"), Index("playerId")],
)
data class MatchPlayer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val playerId: Long,
    val position: Int,
)
