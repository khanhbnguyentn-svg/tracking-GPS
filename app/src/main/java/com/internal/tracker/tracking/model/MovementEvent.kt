package com.internal.tracker.tracking.model

enum class MovementState {
    MOVING,
    STOP_CANDIDATE,
    TEMP_STOP,
}

enum class MovementEventType {
    TEMP_STOP_STARTED,
    TEMP_STOP_ENDED,
    RESUME,
}

data class MovementEvent(
    val type: MovementEventType,
    val effectiveAtUtcMillis: Long,
    val confirmedAtUtcMillis: Long,
    val firstSourceSequenceNumber: Long,
    val confirmingSourceSequenceNumber: Long,
    val algorithmVersion: Int = 1,
)
