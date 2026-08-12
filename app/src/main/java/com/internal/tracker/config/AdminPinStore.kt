package com.internal.tracker.config

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PinRecord(val salt: ByteArray, val hash: ByteArray)

interface PinPreferences {
    fun read(): PinRecord?
    fun write(record: PinRecord)
}

class AdminPinStore(private val preferences: PinPreferences) {
    fun verify(candidate: String): Boolean {
        val record = preferences.read() ?: DEFAULT_RECORD
        return MessageDigest.isEqual(record.hash, derive(candidate, record.salt))
    }

    fun change(current: String, replacement: String): Result<Unit> = runCatching {
        require(verify(current)) { "PIN hien tai khong dung" }
        require(replacement.matches(Regex("\\d{8}"))) { "PIN moi phai gom 8 chu so" }
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        preferences.write(PinRecord(salt, derive(replacement, salt)))
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private companion object {
        const val ITERATIONS = 120_000
        val DEFAULT_RECORD = PinRecord(
            salt = java.util.Base64.getDecoder().decode("AQIDBAUGBwgJCgsMDQ4PEA=="),
            hash = java.util.Base64.getDecoder().decode("esSi9vsxohKpJWx+WBkk+SlxZe1Sf1QcbqiTKFbdPq4="),
        )
    }
}

@Suppress("DEPRECATION")
class EncryptedPinPreferences(context: Context) : PinPreferences {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "admin_pin",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun read(): PinRecord? {
        val salt = preferences.getString("salt", null) ?: return null
        val hash = preferences.getString("hash", null) ?: return null
        return PinRecord(Base64.decode(salt, Base64.NO_WRAP), Base64.decode(hash, Base64.NO_WRAP))
    }

    override fun write(record: PinRecord) {
        check(preferences.edit()
            .putString("salt", Base64.encodeToString(record.salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(record.hash, Base64.NO_WRAP))
            .commit()) { "Khong the luu PIN" }
    }
}
