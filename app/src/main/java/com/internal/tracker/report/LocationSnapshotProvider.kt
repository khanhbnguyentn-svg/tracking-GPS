package com.internal.tracker.report

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.internal.tracker.history.CapturedLocation
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

class LocationSnapshotProvider(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun capture(): CapturedLocation = withTimeout(30_000) {
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationTokenSource()
            continuation.invokeOnCancellation { cancellation.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                .addOnSuccessListener { location ->
                    if (location == null) continuation.resumeWithException(IllegalStateException("LOCATION_UNAVAILABLE"))
                    else continuation.resume(
                        CapturedLocation(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracy = location.accuracy.toDouble(),
                            capturedAt = location.time,
                            timezone = ZoneId.systemDefault().id,
                        ),
                    )
                }
                .addOnFailureListener(continuation::resumeWithException)
        }
    }
}
