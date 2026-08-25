package com.internal.tracker.core.time

import android.os.SystemClock
import java.time.Instant

object SystemBusinessClock : BusinessClock {
    override fun now(): Instant = Instant.now()

    override fun elapsedRealtimeNanos(): Long = SystemClock.elapsedRealtimeNanos()
}
