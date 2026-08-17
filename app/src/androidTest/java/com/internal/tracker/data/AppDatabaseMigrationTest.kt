package com.internal.tracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun createVersionTwoDatabase() {
        context.deleteDatabase(DATABASE_NAME)
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL(
                """
                CREATE TABLE location_records (
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
            database.execSQL("CREATE INDEX index_location_records_capturedAt ON location_records (capturedAt)")
            database.execSQL("CREATE INDEX index_location_records_state ON location_records (state)")
            database.execSQL("CREATE INDEX index_location_records_source ON location_records (source)")
            database.execSQL(
                """
                INSERT INTO location_records (
                    deviceNumber, deviceId, capturedAt, timezone, latitude, longitude,
                    accuracy, batteryPercent, trackedDurationMillis, source, state,
                    attemptCount, lastError, sentAt
                ) VALUES ('001', 'AND-1', 1000, 'Asia/Bangkok', 10.0, 20.0,
                    5.0, 80, 60000, 'CURRENT', 'PENDING', 0, NULL, NULL)
                """.trimIndent(),
            )
            database.version = 2
        }
    }

    @After
    fun deleteDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrationTwoToThreePreservesRowsWithFinalizedPeriodicDefaults() {
        val room = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        room.openHelper.writableDatabase.query(
            "SELECT recordType, isFinalized FROM location_records WHERE id = 1",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("PERIODIC", cursor.getString(cursor.getColumnIndexOrThrow("recordType")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isFinalized")))
        }
        room.close()
    }

    @Test
    fun migrationThreeToFourPreservesRoutesAndCreatesDiagnosticTables() {
        context.deleteDatabase(DATABASE_NAME)
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL(
                """
                CREATE TABLE location_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, deviceNumber TEXT NOT NULL,
                    deviceId TEXT NOT NULL, capturedAt INTEGER NOT NULL, timezone TEXT NOT NULL,
                    latitude REAL NOT NULL, longitude REAL NOT NULL, accuracy REAL,
                    batteryPercent INTEGER, trackedDurationMillis INTEGER NOT NULL,
                    source TEXT NOT NULL, state TEXT NOT NULL, attemptCount INTEGER NOT NULL,
                    lastError TEXT, sentAt INTEGER, recordType TEXT NOT NULL,
                    isFinalized INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE INDEX index_location_records_capturedAt ON location_records (capturedAt)")
            database.execSQL("CREATE INDEX index_location_records_state ON location_records (state)")
            database.execSQL("CREATE INDEX index_location_records_source ON location_records (source)")
            database.execSQL("INSERT INTO location_records VALUES (1,'001','AND-1',1000,'Asia/Bangkok',10.0,20.0,5.0,80,60000,'CURRENT','PENDING',0,NULL,NULL,'START',1)")
            database.version = 3
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        assertEquals(1, runBlocking { room.locationRecordDao().count() })
        room.openHelper.writableDatabase.query("SELECT COUNT(*) FROM diagnostic_incidents").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        room.close()
    }

    private companion object {
        const val DATABASE_NAME = "migration-v2-v3.db"
    }
}
