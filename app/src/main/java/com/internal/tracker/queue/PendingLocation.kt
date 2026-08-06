package com.internal.tracker.queue

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val speed: Double,
    val accuracy: Double,
)

@Entity(tableName = "pending_locations", indices = [Index("timestamp")])
data class PendingLocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val speed: Double,
    val accuracy: Double,
    val retryCount: Int = 0,
)
