package com.internal.tracker.tracking.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracking_database_metadata")
data class TrackingDatabaseMetadataEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val createdAtUtcMillis: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
