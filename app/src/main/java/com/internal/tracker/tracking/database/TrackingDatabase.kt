package com.internal.tracker.tracking.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackingDatabaseMetadataEntity::class,
        BootSessionEntity::class,
        SequenceStateEntity::class,
        RawGpsSampleEntity::class,
        MovementEventEntity::class,
        TrackingIncidentEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class TrackingDatabase : RoomDatabase() {
    abstract fun trackingDao(): TrackingDao
}
