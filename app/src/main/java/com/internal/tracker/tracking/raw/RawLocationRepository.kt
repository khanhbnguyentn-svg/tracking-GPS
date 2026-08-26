package com.internal.tracker.tracking.raw

import com.internal.tracker.tracking.model.BootReason
import com.internal.tracker.tracking.model.PersistedRawSample
import com.internal.tracker.tracking.model.RawLocationSample
import com.internal.tracker.tracking.model.RawSampleKind

interface RawLocationRepository {
    suspend fun startBootSession(reason: BootReason, nowUtcMillis: Long, nowElapsedNanos: Long): String
    suspend fun persistOrdinary(sample: RawLocationSample, bootSessionId: String): PersistedRawSample?
    suspend fun persistBoundary(
        sample: RawLocationSample,
        bootSessionId: String,
        kind: RawSampleKind,
    ): PersistedRawSample
}
