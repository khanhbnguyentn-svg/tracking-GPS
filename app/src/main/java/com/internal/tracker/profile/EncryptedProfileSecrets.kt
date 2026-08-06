package com.internal.tracker.profile

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

@Suppress("DEPRECATION")
class EncryptedProfileSecrets(context: Context) : ProfileSecrets {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "profile_secrets",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun get(id: Long): ProfileSecret? = preferences.getString(id.toString(), null)?.let { raw ->
        JSONObject(raw).let { json ->
            ProfileSecret(
                host = json.getString("host"),
                certificatePin = json.optString("pin").takeIf(String::isNotBlank),
                customCa = json.optString("ca").takeIf(String::isNotBlank)?.let { Base64.decode(it, Base64.NO_WRAP) },
            )
        }
    }

    override fun put(id: Long, value: ProfileSecret) {
        val json = JSONObject().put("host", value.host)
        value.certificatePin?.let { json.put("pin", it) }
        value.customCa?.let { json.put("ca", Base64.encodeToString(it, Base64.NO_WRAP)) }
        preferences.edit().putString(id.toString(), json.toString()).apply()
    }

    override fun delete(id: Long) {
        preferences.edit().remove(id.toString()).apply()
    }
}
