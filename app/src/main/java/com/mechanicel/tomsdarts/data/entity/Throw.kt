package com.mechanicel.tomsdarts.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ein einzelner Dartwurf innerhalb eines [Turn]. Lokal persistiert.
 *
 * @param id Auto-generierter Primaerschluessel.
 * @param turnId Zugehoerige Aufnahme (FK -> turns, CASCADE).
 * @param dartIndex Position des Wurfs in der Aufnahme (0..2).
 * @param segment Getroffenes Segment (1-20, 25 = Bull, 0 = daneben/Out).
 * @param multiplier Faktor des Felds (1 = Single, 2 = Double, 3 = Triple).
 * @param value Erzielte Punkte dieses Wurfs (segment * multiplier).
 * @param timestamp Zeitpunkt des Wurfs in Epoch-Millis.
 */
@Entity(
    tableName = "throws",
    foreignKeys = [
        ForeignKey(
            entity = Turn::class,
            parentColumns = ["id"],
            childColumns = ["turnId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("turnId")],
)
data class Throw(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val turnId: Long,
    val dartIndex: Int,
    val segment: Int,
    val multiplier: Int,
    val value: Int,
    val timestamp: Long,
)
