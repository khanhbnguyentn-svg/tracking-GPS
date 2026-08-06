package com.internal.tracker.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.internal.tracker.profile.ProfileDao
import com.internal.tracker.profile.ProfileEntity
import com.internal.tracker.queue.PendingLocation
import com.internal.tracker.queue.PendingLocationDao

@Database(entities = [ProfileEntity::class, PendingLocation::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun pendingLocationDao(): PendingLocationDao
}
