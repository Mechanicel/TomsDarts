package com.mechanicel.tomsdarts.data

import androidx.room.Database
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
}
