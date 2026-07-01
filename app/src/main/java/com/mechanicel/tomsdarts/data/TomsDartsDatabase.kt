package com.mechanicel.tomsdarts.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mechanicel.tomsdarts.data.dao.LegDao
import com.mechanicel.tomsdarts.data.dao.MatchDao
import com.mechanicel.tomsdarts.data.dao.MatchPlayerDao
import com.mechanicel.tomsdarts.data.dao.PlayerDao
import com.mechanicel.tomsdarts.data.dao.ThrowDao
import com.mechanicel.tomsdarts.data.dao.TurnDao
import com.mechanicel.tomsdarts.data.entity.Leg
import com.mechanicel.tomsdarts.data.entity.Match
import com.mechanicel.tomsdarts.data.entity.MatchPlayer
import com.mechanicel.tomsdarts.data.entity.Player
import com.mechanicel.tomsdarts.data.entity.Throw
import com.mechanicel.tomsdarts.data.entity.Turn

/**
 * Lokale Room-Datenbank von TomsDarts. Komplett offline, keine Cloud/Backend.
 *
 * Bewusst minimal gehalten: Singleton/Provider und Dependency-Injection folgen
 * in einer spaeteren Aufgabe (Repository-/DI-Schritt).
 */
@Database(
    entities = [
        Player::class,
        Match::class,
        Leg::class,
        Turn::class,
        MatchPlayer::class,
        Throw::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TomsDartsDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao

    abstract fun matchDao(): MatchDao

    abstract fun legDao(): LegDao

    abstract fun turnDao(): TurnDao

    abstract fun throwDao(): ThrowDao

    abstract fun matchPlayerDao(): MatchPlayerDao

    companion object {
        @Volatile
        private var INSTANCE: TomsDartsDatabase? = null

        /**
         * Liefert die prozessweite Singleton-Instanz (thread-sicher, double-checked).
         *
         * Persistiert lokal in `tomsdarts.db`. `fallbackToDestructiveMigration`
         * folgt der Strategie "Schema bei Bedarf regenerieren statt migrieren".
         */
        fun getInstance(context: Context): TomsDartsDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TomsDartsDatabase::class.java,
                    "tomsdarts.db",
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
