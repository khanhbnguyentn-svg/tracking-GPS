package com.internal.tracker.tracking.raw

import com.internal.tracker.tracking.model.RawSampleKind

class RawSampleGate {
    fun shouldPersist(
        elapsedRealtimeNanos: Long,
        previousOrdinaryElapsedRealtimeNanos: Long?,
        kind: RawSampleKind,
    ): Boolean {
        if (kind != RawSampleKind.ORDINARY) return true
        if (previousOrdinaryElapsedRealtimeNanos == null) return true
        return elapsedRealtimeNanos > previousOrdinaryElapsedRealtimeNanos &&
            elapsedRealtimeNanos - previousOrdinaryElapsedRealtimeNanos >= TEN_SECONDS_NANOS
    }

    private companion object {
        const val TEN_SECONDS_NANOS = 10_000_000_000L
    }
}
