package com.internal.tracker.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.internal.tracker.TrackerApplication
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

class VehicleActivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val result = intent?.takeIf(ActivityTransitionResult::hasResult)
            ?.let(ActivityTransitionResult::extractResult)
            ?: return
        val event = result.transitionEvents.lastOrNull {
            it.activityType == DetectedActivity.IN_VEHICLE
        } ?: return
        val app = context.applicationContext as? TrackerApplication ?: return
        if (!app.container.trackingPreferences.enabled) return

        val action = when (event.transitionType) {
            ActivityTransition.ACTIVITY_TRANSITION_ENTER -> TrackingService.ACTION_VEHICLE_ENTER
            ActivityTransition.ACTIVITY_TRANSITION_EXIT -> TrackingService.ACTION_VEHICLE_EXIT
            else -> return
        }
        ContextCompat.startForegroundService(
            context,
            Intent(context, TrackingService::class.java).setAction(action),
        )
    }
}
