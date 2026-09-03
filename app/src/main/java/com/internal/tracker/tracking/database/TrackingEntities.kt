package com.internal.tracker.tracking.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "boot_session")
data class BootSessionEntity(
    @PrimaryKey val id: String,
    val reason: String,
    val startedAtUtcMillis: Long,
    val startedAtElapsedRealtimeNanos: Long,
)

@Entity(tableName = "sequence_state")
data class SequenceStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val nextSequenceNumber: Long = 1L,
) {
    companion object { const val SINGLETON_ID = 1 }
}

@Entity(
    tableName = "gps_raw_sample",
    foreignKeys = [ForeignKey(entity = BootSessionEntity::class, parentColumns = ["id"], childColumns = ["bootSessionId"])],
    indices = [Index(value = ["capturedUtcMillis"]), Index(value = ["bootSessionId", "elapsedRealtimeNanos"])],
)
data class RawGpsSampleEntity(
    @PrimaryKey val sequenceNumber: Long,
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
    val bootSessionId: String,
    val kind: String,
)

@Entity(
    tableName = "movement_event",
    foreignKeys = [
        ForeignKey(
            entity = RawGpsSampleEntity::class,
            parentColumns = ["sequenceNumber"],
            childColumns = ["firstSourceSequenceNumber"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RawGpsSampleEntity::class,
            parentColumns = ["sequenceNumber"],
            childColumns = ["confirmingSourceSequenceNumber"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["effectiveAtUtcMillis"]),
        Index(value = ["firstSourceSequenceNumber"]),
        Index(value = ["confirmingSourceSequenceNumber"]),
    ],
)
data class MovementEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val effectiveAtUtcMillis: Long,
    val confirmedAtUtcMillis: Long,
    val firstSourceSequenceNumber: Long,
    val confirmingSourceSequenceNumber: Long,
    val algorithmVersion: Int,
)

@Entity(tableName = "tracking_incident", indices = [Index(value = ["openedAtUtcMillis"])])
data class TrackingIncidentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val openedAtUtcMillis: Long,
    val closedAtUtcMillis: Long?,
)
