package com.internal.tracker.tracking.health

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.internal.tracker.tracking.model.TrackingIncidentType

data class PendingIncidentRecovery(val type: TrackingIncidentType, val openedAtUtcMillis: Long, val closedAtUtcMillis: Long?)

class IncidentRecoveryMarker(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(context, "tracking-incident-recovery", MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(), EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun write(type: TrackingIncidentType, openedAtUtcMillis: Long, closedAtUtcMillis: Long?) { preferences.edit().putString("type", type.name).putLong("opened", openedAtUtcMillis).apply { if (closedAtUtcMillis == null) remove("closed") else putLong("closed", closedAtUtcMillis) }.commit() }
    fun read(): PendingIncidentRecovery? { val type = preferences.getString("type", null) ?: return null; return PendingIncidentRecovery(TrackingIncidentType.valueOf(type), preferences.getLong("opened", 0L), if (preferences.contains("closed")) preferences.getLong("closed", 0L) else null) }
    fun clear() { preferences.edit().clear().commit() }
}
