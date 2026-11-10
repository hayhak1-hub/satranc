package com.thunderbascraft.satranc.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    /** Newest first: finished games by end time, unfinished ones by start time. */
    @Query("SELECT * FROM games ORDER BY COALESCE(finishedAt, startedAt) DESC, id DESC")
    fun observeAll(): Flow<List<GameRecord>>

    @Query("SELECT * FROM games ORDER BY COALESCE(finishedAt, startedAt) DESC, id DESC")
    suspend fun all(): List<GameRecord>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun get(id: Long): GameRecord?

    @Query("SELECT * FROM games WHERE id IN (:ids) ORDER BY COALESCE(finishedAt, startedAt) DESC, id DESC")
    suspend fun getAll(ids: List<Long>): List<GameRecord>

    @Query("SELECT COUNT(*) FROM games WHERE pgnHash = :hash")
    suspend fun countByHash(hash: String): Int

    @Query("SELECT id FROM games WHERE pgnHash = :hash LIMIT 1")
    suspend fun idByHash(hash: String): Long?

    @Insert
    suspend fun insert(record: GameRecord): Long

    @Insert
    suspend fun insertAll(records: List<GameRecord>): List<Long>

    @Update
    suspend fun update(record: GameRecord)

    @Query("UPDATE games SET analysis_whiteInaccuracies = :wi, analysis_whiteMistakes = :wm, analysis_whiteBlunders = :wb, " +
        "analysis_whiteAverageLoss = :wl, analysis_blackInaccuracies = :bi, analysis_blackMistakes = :bm, " +
        "analysis_blackBlunders = :bb, analysis_blackAverageLoss = :bl WHERE id = :id")
    suspend fun setAnalysis(id: Long, wi: Int, wm: Int, wb: Int, wl: Int, bi: Int, bm: Int, bb: Int, bl: Int)

    @Query("DELETE FROM games WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM games")
    suspend fun deleteAll()
}
