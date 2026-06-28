package com.mechanicel.tomsdarts.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ein Leg innerhalb eines [Match]. Lokal persistiert (offline-first).
 *
 * @param id Auto-generierter Primaerschluessel.
 * @param matchId Zugehoeriges Match (FK -> matches, CASCADE).
 * @param setNumber Set-Nummer, null wenn ohne Sets gespielt wird.
 * @param legNumber Fortlaufende Leg-Nummer.
 * @param winnerId Gewinner-Spieler (FK -> players, SET_NULL bei Spieler-Loeschung).
 * @param startedAt Startzeitpunkt in Epoch-Millis.
 * @param endedAt Endzeitpunkt in Epoch-Millis, null solange laufend.
 */
@Entity(
    tableName = "legs",
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
            childColumns = ["winnerId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("matchId"), Index("winnerId")],
)
data class Leg(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val setNumber: Int? = null,
    val legNumber: Int,
    val winnerId: Long? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
)
