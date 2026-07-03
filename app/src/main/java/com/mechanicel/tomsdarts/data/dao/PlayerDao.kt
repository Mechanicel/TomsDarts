package com.mechanicel.tomsdarts.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mechanicel.tomsdarts.data.entity.Player
import kotlinx.coroutines.flow.Flow

/**
 * Datenzugriff fuer [Player]. Happy-Path-Umfang fuer den initialen Room-Stack.
 */
@Dao
interface PlayerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(player: Player): Long

    @Update
    suspend fun update(player: Player)

    @Delete
    suspend fun delete(player: Player)

    @Query("SELECT * FROM players ORDER BY id")
    suspend fun getAll(): List<Player>

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun getById(id: Long): Player?

    @Query("SELECT * FROM players ORDER BY name")
    fun observeAll(): Flow<List<Player>>
}
