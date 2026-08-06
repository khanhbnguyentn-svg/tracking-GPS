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
}
