package com.internal.tracker.tracking.raw

import androidx.room.withTransaction
import com.internal.tracker.tracking.database.BootSessionEntity
import com.internal.tracker.tracking.database.RawGpsSampleEntity
import com.internal.tracker.tracking.database.SequenceStateEntity
import com.internal.tracker.tracking.database.TrackingDatabase
import com.internal.tracker.tracking.model.BootReason
import com.internal.tracker.tracking.model.PersistedRawSample
import com.internal.tracker.tracking.model.RawLocationSample
import com.internal.tracker.tracking.model.RawSampleKind
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomRawLocationRepository(
    private val database: TrackingDatabase,
    private val gate: RawSampleGate = RawSampleGate(),
) : RawLocationRepository {
    private val mutex = Mutex()

    override suspend fun startBootSession(reason: BootReason, nowUtcMillis: Long, nowElapsedNanos: Long): String = mutex.withLock {
        val id = UUID.randomUUID().toString()
        database.withTransaction {
            database.trackingDao().insertSequenceState(SequenceStateEntity())
            database.trackingDao().insertBootSession(BootSessionEntity(id, reason.name, nowUtcMillis, nowElapsedNanos))
        }
        id
    }

    override suspend fun persistOrdinary(sample: RawLocationSample, bootSessionId: String): PersistedRawSample? =
        persist(sample, bootSessionId, RawSampleKind.ORDINARY)

    override suspend fun persistBoundary(
        sample: RawLocationSample,
        bootSessionId: String,
        kind: RawSampleKind,
    ): PersistedRawSample {
        require(kind != RawSampleKind.ORDINARY)
        return checkNotNull(persist(sample, bootSessionId, kind))
    }

    private suspend fun persist(sample: RawLocationSample, bootSessionId: String, kind: RawSampleKind): PersistedRawSample? = mutex.withLock {
        database.withTransaction {
            val dao = database.trackingDao()
            val previous = dao.latestOrdinaryElapsedRealtimeNanos(bootSessionId)
            if (!gate.shouldPersist(sample.elapsedRealtimeNanos, previous, kind)) return@withTransaction null
            val sequenceNumber = dao.nextSequenceNumber()
            dao.insertRawSample(sample.toEntity(sequenceNumber, bootSessionId, kind))
            dao.advanceSequenceNumber()
            PersistedRawSample(sequenceNumber, sample)
        }
    }

    private fun RawLocationSample.toEntity(sequenceNumber: Long, bootSessionId: String, kind: RawSampleKind) = RawGpsSampleEntity(
        sequenceNumber, capturedUtcMillis, capturedOffsetMinutes, elapsedRealtimeNanos, latitude, longitude,
        altitudeMeters, horizontalAccuracyMeters, verticalAccuracyMeters, speedMetersPerSecond,
        speedAccuracyMetersPerSecond, bearingDegrees, bearingAccuracyDegrees, provider, isMock, bootSessionId, kind.name,
    )
}
