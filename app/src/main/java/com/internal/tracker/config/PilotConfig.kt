package com.internal.tracker.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.internal.tracker.BuildConfig

data class PilotConfig(
    val deviceNumber: String,
    val recipient: String,
    val intervalHours: Int,
    val sender: String,
    val appPassword: String,
) {
    fun normalized(): PilotConfig = copy(
        deviceNumber = deviceNumber.padStart(3, '0'),
        recipient = recipient.trim(),
        sender = sender.trim(),
        appPassword = appPassword.filterNot(Char::isWhitespace),
    )

    fun isValid(): Boolean {
        val value = normalized()
        return value.deviceNumber.toIntOrNull() in 1..100 &&
            value.deviceNumber.length == 3 &&
            value.intervalHours in setOf(6, 12, 24) &&
            EMAIL.matches(value.recipient) &&
            EMAIL.matches(value.sender) &&
            value.appPassword.length == 16
    }

    private companion object {
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

interface PilotConfigStore {
    fun load(): PilotConfig
    fun save(config: PilotConfig): Result<Unit>
}

@Suppress("DEPRECATION")
class EncryptedPilotConfigStore(context: Context) : PilotConfigStore {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "pilot_config",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun load() = PilotConfig(
        deviceNumber = preferences.getString("device_number", "001") ?: "001",
        recipient = preferences.getString("recipient", "").orEmpty(),
        intervalHours = preferences.getInt("interval_hours", 24),
        sender = preferences.getString("sender", BuildConfig.SMTP_USER).orEmpty(),
        appPassword = preferences.getString("app_password", BuildConfig.SMTP_APP_PASSWORD).orEmpty(),
    ).normalized()

    override fun save(config: PilotConfig): Result<Unit> = runCatching {
        val value = config.normalized()
        require(value.isValid()) { "Cau hinh khong hop le" }
        check(preferences.edit()
            .putString("device_number", value.deviceNumber)
            .putString("recipient", value.recipient)
            .putInt("interval_hours", value.intervalHours)
            .putString("sender", value.sender)
            .putString("app_password", value.appPassword)
            .commit()) { "Khong the luu cau hinh" }
    }
}
