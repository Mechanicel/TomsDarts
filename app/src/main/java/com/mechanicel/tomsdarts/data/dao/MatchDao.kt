package com.mechanicel.tomsdarts.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mechanicel.tomsdarts.data.entity.Match

/**
 * Datenzugriff fuer [Match]. Happy-Path-Umfang, Stil analog zu PlayerDao.
 */
@Dao
interface MatchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(match: Match): Long

    @Delete
    suspend fun delete(match: Match)

    @Query("SELECT * FROM matches ORDER BY id")
    suspend fun getAll(): List<Match>

    @Query("SELECT * FROM matches WHERE id = :id")
    suspend fun getById(id: Long): Match?
}
