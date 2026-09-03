package com.internal.tracker.tracking.health

import com.internal.tracker.tracking.model.HealthDirective

class TrackingHealthMonitor {
    private var lastLocationCallbackNanos: Long? = null
    private var gapOpenedAtNanos: Long? = null
    private var reregisteredForOpenGap = false

    fun onStarted(nowElapsedNanos: Long) {
        lastLocationCallbackNanos = nowElapsedNanos
        gapOpenedAtNanos = null
        reregisteredForOpenGap = false
    }

    fun onLocationCallback(nowElapsedNanos: Long): HealthDirective {
        lastLocationCallbackNanos = nowElapsedNanos
        val hadGap = gapOpenedAtNanos != null
        gapOpenedAtNanos = null
        reregisteredForOpenGap = false
        return if (hadGap) HealthDirective.CloseGap else HealthDirective.None
    }

    fun onTick(nowElapsedNanos: Long): HealthDirective {
        val lastCallback = lastLocationCallbackNanos ?: return HealthDirective.None
        val openGapAt = gapOpenedAtNanos
        if (openGapAt == null) {
            if (nowElapsedNanos - lastCallback < GAP_OPEN_NANOS) return HealthDirective.None
            gapOpenedAtNanos = nowElapsedNanos
            return HealthDirective.OpenGap(reRegisterNow = true)
        }
        if (!reregisteredForOpenGap && nowElapsedNanos - openGapAt >= REREGISTER_AFTER_GAP_NANOS) {
            reregisteredForOpenGap = true
            return HealthDirective.ReRegister
        }
        return HealthDirective.None
    }

    private companion object {
        const val GAP_OPEN_NANOS = 30_000_000_000L
        const val REREGISTER_AFTER_GAP_NANOS = 300_000_000_000L
    }
}
