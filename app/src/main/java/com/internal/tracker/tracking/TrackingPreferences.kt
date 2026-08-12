package com.internal.tracker.tracking

import android.content.Context

class TrackingPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean("enabled", false)
        set(value) = preferences.edit().putBoolean("enabled", value).apply()

    var lastLocationTime: Long
        get() = preferences.getLong("last_location", 0)
        set(value) = preferences.edit().putLong("last_location", value).apply()

    var lastSendTime: Long
        get() = preferences.getLong("last_send", 0)
        set(value) = preferences.edit().putLong("last_send", value).apply()

    var startedAt: Long
        get() = preferences.getLong("started_at", 0)
        set(value) = preferences.edit().putLong("started_at", value).apply()

    var lastError: String?
        get() = preferences.getString("last_error", null)
        set(value) = preferences.edit().putString("last_error", value).apply()
}
