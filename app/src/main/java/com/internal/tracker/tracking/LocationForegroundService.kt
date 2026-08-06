package com.internal.tracker.tracking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.internal.tracker.MainActivity
import com.internal.tracker.R
import com.internal.tracker.profile.Profile
import com.internal.tracker.queue.LocationSample
import com.internal.tracker.queue.QueueStore
import com.internal.tracker.worker.QueueUploader
import com.internal.tracker.worker.UploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

interface TrackingDependenciesOwner {
    val trackingQueue: QueueStore
    val trackingUploader: QueueUploader
    suspend fun activeTrackingProfile(): Profile?
    fun rememberLocation(sample: LocationSample)
}

class LocationForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val preferences by lazy { TrackingPreferences(this) }
    private val dependencies get() = application as? TrackingDependenciesOwner

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                scope.launch {
                    val owner = dependencies ?: return@launch
                    owner.trackingQueue.enqueue(
                        LocationSample(
                            location.latitude,
                            location.longitude,
                            location.time,
                            location.speed.toDouble(),
                            location.accuracy.toDouble(),
                        ).also(owner::rememberLocation),
                    )
                    preferences.lastLocationTime = location.time
                    val upload = owner.trackingUploader.drain()
                    if (upload.sent > 0) preferences.lastSendTime = System.currentTimeMillis()
                    if (upload.failed > 0) UploadWorker.schedule(this@LocationForegroundService)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }
        scope.launch {
            val profile = dependencies?.activeTrackingProfile()
            if (profile == null || ContextCompat.checkSelfPermission(this@LocationForegroundService, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                stopTracking()
                return@launch
            }
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, profile.intervalSeconds * 1000L)
                .setMinUpdateIntervalMillis(profile.intervalSeconds * 1000L)
                .build()
            locationClient.requestLocationUpdates(request, callback, mainLooper)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        locationClient.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopTracking() {
        preferences.enabled = false
        locationClient.removeLocationUpdates(callback)
        stopSelf()
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.tracking_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle(getString(R.string.tracking_notification_title))
        .setContentText(getString(R.string.tracking_notification_text))
        .setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .addAction(0, getString(R.string.stop_tracking), PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
        .build()

    companion object {
        const val ACTION_STOP = "com.internal.tracker.STOP"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1001
    }
}
