package com.internal.tracker.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DeliveryState { PENDING, SENT, RETRYING }
enum class RecordSource { CURRENT, LEGACY_IMPORT }

data class CapturedLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val capturedAt: Long,
    val timezone: String,
)

@Entity(
    tableName = "location_records",
    indices = [Index("capturedAt"), Index("state"), Index("source")],
)
data class LocationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceNumber: String,
    val deviceId: String,
    val capturedAt: Long,
    val timezone: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val batteryPercent: Int?,
    val trackedDurationMillis: Long,
    val source: RecordSource = RecordSource.CURRENT,
    val state: DeliveryState = DeliveryState.PENDING,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val sentAt: Long? = null,
)
