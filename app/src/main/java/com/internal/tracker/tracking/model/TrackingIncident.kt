package com.internal.tracker.tracking.model

enum class TrackingIncidentType {
    GPS_GAP,
    PERMISSION,
    PROVIDER,
    SERVICE,
    PERSISTENCE,
    CLOCK_CHANGE,
}

data class TrackingIncident(
    val id: Long,
    val type: TrackingIncidentType,
    val openedAtUtcMillis: Long,
    val closedAtUtcMillis: Long?,
)
