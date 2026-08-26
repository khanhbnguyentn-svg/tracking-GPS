package com.internal.tracker.tracking.model

data class RawLocationSample(
    val capturedUtcMillis: Long,
    val capturedOffsetMinutes: Int,
    val elapsedRealtimeNanos: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val horizontalAccuracyMeters: Float?,
    val verticalAccuracyMeters: Float?,
    val speedMetersPerSecond: Float?,
    val speedAccuracyMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val bearingAccuracyDegrees: Float?,
    val provider: String?,
    val isMock: Boolean,
)

enum class BootReason {
    PROCESS_START,
    BOOT,
    PACKAGE_REPLACED,
}

data class PersistedRawSample(
    val sequenceNumber: Long,
    val sample: RawLocationSample,
)
