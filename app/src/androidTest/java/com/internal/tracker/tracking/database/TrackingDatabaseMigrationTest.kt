package com.internal.tracker.tracking.database

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackingDatabaseMigrationTest {
    @Test
    fun migrationFromVersion2RetainsRawSequenceAndValues() {
        val name = "tracking-v2-${UUID.randomUUID()}.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE boot_session (id TEXT NOT NULL PRIMARY KEY, reason TEXT NOT NULL, startedAtUtcMillis INTEGER NOT NULL, startedAtElapsedRealtimeNanos INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE sequence_state (id INTEGER NOT NULL PRIMARY KEY, nextSequenceNumber INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE gps_raw_sample (sequenceNumber INTEGER NOT NULL PRIMARY KEY, capturedUtcMillis INTEGER NOT NULL, capturedOffsetMinutes INTEGER NOT NULL, elapsedRealtimeNanos INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, altitudeMeters REAL, horizontalAccuracyMeters REAL, verticalAccuracyMeters REAL, speedMetersPerSecond REAL, speedAccuracyMetersPerSecond REAL, bearingDegrees REAL, bearingAccuracyDegrees REAL, provider TEXT, isMock INTEGER NOT NULL, bootSessionId TEXT NOT NULL, kind TEXT NOT NULL)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.apply {
            execSQL("INSERT INTO boot_session VALUES ('boot-1', 'PROCESS_START', 1, 1)")
            execSQL("INSERT INTO sequence_state VALUES (1, 2)")
            execSQL("INSERT INTO gps_raw_sample VALUES (1, 1000, 420, 10000000000, 10.7769, 106.7009, NULL, 3.5, NULL, 0.5, NULL, NULL, NULL, 'fused', 0, 'boot-1', 'ORDINARY')")
            TrackingMigrations.MIGRATION_2_3.migrate(this)
            query("SELECT sequenceNumber, capturedUtcMillis, latitude, longitude FROM gps_raw_sample").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1L, cursor.getLong(0))
                assertEquals(1000L, cursor.getLong(1))
                assertEquals(10.7769, cursor.getDouble(2), 0.0)
                assertEquals(106.7009, cursor.getDouble(3), 0.0)
            }
            query("SELECT nextSequenceNumber FROM sequence_state WHERE id = 1").use { cursor ->
                cursor.moveToFirst()
                assertEquals(2L, cursor.getLong(0))
            }
            close()
        }
        context.deleteDatabase(name)
    }
}
