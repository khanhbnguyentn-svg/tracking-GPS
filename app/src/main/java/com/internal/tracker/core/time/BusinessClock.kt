package com.internal.tracker.core.time

import java.time.Instant

interface BusinessClock {
    fun now(): Instant

    fun elapsedRealtimeNanos(): Long
}
