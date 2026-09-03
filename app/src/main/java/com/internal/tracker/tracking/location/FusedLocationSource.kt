package com.internal.tracker.tracking.location

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.TimeZone

class FusedLocationSource(context: Context) : LocationSource {
    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null
    override fun startOrdinary(onLocation: (com.internal.tracker.tracking.model.RawLocationSample) -> Unit) {
        if (callback != null) return
        if (!hasLocationPermission()) return
        callback = object : LocationCallback() { override fun onLocationResult(result: LocationResult) { result.locations.sortedBy { it.elapsedRealtimeNanos }.forEach { onLocation(AndroidLocationMapper.map(it, TimeZone.getDefault().getOffset(it.time) / 60_000)) } } }
        try {
            client.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L).setMinUpdateIntervalMillis(10_000L).build(), callback!!, null)
        } catch (_: SecurityException) {
            callback = null
        }
    }
    override fun stopOrdinary() { callback?.let(client::removeLocationUpdates); callback = null }
    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
