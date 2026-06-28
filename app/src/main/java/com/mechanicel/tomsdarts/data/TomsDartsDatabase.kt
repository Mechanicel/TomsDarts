package com.mechanicel.tomsdarts.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mechanicel.tomsdarts.data.dao.PlayerDao
import com.mechanicel.tomsdarts.data.entity.Player

/**
 * Lokale Room-Datenbank von TomsDarts. Komplett offline, keine Cloud/Backend.
 *
 * Bewusst minimal gehalten: Singleton/Provider und Dependency-Injection folgen
 * in einer spaeteren Aufgabe (Repository-/DI-Schritt).
 */
@Database(
    entities = [Player::class],
    version = 1,
    exportSchema = true,
)
abstract class TomsDartsDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
}
