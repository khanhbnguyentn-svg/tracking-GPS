package com.internal.tracker.tracking.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.internal.tracker.R
import com.internal.tracker.TrackerApplication
import com.internal.tracker.tracking.database.TrackingDatabaseFactory
import com.internal.tracker.tracking.model.BootReason
import com.internal.tracker.tracking.raw.RoomRawLocationRepository
import com.internal.tracker.tracking.location.FusedLocationSource
import com.internal.tracker.tracking.health.TrackingHealthMonitor
import com.internal.tracker.tracking.movement.MovementClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AlwaysOnTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var locationSource: FusedLocationSource? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle(getString(R.string.tracking_notification_title)).setOngoing(true).build())
        if (locationSource == null) scope.launch {
            val platform = (application as TrackerApplication).platform
            val database = TrackingDatabaseFactory(applicationContext, platform.databasePassphraseStore, platform.sqlCipherFactoryProvider).open()
            val raw = RoomRawLocationRepository(database)
            val startedAtElapsedNanos = android.os.SystemClock.elapsedRealtimeNanos()
            val boot = raw.startBootSession(BootReason.PROCESS_START, System.currentTimeMillis(), startedAtElapsedNanos)
            val health = TrackingHealthMonitor().also { it.onStarted(startedAtElapsedNanos) }
            val movement = MovementClassifier()
            locationSource = FusedLocationSource(applicationContext).also { source -> source.startOrdinary { sample -> scope.launch {
                health.onLocationCallback(android.os.SystemClock.elapsedRealtimeNanos())
                raw.persistOrdinary(sample, boot)?.let(movement::onPersistedSample)
            } } }
        }
        return START_STICKY
    }
    override fun onDestroy() { locationSource?.stopOrdinary(); scope.coroutineContext.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun createChannel() { NotificationManagerCompat.from(this).createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.tracking_notification_channel), NotificationManager.IMPORTANCE_LOW)) }
    private companion object { const val CHANNEL_ID = "tracking"; const val NOTIFICATION_ID = 1001 }
}
