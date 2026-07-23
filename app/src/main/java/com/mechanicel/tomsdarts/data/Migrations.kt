package com.mechanicel.tomsdarts.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration von Schema-Version 1 auf 2.
 *
 * Zweck: Ein Spieler soll loeschbar sein, auch wenn er bereits Match-Historie hat.
 * Dafuer wird die Fremdschluessel-Aktion der Spieler-Referenz in `turns` und
 * `match_players` von RESTRICT (Loeschen blockiert) auf SET_NULL (Loeschen erlaubt,
 * Referenz wird anonymisiert) umgestellt und die betroffene Spalte `playerId` von
 * NOT NULL auf nullable gelockert.
 *
 * SQLite kann Fremdschluessel-Definitionen nicht per `ALTER TABLE` aendern, daher
 * werden beide Tabellen nach dem empfohlenen Verfahren neu aufgebaut: neue Tabelle
 * mit gewuenschter Definition anlegen, Daten kopieren, alte Tabelle verwerfen, neue
 * umbenennen und die Indizes neu anlegen.
 *
 * Room fuehrt Migrationen aus, bevor die Fremdschluessel-Durchsetzung fuer die
 * Verbindung eingeschaltet wird (`PRAGMA foreign_keys = ON` erst in `onOpen`).
 * Das `DROP TABLE turns` loest daher keine kaskadierende Loeschung der auf `turns`
 * verweisenden `throws`-Zeilen aus.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // --- turns: playerId nullable + SET_NULL statt RESTRICT --------------
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `turns_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`legId` INTEGER NOT NULL, " +
                "`playerId` INTEGER, " +
                "`turnIndex` INTEGER NOT NULL, " +
                "`bust` INTEGER NOT NULL, " +
                "`totalScored` INTEGER NOT NULL, " +
                "FOREIGN KEY(`legId`) REFERENCES `legs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`playerId`) REFERENCES `players`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL(
            "INSERT INTO `turns_new` (`id`, `legId`, `playerId`, `turnIndex`, `bust`, `totalScored`) " +
                "SELECT `id`, `legId`, `playerId`, `turnIndex`, `bust`, `totalScored` FROM `turns`",
        )
        db.execSQL("DROP TABLE `turns`")
        db.execSQL("ALTER TABLE `turns_new` RENAME TO `turns`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_turns_legId` ON `turns` (`legId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_turns_playerId` ON `turns` (`playerId`)")

        // --- match_players: playerId nullable + SET_NULL statt RESTRICT ------
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `match_players_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`matchId` INTEGER NOT NULL, " +
                "`playerId` INTEGER, " +
                "`position` INTEGER NOT NULL, " +
                "FOREIGN KEY(`matchId`) REFERENCES `matches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`playerId`) REFERENCES `players`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL(
            "INSERT INTO `match_players_new` (`id`, `matchId`, `playerId`, `position`) " +
                "SELECT `id`, `matchId`, `playerId`, `position` FROM `match_players`",
        )
        db.execSQL("DROP TABLE `match_players`")
        db.execSQL("ALTER TABLE `match_players_new` RENAME TO `match_players`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_match_players_matchId` ON `match_players` (`matchId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_match_players_playerId` ON `match_players` (`playerId`)")
    }
}
