package com.internal.tracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class SqlCipherRoomIntegrationTest {
    @Test
    fun encryptedDatabaseReopensWithSameKeyAndRejectsWrongKey() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "sqlcipher-probe-${UUID.randomUUID()}.db"
            val key = ByteArray(32) { it.toByte() }

            try {
                val writableDatabase = open(context, name, key)
                try {
                    writableDatabase.probeDao().insert(EncryptionProbeEntity(1, "secret"))
                } finally {
                    writableDatabase.close()
                }

                val readableDatabase = open(context, name, key)
                try {
                    assertEquals("secret", readableDatabase.probeDao().value(1))
                } finally {
                    readableDatabase.close()
                }

                assertThrows(Exception::class.java) {
                    val wrongKeyDatabase = open(context, name, ByteArray(32) { 7 })
                    try {
                        wrongKeyDatabase.openHelper.writableDatabase.query(
                            "SELECT value FROM encryption_probe WHERE id = 1",
                        ).use { cursor -> cursor.moveToFirst() }
                    } finally {
                        wrongKeyDatabase.close()
                    }
                }
            } finally {
                context.deleteDatabase(name)
            }
        }
    }

    private fun open(
        context: Context,
        name: String,
        passphrase: ByteArray,
    ): EncryptionProbeDatabase = Room.databaseBuilder(
        context,
        EncryptionProbeDatabase::class.java,
        name,
    )
        .openHelperFactory(SqlCipherFactoryProvider().create(passphrase))
        .build()
}
