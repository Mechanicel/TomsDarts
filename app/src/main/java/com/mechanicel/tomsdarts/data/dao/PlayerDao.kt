package com.mechanicel.tomsdarts.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mechanicel.tomsdarts.data.entity.Player

/**
 * Datenzugriff fuer [Player]. Happy-Path-Umfang fuer den initialen Room-Stack.
 */
@Dao
interface PlayerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(player: Player): Long

    @Query("SELECT * FROM players ORDER BY id")
    suspend fun getAll(): List<Player>

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun getById(id: Long): Player?
}
