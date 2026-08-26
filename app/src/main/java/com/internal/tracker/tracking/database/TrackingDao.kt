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
}
