package com.mechanicel.tomsdarts.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ein Match (Partie) zwischen Spielern. Lokal persistiert (offline-first).
 *
 * @param id Auto-generierter Primaerschluessel.
 * @param modeType Spielmodus (z.B. "501", "301", "cricket").
 * @param startScore Startpunktzahl des Modus.
 * @param doubleOut Ob ein Double zum Auschecken noetig ist.
 * @param legsToWin Anzahl Legs, die fuer einen Set-/Match-Gewinn noetig sind.
 * @param setsToWin Anzahl Sets, die fuer den Match-Gewinn noetig sind.
 * @param startedAt Startzeitpunkt in Epoch-Millis.
 * @param endedAt Endzeitpunkt in Epoch-Millis, null solange laufend.
 * @param winnerId Gewinner-Spieler (FK -> players, SET_NULL bei Spieler-Loeschung).
 */
@Entity(
    tableName = "matches",
    foreignKeys = [
        ForeignKey(
            entity = Player::class,
            parentColumns = ["id"],
            childColumns = ["winnerId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("winnerId")],
)
data class Match(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modeType: String,
    val startScore: Int,
    val doubleOut: Boolean,
    val legsToWin: Int,
    val setsToWin: Int,
    val startedAt: Long,
    val endedAt: Long? = null,
    val winnerId: Long? = null,
)
