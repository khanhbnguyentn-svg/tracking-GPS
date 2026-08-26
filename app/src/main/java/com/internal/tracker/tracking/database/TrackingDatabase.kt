package com.internal.tracker.tracking.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackingDatabaseMetadataEntity::class,
        BootSessionEntity::class,
        SequenceStateEntity::class,
        RawGpsSampleEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class TrackingDatabase : RoomDatabase() {
    abstract fun trackingDao(): TrackingDao
}
