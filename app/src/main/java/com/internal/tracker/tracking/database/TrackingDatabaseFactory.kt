package com.internal.tracker.tracking.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.internal.tracker.core.database.SqlCipherFactoryProvider
import com.internal.tracker.core.security.DatabaseKeyResult
import com.internal.tracker.core.security.DatabasePassphraseStore

class TrackingDatabaseFactory(
    private val context: Context,
    private val passphraseStore: DatabasePassphraseStore,
    private val factoryProvider: SqlCipherFactoryProvider,
) {
    fun open(name: String = DATABASE_NAME): TrackingDatabase {
        val passphrase = when (val keyResult = passphraseStore.getOrCreate()) {
            is DatabaseKeyResult.Ready -> keyResult.passphrase
            is DatabaseKeyResult.RecoveryRequired -> error("Tracking database recovery required: ${keyResult.reason}")
        }

        return try {
            Room.databaseBuilder(context, TrackingDatabase::class.java, name)
                .openHelperFactory(
                    ConfiguringOpenHelperFactory(factoryProvider.create(passphrase)),
                )
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(TrackingMigrations.MIGRATION_2_3)
                .build()
        } finally {
            passphrase.fill(0)
        }
    }

    private companion object {
        const val DATABASE_NAME = "tracking-set.db"
    }
}

private class ConfiguringOpenHelperFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory,
) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        delegate.create(
            SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(PragmaConfiguringCallback(configuration.callback))
                .build(),
        )
}

private class PragmaConfiguringCallback(
    private val delegate: SupportSQLiteOpenHelper.Callback,
) : SupportSQLiteOpenHelper.Callback(delegate.version) {
    override fun onConfigure(db: SupportSQLiteDatabase) {
        DatabasePragmas.configure(db)
        delegate.onConfigure(db)
    }

    override fun onCreate(db: SupportSQLiteDatabase) = delegate.onCreate(db)

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
        delegate.onUpgrade(db, oldVersion, newVersion)

    override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
        delegate.onDowngrade(db, oldVersion, newVersion)

    override fun onOpen(db: SupportSQLiteDatabase) {
        delegate.onOpen(db)
        DatabasePragmas.ensureIncrementalAutoVacuum(db)
    }

    override fun onCorruption(db: SupportSQLiteDatabase) = delegate.onCorruption(db)
}
