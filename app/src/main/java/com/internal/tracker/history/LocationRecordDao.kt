package com.internal.tracker.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

interface LocationRecordStore {
    suspend fun insert(record: LocationRecord): Long
    suspend fun get(id: Long): LocationRecord?
    suspend fun unsent(limit: Int): List<LocationRecord>
    suspend fun count(): Int
    fun observeBetween(from: Long, until: Long): Flow<List<LocationRecord>>
    suspend fun between(from: Long, until: Long): List<LocationRecord>
    suspend fun markRetrying(ids: List<Long>, error: String)
    suspend fun markSent(ids: List<Long>, sentAt: Long)
}

@Dao
interface LocationRecordDao : LocationRecordStore {
    @Insert
    override suspend fun insert(record: LocationRecord): Long

    @Query("SELECT * FROM location_records WHERE id = :id")
    override suspend fun get(id: Long): LocationRecord?

    @Query("SELECT * FROM location_records WHERE state != 'SENT' ORDER BY capturedAt, id LIMIT :limit")
    override suspend fun unsent(limit: Int): List<LocationRecord>

    @Query("SELECT COUNT(*) FROM location_records")
    override suspend fun count(): Int

    @Query("SELECT * FROM location_records WHERE capturedAt >= :from AND capturedAt < :until ORDER BY capturedAt DESC, id DESC")
    override fun observeBetween(from: Long, until: Long): Flow<List<LocationRecord>>

    @Query("SELECT * FROM location_records WHERE capturedAt >= :from AND capturedAt < :until ORDER BY capturedAt, id")
    override suspend fun between(from: Long, until: Long): List<LocationRecord>

    @Query("UPDATE location_records SET state = 'RETRYING', attemptCount = attemptCount + 1, lastError = :error WHERE id IN (:ids)")
    override suspend fun markRetrying(ids: List<Long>, error: String)

    @Query("UPDATE location_records SET state = 'SENT', sentAt = :sentAt, lastError = NULL WHERE id IN (:ids)")
    override suspend fun markSent(ids: List<Long>, sentAt: Long)
}
