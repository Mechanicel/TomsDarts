package com.mechanicel.tomsdarts.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mechanicel.tomsdarts.data.entity.Turn

/**
 * Datenzugriff fuer [Turn]. Happy-Path-Umfang, Stil analog zu PlayerDao.
 */
@Dao
interface TurnDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(turn: Turn): Long

    @Query("SELECT * FROM turns WHERE id = :id")
    suspend fun getById(id: Long): Turn?

    @Query("SELECT * FROM turns WHERE legId = :legId ORDER BY turnIndex")
    suspend fun getByLeg(legId: Long): List<Turn>
}
