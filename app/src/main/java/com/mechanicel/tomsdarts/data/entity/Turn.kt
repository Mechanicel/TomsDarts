package com.mechanicel.tomsdarts.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ein Aufnahme/Turn eines Spielers innerhalb eines [Leg]. Lokal persistiert.
 *
 * @param id Auto-generierter Primaerschluessel.
 * @param legId Zugehoeriges Leg (FK -> legs, CASCADE).
 * @param playerId Werfender Spieler (FK -> players, SET_NULL: wird der Spieler
 *   geloescht, bleibt die Aufnahme als anonymisierter Eintrag (playerId = null)
 *   in der Historie erhalten, statt das Loeschen zu blockieren).
 * @param turnIndex Fortlaufender Index der Aufnahme innerhalb des Legs.
 * @param bust Ob die Aufnahme ueberworfen wurde (Bust).
 * @param totalScored In dieser Aufnahme erzielte Gesamtpunkte.
 */
@Entity(
    tableName = "turns",
    foreignKeys = [
        ForeignKey(
            entity = Leg::class,
            parentColumns = ["id"],
            childColumns = ["legId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Player::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("legId"), Index("playerId")],
)
data class Turn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val legId: Long,
    val playerId: Long?,
    val turnIndex: Int,
    val bust: Boolean,
    val totalScored: Int,
)
