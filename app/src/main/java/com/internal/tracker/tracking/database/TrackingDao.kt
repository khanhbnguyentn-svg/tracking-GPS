package com.internal.tracker.tracking.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrackingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertBootSession(entity: BootSessionEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSequenceState(entity: SequenceStateEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRawSample(entity: RawGpsSampleEntity)
    @Query("SELECT nextSequenceNumber FROM sequence_state WHERE id = :id") suspend fun nextSequenceNumber(id: Int = SequenceStateEntity.SINGLETON_ID): Long
    @Query("UPDATE sequence_state SET nextSequenceNumber = nextSequenceNumber + 1 WHERE id = :id") suspend fun advanceSequenceNumber(id: Int = SequenceStateEntity.SINGLETON_ID)
    @Query("SELECT elapsedRealtimeNanos FROM gps_raw_sample WHERE bootSessionId = :bootSessionId AND kind = 'ORDINARY' ORDER BY sequenceNumber DESC LIMIT 1") suspend fun latestOrdinaryElapsedRealtimeNanos(bootSessionId: String): Long?
    @Query("SELECT * FROM gps_raw_sample ORDER BY sequenceNumber") suspend fun rawSamples(): List<RawGpsSampleEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMovementEvent(entity: MovementEventEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertIncident(entity: TrackingIncidentEntity): Long
    @Query("UPDATE tracking_incident SET closedAtUtcMillis = :closedAtUtcMillis WHERE id = :id") suspend fun closeIncident(id: Long, closedAtUtcMillis: Long)
    @Query("SELECT * FROM tracking_incident WHERE type = :type AND closedAtUtcMillis IS NULL ORDER BY id LIMIT 1") suspend fun openIncident(type: String): TrackingIncidentEntity?
    @Query("SELECT * FROM movement_event ORDER BY id") suspend fun movementEvents(): List<MovementEventEntity>
    @Query("SELECT * FROM tracking_incident ORDER BY id") suspend fun incidents(): List<TrackingIncidentEntity>
    @Query("DELETE FROM movement_event WHERE effectiveAtUtcMillis >= :fromUtcMillis AND effectiveAtUtcMillis < :toUtcMillis") suspend fun deleteMovementEventsInRange(fromUtcMillis: Long, toUtcMillis: Long): Int
    @Query("DELETE FROM gps_raw_sample WHERE capturedUtcMillis >= :fromUtcMillis AND capturedUtcMillis < :toUtcMillis") suspend fun deleteRawSamplesInRange(fromUtcMillis: Long, toUtcMillis: Long): Int
}
