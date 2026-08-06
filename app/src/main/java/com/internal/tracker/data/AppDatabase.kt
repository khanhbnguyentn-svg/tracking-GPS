package com.internal.tracker.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.internal.tracker.profile.ProfileDao
import com.internal.tracker.profile.ProfileEntity

@Database(entities = [ProfileEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
}
