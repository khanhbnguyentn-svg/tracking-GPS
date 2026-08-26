package com.internal.tracker.tracking.database

import androidx.sqlite.db.SupportSQLiteDatabase

object DatabasePragmas {
    fun configure(database: SupportSQLiteDatabase) {
        database.execSQL("PRAGMA foreign_keys=ON")
        database.execSQL("PRAGMA auto_vacuum=INCREMENTAL")
    }

    fun ensureIncrementalAutoVacuum(database: SupportSQLiteDatabase) {
        val autoVacuum = database.query("PRAGMA auto_vacuum").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
        if (autoVacuum != INCREMENTAL) {
            database.execSQL("PRAGMA auto_vacuum=INCREMENTAL")
            database.execSQL("VACUUM")
        }
    }

    private const val INCREMENTAL = 2
}
