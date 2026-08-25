package com.internal.tracker.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.internal.tracker.MainActivity
import com.internal.tracker.R
import com.internal.tracker.TrackerApplication
import com.internal.tracker.diagnostics.GpsGapDetector
import com.internal.tracker.diagnostics.IntegrityDirective
import com.internal.tracker.schedule.RecoveryCause
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processingMutex = Mutex()
    private lateinit var fusedLocation: FusedLocationProviderClient
    private lateinit var activityMonitor: VehicleActivityMonitor
    private lateinit var locationManager: LocationManager
    private val container get() = (application as TrackerApplication).container
    private var healthJob: Job? = null
    @Volatile private var currentPriority: Int? = null
    @Volatile private var started = false
    @Volatile private var inVehicle = false
    @Volatile private var lastCallbackElapsed = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            process(result.locations)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocation = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(LocationManager::class.java)
        activityMonitor = VehicleActivityMonitor(this) { error ->
            container.trackingPreferences.lastError = error
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> stopTracking()
            ACTION_VEHICLE_ENTER -> {
                inVehicle = true
                startTrackingIfEnabled()
            }
            ACTION_VEHICLE_EXIT -> {
                inVehicle = false
                startTrackingIfEnabled()
            }
            else -> startTrackingIfEnabled(consumeRecoveryCause(intent))
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        healthJob?.cancel()
        fusedLocation.removeLocationUpdates(locationCallback)
        activityMonitor.unregister()
        scope.cancel()
        super.onDestroy()
    }

    private fun consumeRecoveryCause(intent: Intent?): RecoveryCause? {
        val stored = container.trackingPreferences.consumeRecoveryCause()
        return stored?.let { runCatching { RecoveryCause.valueOf(it) }.getOrNull() }
            ?: RecoveryCause.PROCESS_RECREATED.takeIf { intent == null }
    }

    private fun startTrackingIfEnabled(recoveryCause: RecoveryCause? = null) {
        if (!container.trackingPreferences.enabled) {
            stopSelf()
            return
        }
        if (started) {
            activityMonitor.register()
            if (currentPriority == null) registerLocationUpdates(Priority.PRIORITY_HIGH_ACCURACY, force = true)
            return
        }
        started = true
        scope.launch {
            processingMutex.withLock {
                if (!container.trackingPreferences.enabled) return@withLock
                runCatching {
                    val state = container.trackingCoordinator.restore(
                        container.trackingPreferences.startedAt,
                    )
                    val nowElapsed = SystemClock.elapsedRealtime()
                    lastCallbackElapsed = nowElapsed
                    container.trackingIntegrityMonitor.onRestoredMovement(state)
                    container.trackingIntegrityMonitor.onStarted(
                        trackingEnabled = true,
                        nowWall = System.currentTimeMillis(),
                        nowElapsed = nowElapsed,
                        lastCallbackWall = container.trackingPreferences.lastGpsCallbackAt,
                        condition = TrackingHealthPolicy.startupCondition(recoveryCause),
                    )
                    registerLocationUpdates(LocationPriorityPolicy.forMode(state.mode))
                    activityMonitor.register()
                    startHealthChecks()
                }.onFailure {
                    started = false
                    currentPriority = null
                    container.trackingPreferences.lastError = ERROR_TRACKING_START_FAILED
                }
            }
        }
    }

    private fun stopTracking() {
        scope.launch {
            processingMutex.withLock {
                runCatching { container.trackingCoordinator.stop() }
                    .onFailure { container.trackingPreferences.lastError = ERROR_PERSIST_FAILED }
                fusedLocation.removeLocationUpdates(locationCallback)
                activityMonitor.unregister()
                healthJob?.cancel()
                healthJob = null
                currentPriority = null
                started = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun process(locations: List<Location>) {
        val receivedAt = System.currentTimeMillis()
        val elapsedAt = SystemClock.elapsedRealtime()
        lastCallbackElapsed = elapsedAt
        container.trackingPreferences.lastGpsCallbackAt = receivedAt
        val fixes = locations.sortedBy(Location::getTime).map { it.toTrackingFix() }
        scope.launch {
            processingMutex.withLock {
                if (!container.trackingPreferences.enabled) return@withLock
                fixes.forEach { fix ->
                    runCatching {
                        container.trackingIntegrityMonitor.onLocationReceived(fix, receivedAt, elapsedAt)
                        val outcome = container.trackingCoordinator.onFix(fix, inVehicle)
                        container.trackingIntegrityMonitor.onMovementProcessed(outcome)
                        registerLocationUpdates(LocationPriorityPolicy.forMode(outcome.currentState.mode))
                    }.onFailure { container.trackingPreferences.lastError = ERROR_PERSIST_FAILED }
                }
            }
        }
    }

    private fun Location.toTrackingFix() = TrackingFix(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy.takeIf { hasAccuracy() }?.toDouble(),
        capturedAt = time,
        timezone = ZoneId.systemDefault().id,
        speedMetersPerSecond = speed.takeIf { hasSpeed() }?.toDouble(),
    )

    @SuppressLint("MissingPermission")
    private fun registerLocationUpdates(priority: Int, force: Boolean = false) {
        if (!force && currentPriority == priority) return
        if (!hasLocationPermission()) {
            container.trackingPreferences.lastError = ERROR_LOCATION_PERMISSION_MISSING
            fusedLocation.removeLocationUpdates(locationCallback)
            currentPriority = null
            return
        }

        val request = LocationRequest.Builder(priority, LOCATION_INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(LOCATION_INTERVAL_MILLIS)
            .build()
        fusedLocation.removeLocationUpdates(locationCallback)
        runCatching {
            currentPriority = priority
            fusedLocation.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
                .addOnFailureListener {
                    currentPriority = null
                    container.trackingPreferences.lastError = ERROR_LOCATION_UPDATES_FAILED
                }
        }.onFailure {
            currentPriority = null
            container.trackingPreferences.lastError = ERROR_LOCATION_UPDATES_FAILED
        }
    }

    private fun startHealthChecks() {
        healthJob?.cancel()
        healthJob = scope.launch {
            while (isActive) {
                delay(LOCATION_INTERVAL_MILLIS)
                processingMutex.withLock {
                    if (!started || !container.trackingPreferences.enabled) return@withLock
                    val nowElapsed = SystemClock.elapsedRealtime()
                    val directive = container.trackingIntegrityMonitor.onHealthTick(
                        nowWall = System.currentTimeMillis(),
                        nowElapsed = nowElapsed,
                        condition = currentDeviceCondition(nowElapsed),
                    )
                    if (directive == IntegrityDirective.RE_REGISTER_LOCATION) {
                        registerLocationUpdates(Priority.PRIORITY_HIGH_ACCURACY, force = true)
                    }
                }
            }
        }
    }

    private fun currentDeviceCondition(nowElapsed: Long) = TrackingHealthPolicy.condition(
        hasLocationPermission = hasLocationPermission(),
        isLocationEnabled = LocationManagerCompat.isLocationEnabled(locationManager),
        callbackGapOpen = nowElapsed - lastCallbackElapsed >= GpsGapDetector.GAP_THRESHOLD_MILLIS,
    )

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Theo dõi GPS",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun notification() = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle(getString(R.string.app_name))
        .setContentText("Đang theo dõi vị trí")
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    companion object {
        const val ACTION_START = "com.internal.tracker.tracking.START"
        const val ACTION_STOP = "com.internal.tracker.tracking.STOP"
        const val ACTION_VEHICLE_ENTER = "com.internal.tracker.tracking.VEHICLE_ENTER"
        const val ACTION_VEHICLE_EXIT = "com.internal.tracker.tracking.VEHICLE_EXIT"
        const val LOCATION_INTERVAL_MILLIS = 10_000L
        const val ERROR_LOCATION_PERMISSION_MISSING = "LOCATION_PERMISSION_MISSING"
        const val ERROR_LOCATION_UPDATES_FAILED = "LOCATION_UPDATES_FAILED"
        const val ERROR_TRACKING_START_FAILED = "TRACKING_START_FAILED"
        const val ERROR_PERSIST_FAILED = "TRACKING_PERSIST_FAILED"
        private const val NOTIFICATION_CHANNEL_ID = "continuous_tracking"
        private const val NOTIFICATION_ID = 701
    }
}
