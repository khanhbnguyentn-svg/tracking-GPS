package com.internal.tracker.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.internal.tracker.history.LocationRecord
import com.internal.tracker.history.LocationRecordDao

@Database(entities = [LocationRecord::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationRecordDao(): LocationRecordDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS location_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        deviceNumber TEXT NOT NULL,
                        deviceId TEXT NOT NULL,
                        capturedAt INTEGER NOT NULL,
                        timezone TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        accuracy REAL,
                        batteryPercent INTEGER,
                        trackedDurationMillis INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        state TEXT NOT NULL,
                        attemptCount INTEGER NOT NULL,
                        lastError TEXT,
                        sentAt INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_location_records_capturedAt ON location_records (capturedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_location_records_state ON location_records (state)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_location_records_source ON location_records (source)")
                db.execSQL(
                    """
                    INSERT INTO location_records (
                        deviceNumber, deviceId, capturedAt, timezone, latitude, longitude,
                        accuracy, batteryPercent, trackedDurationMillis, source, state,
                        attemptCount, lastError, sentAt
                    )
                    SELECT '000', 'LEGACY-' || id, timestamp, 'UTC', latitude, longitude,
                        accuracy, NULL, 0, 'LEGACY_IMPORT', 'PENDING', retryCount, NULL, NULL
                    FROM pending_locations
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE location_records ADD COLUMN recordType TEXT NOT NULL DEFAULT 'PERIODIC'",
                )
                db.execSQL(
                    "ALTER TABLE location_records ADD COLUMN isFinalized INTEGER NOT NULL DEFAULT 1",
                )
            }
        }
    }
}
