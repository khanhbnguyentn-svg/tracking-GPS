package com.internal.tracker.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.internal.tracker.history.LocationRecord
import com.internal.tracker.history.LocationRecordDao
import com.internal.tracker.diagnostics.DiagnosticDao
import com.internal.tracker.diagnostics.DiagnosticIncident
import com.internal.tracker.diagnostics.DiagnosticSample

@Database(entities = [LocationRecord::class, DiagnosticIncident::class, DiagnosticSample::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationRecordDao(): LocationRecordDao
    abstract fun diagnosticDao(): DiagnosticDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS diagnostic_incidents (
                        incidentId TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL, reasonCodes TEXT NOT NULL,
                        openedAt INTEGER NOT NULL, recoveredAt INTEGER,
                        state TEXT NOT NULL, confidenceScore INTEGER NOT NULL,
                        confidenceBand TEXT NOT NULL, deviceCondition TEXT NOT NULL,
                        evidenceComplete INTEGER NOT NULL,
                        lastCapturedAt INTEGER, lastLatitude REAL, lastLongitude REAL,
                        firstCapturedAt INTEGER, firstLatitude REAL, firstLongitude REAL,
                        openedAlertState TEXT NOT NULL, openedAlertAttempts INTEGER NOT NULL,
                        openedAlertSentAt INTEGER, openedAlertError TEXT,
                        recoveredAlertState TEXT NOT NULL, recoveredAlertAttempts INTEGER NOT NULL,
                        recoveredAlertSentAt INTEGER, recoveredAlertError TEXT,
                        reportedAt INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_incidents_openedAt ON diagnostic_incidents (openedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_incidents_reportedAt ON diagnostic_incidents (reportedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_incidents_type ON diagnostic_incidents (type)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS diagnostic_samples (
                        incidentId TEXT NOT NULL, sequence INTEGER NOT NULL,
                        role TEXT NOT NULL, capturedAt INTEGER NOT NULL, receivedAt INTEGER NOT NULL,
                        latitude REAL NOT NULL, longitude REAL NOT NULL, accuracy REAL,
                        speedMetersPerSecond REAL, derivedDistanceMeters REAL,
                        derivedSpeedMetersPerSecond REAL, signalFlags TEXT NOT NULL,
                        PRIMARY KEY (incidentId, sequence),
                        FOREIGN KEY (incidentId) REFERENCES diagnostic_incidents(incidentId) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_samples_incidentId ON diagnostic_samples (incidentId)")
            }
        }
    }
}
