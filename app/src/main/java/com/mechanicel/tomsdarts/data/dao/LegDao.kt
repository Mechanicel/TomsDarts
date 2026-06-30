package com.mechanicel.tomsdarts.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mechanicel.tomsdarts.data.entity.Leg

/**
 * Datenzugriff fuer [Leg]. Happy-Path-Umfang, Stil analog zu PlayerDao.
 */
@Dao
interface LegDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(leg: Leg): Long

    @Update
    suspend fun update(leg: Leg)

    @Query("SELECT * FROM legs WHERE id = :id")
    suspend fun getById(id: Long): Leg?

    @Query("SELECT * FROM legs WHERE matchId = :matchId ORDER BY legNumber")
    suspend fun getByMatch(matchId: Long): List<Leg>
}
