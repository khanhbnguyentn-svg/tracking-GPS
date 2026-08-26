package com.internal.tracker.core.platform

import android.content.Context
import android.provider.Settings
import com.internal.tracker.core.database.SqlCipherFactoryProvider
import com.internal.tracker.core.device.DeviceIdHasher
import com.internal.tracker.core.device.EncryptedDeviceIdCache
import com.internal.tracker.core.device.StableDeviceIdProvider
import com.internal.tracker.core.id.RandomUuidSource
import com.internal.tracker.core.security.AndroidDatabasePassphraseStore
import com.internal.tracker.core.time.SystemBusinessClock

object SetPlatformFactory {
    fun create(context: Context): SetPlatformModule {
        val appContext = context.applicationContext
        return SetPlatformModule(
            clock = SystemBusinessClock,
            uuidSource = RandomUuidSource,
            deviceIdProvider = StableDeviceIdProvider(
                androidId = {
                    Settings.Secure.getString(
                        appContext.contentResolver,
                        Settings.Secure.ANDROID_ID,
                    )
                },
                applicationId = appContext.packageName,
                hasher = DeviceIdHasher(),
                cache = EncryptedDeviceIdCache(appContext),
            ),
            databasePassphraseStore = AndroidDatabasePassphraseStore(appContext),
            sqlCipherFactoryProvider = SqlCipherFactoryProvider(),
        )
    }
}
