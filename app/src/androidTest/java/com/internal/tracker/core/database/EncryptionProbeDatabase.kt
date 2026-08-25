package com.internal.tracker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [EncryptionProbeEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class EncryptionProbeDatabase : RoomDatabase() {
    abstract fun probeDao(): EncryptionProbeDao
}
