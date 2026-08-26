package com.internal.tracker.tracking.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.internal.tracker.core.database.SqlCipherFactoryProvider
import com.internal.tracker.core.security.DatabaseKeyResult
import com.internal.tracker.core.security.DatabasePassphraseStore
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackingDatabaseIntegrationTest {
    @Test
    fun opensProductionDatabaseWithSqlCipherWalAndForeignKeys() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "tracking-production-${UUID.randomUUID()}.db"
        val factory = TrackingDatabaseFactory(
            context = context,
            passphraseStore = DatabasePassphraseStore {
                DatabaseKeyResult.Ready(ByteArray(32) { it.toByte() })
            },
            factoryProvider = SqlCipherFactoryProvider(),
        )

        try {
            val database = factory.open(name)
            try {
                val sqlite = database.openHelper.writableDatabase
                assertEquals(
                    "wal",
                    sqlite.query("PRAGMA journal_mode").use { cursor ->
                        cursor.moveToFirst()
                        cursor.getString(0).lowercase()
                    },
                )
                assertEquals(
                    1,
                    sqlite.query("PRAGMA foreign_keys").use { cursor ->
                        cursor.moveToFirst()
                        cursor.getInt(0)
                    },
                )
                assertEquals(
                    2,
                    sqlite.query("PRAGMA auto_vacuum").use { cursor ->
                        cursor.moveToFirst()
                        cursor.getInt(0)
                    },
                )
            } finally {
                database.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
