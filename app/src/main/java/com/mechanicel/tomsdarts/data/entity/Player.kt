package com.mechanicel.tomsdarts.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ein Spielerprofil. Lokal auf dem Geraet persistiert (offline-first).
 *
 * @param id Auto-generierter Primaerschluessel.
 * @param name Anzeigename des Spielers.
 * @param createdAt Erstellungszeitpunkt in Epoch-Millis.
 */
@Entity(tableName = "players")
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)
