package com.internal.tracker

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.internal.tracker.config.AdminPinStore
import com.internal.tracker.config.DeviceIdProvider
import com.internal.tracker.config.EncryptedPilotConfigStore
import com.internal.tracker.config.EncryptedPinPreferences
import com.internal.tracker.data.AppDatabase
import com.internal.tracker.export.DailyCsvStore
import com.internal.tracker.history.LocationHistoryRepository
import com.internal.tracker.mail.GmailSmtpSender
import com.internal.tracker.mail.ReportDelivery
import com.internal.tracker.report.BatteryReader
import com.internal.tracker.report.ReportRun
import com.internal.tracker.schedule.WorkManagerReportScheduler
import com.internal.tracker.schedule.ReconcileAction
import com.internal.tracker.schedule.ScheduleReceiverPolicy
import com.internal.tracker.history.RecordType
import com.internal.tracker.tracking.MovementDetector
import com.internal.tracker.tracking.TrackingCoordinator
import com.internal.tracker.tracking.TrackingFix
import com.internal.tracker.tracking.TrackingPreferences
import com.internal.tracker.tracking.TrackingService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Suppress("DEPRECATION")
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = Room.databaseBuilder(appContext, AppDatabase::class.java, "tracker.db")
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
        .build()
    val trackingPreferences = TrackingPreferences(appContext)
    val pilotConfig = EncryptedPilotConfigStore(appContext)
    val adminPin = AdminPinStore(EncryptedPinPreferences(appContext))

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        appContext,
        "device_identity",
        MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    val deviceId = DeviceIdProvider(
        { Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID) },
        { encryptedPrefs.getString("fallback", null) },
        { encryptedPrefs.edit().putString("fallback", it).apply() },
    )


    val history = LocationHistoryRepository(database.locationRecordDao())
    val csv = DailyCsvStore(appContext)
    val gmail = GmailSmtpSender()
    private val battery = BatteryReader(appContext)
    private val reportScheduler = WorkManagerReportScheduler(appContext) { trackingPreferences.nextRunTime = it }
    val trackingCoordinator = TrackingCoordinator(
        history = history,
        detector = MovementDetector(),
        persist = ::persistTrackingEvent,
        onPersisted = { trackingPreferences.lastLocationTime = it.capturedAt },
    )
    private val delivery = ReportDelivery(
        history = database.locationRecordDao(),
        config = pilotConfig::load,
        appVersion = BuildConfig.VERSION_NAME,
        sender = gmail,
        refreshBackup = ::refreshBackups,
    )
    val reportRun = ReportRun(
        cleanup = {
            history.deleteOlderThan(LocalDate.now(ZoneId.systemDefault()).minusYears(1))
        },
        deliver = {
            delivery.deliverPending().also { outcome ->
                trackingPreferences.lastError = outcome.publicError
                if (outcome.sent > 0) trackingPreferences.lastSendTime = System.currentTimeMillis()
            }
        },
        scheduleNext = ::reconcileSchedule,
    )

    fun reconcileSchedule() {
        val config = pilotConfig.load()
        reportScheduler.reconcile(trackingPreferences.enabled, config.intervalHours, config.deviceNumber.toIntOrNull() ?: 1)
    }

    fun startTracking() {
        if (!trackingPreferences.enabled) {
            trackingPreferences.startedAt = System.currentTimeMillis()
            trackingPreferences.enabled = true
        }
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, TrackingService::class.java).setAction(TrackingService.ACTION_START),
        )
        reconcileSchedule()
    }

    suspend fun stopTracking() {
        trackingPreferences.enabled = false
        trackingCoordinator.stop()
        appContext.stopService(Intent(appContext, TrackingService::class.java))
        reconcileSchedule()
    }

    fun reconcileTracking() {
        if (trackingPreferences.enabled) {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, TrackingService::class.java).setAction(TrackingService.ACTION_START),
            )
        }
    }

    fun reconcileBackgroundWork() {
        ScheduleReceiverPolicy.actions(trackingPreferences.enabled).forEach { action ->
            when (action) {
                ReconcileAction.TRACKING -> reconcileTracking()
                ReconcileAction.SCHEDULE -> reconcileSchedule()
            }
        }
    }

    suspend fun persistTrackingEvent(
        fix: TrackingFix,
        type: RecordType,
        finalized: Boolean,
    ): Long {
        val config = pilotConfig.load()
        val id = history.capture(
            location = com.internal.tracker.history.CapturedLocation(
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracy = fix.accuracy,
                capturedAt = fix.capturedAt,
                timezone = fix.timezone,
            ),
            batteryPercent = battery.percent(),
            trackedMillis = (fix.capturedAt - trackingPreferences.startedAt).coerceAtLeast(0),
            deviceNumber = config.deviceNumber,
            deviceId = deviceId.get(),
            recordType = type,
            isFinalized = finalized,
        )
        history.get(id)?.let { refreshBackups(listOf(it)) }
        return id
    }

    private suspend fun refreshBackups(records: List<com.internal.tracker.history.LocationRecord>) {
        records.map { record ->
            val zone = runCatching { ZoneId.of(record.timezone) }.getOrDefault(ZoneId.systemDefault())
            Triple(record.deviceNumber, Instant.ofEpochMilli(record.capturedAt).atZone(zone).toLocalDate(), zone)
        }.distinct().forEach { (number, date, zone) ->
            val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val until = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            csv.writeDay(number, date, history.between(from, until))
        }
    }

}
