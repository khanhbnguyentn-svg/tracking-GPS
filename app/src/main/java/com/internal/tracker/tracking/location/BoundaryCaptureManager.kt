package com.internal.tracker.tracking.location

import java.util.UUID

sealed interface BoundaryCaptureResult {
    data class Captured(val requestId: String, val capturedAtUtcMillis: Long) : BoundaryCaptureResult
    data class Unavailable(val requestId: String) : BoundaryCaptureResult
}

class BoundaryCaptureManager {
    private val pending = mutableMapOf<String, Long>()
    fun begin(actionUtcMillis: Long): String = UUID.randomUUID().toString().also { pending[it] = actionUtcMillis }
    fun accept(requestId: String, capturedUtcMillis: Long): BoundaryCaptureResult? {
        val action = pending[requestId] ?: return null
        if (capturedUtcMillis < action) return null
        pending.remove(requestId)
        return BoundaryCaptureResult.Captured(requestId, capturedUtcMillis)
    }
    fun timeout(requestId: String): BoundaryCaptureResult? = if (pending.remove(requestId) != null) BoundaryCaptureResult.Unavailable(requestId) else null
}
