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
    fun observeOldestCapturedAt(): Flow<Long?>
    suspend fun between(from: Long, until: Long): List<LocationRecord>
    suspend fun activeStopCandidate(since: Long): LocationRecord?
    suspend fun latestSince(since: Long): LocationRecord?
    suspend fun deleteOlderThan(beforeMillis: Long)
    suspend fun deleteBetween(from: Long, until: Long)
    suspend fun deleteAll()
    suspend fun finalizeStopCandidate(id: Long, recordType: RecordType)
    suspend fun markRetrying(ids: List<Long>, error: String)
    suspend fun markSent(ids: List<Long>, sentAt: Long)
}

@Dao
interface LocationRecordDao : LocationRecordStore {
    @Insert
    override suspend fun insert(record: LocationRecord): Long

    @Query("SELECT * FROM location_records WHERE id = :id")
    override suspend fun get(id: Long): LocationRecord?

    @Query("SELECT * FROM location_records WHERE state != 'SENT' AND isFinalized = 1 ORDER BY capturedAt, id LIMIT :limit")
    override suspend fun unsent(limit: Int): List<LocationRecord>

    @Query("SELECT COUNT(*) FROM location_records")
    override suspend fun count(): Int

    @Query("SELECT * FROM location_records WHERE capturedAt >= :from AND capturedAt < :until ORDER BY capturedAt DESC, id DESC")
    override fun observeBetween(from: Long, until: Long): Flow<List<LocationRecord>>

    @Query("SELECT MIN(capturedAt) FROM location_records")
    override fun observeOldestCapturedAt(): Flow<Long?>

    @Query("SELECT * FROM location_records WHERE capturedAt >= :from AND capturedAt < :until ORDER BY capturedAt, id")
    override suspend fun between(from: Long, until: Long): List<LocationRecord>

    @Query("SELECT * FROM location_records WHERE recordType = 'TEMP_STOP' AND isFinalized = 0 AND capturedAt >= :since ORDER BY capturedAt DESC, id DESC LIMIT 1")
    override suspend fun activeStopCandidate(since: Long): LocationRecord?

    @Query("SELECT * FROM location_records WHERE capturedAt >= :since ORDER BY capturedAt DESC, id DESC LIMIT 1")
    override suspend fun latestSince(since: Long): LocationRecord?

    @Query("DELETE FROM location_records WHERE capturedAt < :beforeMillis")
    override suspend fun deleteOlderThan(beforeMillis: Long)

    @Query("DELETE FROM location_records WHERE capturedAt >= :from AND capturedAt < :until")
    override suspend fun deleteBetween(from: Long, until: Long)

    @Query("DELETE FROM location_records")
    override suspend fun deleteAll()

    @Query("UPDATE location_records SET recordType = :recordType, isFinalized = 1 WHERE id = :id AND isFinalized = 0")
    override suspend fun finalizeStopCandidate(id: Long, recordType: RecordType)

    @Query("UPDATE location_records SET state = 'RETRYING', attemptCount = attemptCount + 1, lastError = :error WHERE id IN (:ids)")
    override suspend fun markRetrying(ids: List<Long>, error: String)

    @Query("UPDATE location_records SET state = 'SENT', sentAt = :sentAt, lastError = NULL WHERE id IN (:ids)")
    override suspend fun markSent(ids: List<Long>, sentAt: Long)
}
