package com.internal.tracker.tracking.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TrackingMigrations {
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS movement_event (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    type TEXT NOT NULL,
                    effectiveAtUtcMillis INTEGER NOT NULL,
                    confirmedAtUtcMillis INTEGER NOT NULL,
                    firstSourceSequenceNumber INTEGER NOT NULL,
                    confirmingSourceSequenceNumber INTEGER NOT NULL,
                    algorithmVersion INTEGER NOT NULL,
                    FOREIGN KEY(firstSourceSequenceNumber) REFERENCES gps_raw_sample(sequenceNumber)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(confirmingSourceSequenceNumber) REFERENCES gps_raw_sample(sequenceNumber)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_movement_event_effectiveAtUtcMillis ON movement_event(effectiveAtUtcMillis)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_movement_event_firstSourceSequenceNumber ON movement_event(firstSourceSequenceNumber)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_movement_event_confirmingSourceSequenceNumber ON movement_event(confirmingSourceSequenceNumber)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS tracking_incident (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    type TEXT NOT NULL,
                    openedAtUtcMillis INTEGER NOT NULL,
                    closedAtUtcMillis INTEGER
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_tracking_incident_openedAtUtcMillis ON tracking_incident(openedAtUtcMillis)",
            )
        }
    }
}
