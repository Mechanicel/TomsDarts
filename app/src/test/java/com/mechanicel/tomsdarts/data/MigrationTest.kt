package com.mechanicel.tomsdarts.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Happy-Path-Test fuer [MIGRATION_1_2]: baut eine v1-Datenbank mit Rohdaten auf,
 * migriert auf v2 und prueft, dass (a) das Schema von Room validiert wird,
 * (b) alle Zeilen erhalten bleiben und (c) das Loeschen eines Spielers die
 * `playerId` in `turns` und `match_players` per SET_NULL anonymisiert, statt zu
 * blockieren.
 *
 * Laeuft host-seitig unter Robolectric (SDK 34 gepinnt). Der
 * [MigrationTestHelper] liest die exportierten Schemas aus `app/schemas`, das im
 * `test`-SourceSet als Asset registriert ist. Edge-Cases deckt das Test-Gate ab.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TomsDartsDatabase::class.java,
    )

    @Test
    fun migrate1To2PreservesRowsAndEnablesPlayerDeletion() {
        // v1-Datenbank mit einer vollstaendigen Historie-Hierarchie anlegen.
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO players (id, name, createdAt) VALUES (1, 'Tom', 1)")
            execSQL(
                "INSERT INTO matches (id, modeType, startScore, doubleOut, legsToWin, setsToWin, startedAt) " +
                    "VALUES (1, '501', 501, 1, 1, 1, 1)",
            )
            execSQL("INSERT INTO match_players (id, matchId, playerId, position) VALUES (1, 1, 1, 0)")
            execSQL("INSERT INTO legs (id, matchId, legNumber, startedAt) VALUES (1, 1, 1, 1)")
            execSQL(
                "INSERT INTO turns (id, legId, playerId, turnIndex, bust, totalScored) " +
                    "VALUES (1, 1, 1, 0, 0, 60)",
            )
            close()
        }

        // Migration ausfuehren und gegen das exportierte v2-Schema validieren.
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // (a) Zeilen bleiben erhalten.
        assertEquals(1, rowCount(db, "match_players"))
        assertEquals(1, rowCount(db, "turns"))
        assertEquals(1L, longValue(db, "SELECT playerId FROM turns WHERE id = 1"))
        assertEquals(1L, longValue(db, "SELECT playerId FROM match_players WHERE id = 1"))

        // (b) SET_NULL: Spieler mit Historie loeschen -> playerId wird null.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM players WHERE id = 1")

        assertEquals(0, rowCount(db, "players"))
        assertEquals("Turn bleibt anonymisiert erhalten", 1, rowCount(db, "turns"))
        assertEquals("MatchPlayer bleibt anonymisiert erhalten", 1, rowCount(db, "match_players"))
        assertTrue("Turn.playerId ist null", isNull(db, "SELECT playerId FROM turns WHERE id = 1"))
        assertTrue(
            "MatchPlayer.playerId ist null",
            isNull(db, "SELECT playerId FROM match_players WHERE id = 1"),
        )
        db.close()
    }

    private fun rowCount(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun longValue(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    private fun isNull(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Boolean =
        db.query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.isNull(0)
        }

    /**
     * Reichhaltigere v1-Datenbank: zwei Spieler, ein Match mit gesetztem
     * `winnerId`, ein Leg mit gesetztem `winnerId`, ein regulaerer Turn samt
     * Throw sowie ein Bust-Turn (totalScored 0, bust=1) fuer den zweiten
     * Spieler. Nach der Migration muessen alle Werte 1:1 erhalten bleiben und
     * das Loeschen eines der beiden Spieler darf nur dessen eigene Zeilen auf
     * `null` setzen -- der jeweils andere Spieler bleibt unberuehrt.
     */
    @Test
    fun migrate1To2PreservesMultiplePlayersWinnersAndBustTurn() {
        helper.createDatabase(TEST_DB_RICH, 1).apply {
            execSQL("INSERT INTO players (id, name, createdAt) VALUES (1, 'Tom', 1)")
            execSQL("INSERT INTO players (id, name, createdAt) VALUES (2, 'Anna', 1)")
            execSQL(
                "INSERT INTO matches (id, modeType, startScore, doubleOut, legsToWin, setsToWin, startedAt, winnerId) " +
                    "VALUES (1, '501', 501, 1, 1, 1, 1, 1)",
            )
            execSQL("INSERT INTO match_players (id, matchId, playerId, position) VALUES (1, 1, 1, 0)")
            execSQL("INSERT INTO match_players (id, matchId, playerId, position) VALUES (2, 1, 2, 1)")
            execSQL(
                "INSERT INTO legs (id, matchId, legNumber, winnerId, startedAt) VALUES (1, 1, 1, 1, 1)",
            )
            execSQL(
                "INSERT INTO turns (id, legId, playerId, turnIndex, bust, totalScored) " +
                    "VALUES (1, 1, 1, 0, 0, 60)",
            )
            execSQL(
                "INSERT INTO throws (id, turnId, dartIndex, segment, multiplier, value, timestamp) " +
                    "VALUES (1, 1, 0, 20, 3, 60, 1)",
            )
            // Bust-Turn des zweiten Spielers: totalScored 0, bust=1.
            execSQL(
                "INSERT INTO turns (id, legId, playerId, turnIndex, bust, totalScored) " +
                    "VALUES (2, 1, 2, 1, 1, 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB_RICH, 2, true, MIGRATION_1_2)

        // Alle Zeilen und Werte bleiben unveraendert erhalten.
        assertEquals(2, rowCount(db, "players"))
        assertEquals(2, rowCount(db, "match_players"))
        assertEquals(2, rowCount(db, "turns"))
        assertEquals(1, rowCount(db, "throws"))
        assertEquals(1L, longValue(db, "SELECT winnerId FROM matches WHERE id = 1"))
        assertEquals(1L, longValue(db, "SELECT winnerId FROM legs WHERE id = 1"))
        assertEquals(1L, longValue(db, "SELECT bust FROM turns WHERE id = 2"))
        assertEquals(0L, longValue(db, "SELECT totalScored FROM turns WHERE id = 2"))
        assertEquals(60L, longValue(db, "SELECT value FROM throws WHERE id = 1"))

        // Spieler 1 (Tom) loeschen -> nur seine eigenen Zeilen werden anonymisiert.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM players WHERE id = 1")

        assertEquals(1, rowCount(db, "players"))
        assertTrue("Match.winnerId (Tom) ist null", isNull(db, "SELECT winnerId FROM matches WHERE id = 1"))
        assertTrue("Leg.winnerId (Tom) ist null", isNull(db, "SELECT winnerId FROM legs WHERE id = 1"))
        assertTrue("Turn 1 (Tom) playerId ist null", isNull(db, "SELECT playerId FROM turns WHERE id = 1"))
        assertTrue(
            "MatchPlayer 1 (Tom) playerId ist null",
            isNull(db, "SELECT playerId FROM match_players WHERE id = 1"),
        )
        // Annas Zeilen bleiben unberuehrt.
        assertEquals(2L, longValue(db, "SELECT playerId FROM turns WHERE id = 2"))
        assertEquals(2L, longValue(db, "SELECT playerId FROM match_players WHERE id = 2"))
        // Throw haengt am unveraenderten Turn 1 (CASCADE hat hier nichts geloescht).
        assertEquals(1, rowCount(db, "throws"))
        db.close()
    }

    /**
     * Migration einer leeren v1-Datenbank: kein Rohdaten-Insert, nur Schema.
     * Muss ohne Fehler validieren und alle Tabellen leer lassen.
     */
    @Test
    fun migrate1To2OnEmptyDatabaseSucceeds() {
        helper.createDatabase(TEST_DB_EMPTY, 1).close()

        val db = helper.runMigrationsAndValidate(TEST_DB_EMPTY, 2, true, MIGRATION_1_2)

        assertEquals(0, rowCount(db, "players"))
        assertEquals(0, rowCount(db, "matches"))
        assertEquals(0, rowCount(db, "legs"))
        assertEquals(0, rowCount(db, "turns"))
        assertEquals(0, rowCount(db, "throws"))
        assertEquals(0, rowCount(db, "match_players"))
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val TEST_DB_RICH = "migration-test-rich.db"
        const val TEST_DB_EMPTY = "migration-test-empty.db"
    }
}
