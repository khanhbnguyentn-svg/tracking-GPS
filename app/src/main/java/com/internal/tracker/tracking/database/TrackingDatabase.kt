package com.internal.tracker.tracking.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TrackingDatabaseMetadataEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TrackingDatabase : RoomDatabase()
