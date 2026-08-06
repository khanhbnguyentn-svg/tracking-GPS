package com.internal.tracker.tracking

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class TrackingController(
    private val context: Context,
    private val preferences: TrackingPreferences,
) {
    fun start() {
        if (preferences.enabled) return
        preferences.enabled = true
        ContextCompat.startForegroundService(context, Intent(context, LocationForegroundService::class.java))
    }

    fun stop() {
        preferences.enabled = false
        context.startService(Intent(context, LocationForegroundService::class.java).setAction(LocationForegroundService.ACTION_STOP))
    }

    fun isTracking(): Boolean = preferences.enabled
}
