package com.internal.tracker.queue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingLocationDao {
    @Insert
    suspend fun insert(location: PendingLocation): Long

    @Query("SELECT * FROM pending_locations ORDER BY timestamp, id LIMIT :limit")
    suspend fun oldest(limit: Int): List<PendingLocation>

    @Query("DELETE FROM pending_locations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_locations SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)

    @Query("SELECT COUNT(*) FROM pending_locations")
    suspend fun countNow(): Int

    @Query("SELECT COUNT(*) FROM pending_locations")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM pending_locations WHERE id IN (SELECT id FROM pending_locations ORDER BY timestamp, id LIMIT :amount)")
    suspend fun deleteOldest(amount: Int)
}
