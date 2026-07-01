package com.mechanicel.tomsdarts.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mechanicel.tomsdarts.data.entity.Throw

/**
 * Datenzugriff fuer [Throw]. Happy-Path-Umfang, Stil analog zu PlayerDao.
 */
@Dao
interface ThrowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(throwEntity: Throw): Long

    @Query("SELECT * FROM throws WHERE id = :id")
    suspend fun getById(id: Long): Throw?

    @Query("SELECT * FROM throws WHERE turnId = :turnId ORDER BY dartIndex")
    suspend fun getByTurn(turnId: Long): List<Throw>
}
