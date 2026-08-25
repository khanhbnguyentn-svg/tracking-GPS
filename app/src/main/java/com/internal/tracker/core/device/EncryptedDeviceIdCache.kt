package com.internal.tracker.core.device

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class EncryptedDeviceIdCache(context: Context) : DeviceIdCache {
    private val appContext = context.applicationContext

    private val preferences: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun read(): String? = wrapStorageFailure {
        preferences.getString(KEY, null)?.also(::requireValidDeviceId)
    }

    override fun write(deviceId: String) {
        wrapStorageFailure {
            requireValidDeviceId(deviceId)
            check(preferences.edit().putString(KEY, deviceId).commit()) {
                "Unable to persist SET device identity"
            }
        }
    }

    private fun requireValidDeviceId(deviceId: String) {
        if (!DEVICE_ID_PATTERN.matches(deviceId)) {
            throw DeviceIdentityStorageException("Invalid SET device identity")
        }
    }

    private inline fun <T> wrapStorageFailure(block: () -> T): T = try {
        block()
    } catch (exception: DeviceIdentityStorageException) {
        throw exception
    } catch (exception: Exception) {
        throw DeviceIdentityStorageException("Unable to access SET device identity storage", exception)
    }

    private companion object {
        const val FILE_NAME = "set-device-identity"
        const val KEY = "device-id-v1"
        val DEVICE_ID_PATTERN = Regex("[0-9A-F]{64}")
    }
}
