package com.mechanicel.tomsdarts.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mechanicel.tomsdarts.data.entity.MatchPlayer

/**
 * Datenzugriff fuer [MatchPlayer] (Cross-Ref). Stil analog zu PlayerDao.
 */
@Dao
interface MatchPlayerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(matchPlayer: MatchPlayer): Long

    @Query("SELECT * FROM match_players WHERE id = :id")
    suspend fun getById(id: Long): MatchPlayer?

    @Query("SELECT * FROM match_players WHERE matchId = :matchId ORDER BY position")
    suspend fun getByMatch(matchId: Long): List<MatchPlayer>
}
