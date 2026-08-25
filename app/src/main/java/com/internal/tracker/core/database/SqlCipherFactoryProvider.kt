package com.internal.tracker.core.database

import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class SqlCipherFactoryProvider {
    fun create(passphrase: ByteArray): SupportSQLiteOpenHelper.Factory =
        SupportOpenHelperFactory(passphrase.copyOf())
}
