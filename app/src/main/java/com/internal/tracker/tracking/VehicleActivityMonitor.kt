package com.internal.tracker.tracking

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity

class VehicleActivityMonitor(
    private val context: Context,
    private val onError: (String) -> Unit,
) {
    private val client = ActivityRecognition.getClient(context)
    private val pendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, VehicleActivityReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun register() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onError(ERROR_ACTIVITY_RECOGNITION_UNAVAILABLE)
            return
        }

        val transitions = listOf(
            transition(ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            transition(ActivityTransition.ACTIVITY_TRANSITION_EXIT),
        )
        runCatching {
            client.requestActivityTransitionUpdates(
                ActivityTransitionRequest(transitions),
                pendingIntent,
            ).addOnFailureListener { onError(ERROR_ACTIVITY_RECOGNITION_UNAVAILABLE) }
        }.onFailure { onError(ERROR_ACTIVITY_RECOGNITION_UNAVAILABLE) }
    }

    fun unregister() {
        runCatching { client.removeActivityTransitionUpdates(pendingIntent) }
    }

    private fun transition(type: Int) = ActivityTransition.Builder()
        .setActivityType(DetectedActivity.IN_VEHICLE)
        .setActivityTransition(type)
        .build()

    companion object {
        const val ERROR_ACTIVITY_RECOGNITION_UNAVAILABLE = "ACTIVITY_RECOGNITION_UNAVAILABLE"
        private const val REQUEST_CODE = 701
    }
}
