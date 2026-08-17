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

    var nextRunTime: Long
        get() = preferences.getLong("next_run", 0)
        set(value) = preferences.edit().putLong("next_run", value).apply()

    var lastGpsCallbackAt: Long
        get() = preferences.getLong("last_gps_callback", 0)
        set(value) = preferences.edit().putLong("last_gps_callback", value).apply()

    var recoveryCause: String?
        get() = preferences.getString("recovery_cause", null)
        set(value) = preferences.edit().putString("recovery_cause", value).apply()

    @Synchronized
    fun consumeRecoveryCause(): String? {
        val cause = preferences.getString("recovery_cause", null)
        preferences.edit().remove("recovery_cause").commit()
        return cause
    }

    var lastEmailAttemptTime: Long
        get() = preferences.getLong("last_email_attempt", 0)
        set(value) = preferences.edit().putLong("last_email_attempt", value).apply()

    var consecutiveEmailFailures: Int
        get() = preferences.getInt("consecutive_email_failures", 0)
        set(value) = preferences.edit().putInt("consecutive_email_failures", value).apply()

    var lastEmailFailure: String?
        get() = preferences.getString("last_email_failure", null)
        set(value) = preferences.edit().putString("last_email_failure", value).apply()
}
