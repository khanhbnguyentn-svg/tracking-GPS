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
import com.internal.tracker.diagnostics.DiagnosticAlertDelivery
import com.internal.tracker.diagnostics.DiagnosticRepository
import com.internal.tracker.diagnostics.EventSequenceValidator
import com.internal.tracker.diagnostics.GpsGapDetector
import com.internal.tracker.diagnostics.TrackingIntegrityMonitor
import com.internal.tracker.diagnostics.TrajectoryAnomalyDetector
import com.internal.tracker.diagnostics.WorkManagerDiagnosticAlertScheduler
import com.internal.tracker.export.DailyCsvStore
import com.internal.tracker.history.LocationHistoryRepository
import com.internal.tracker.mail.GmailSmtpSender
import com.internal.tracker.mail.ReportDelivery
import com.internal.tracker.report.BatteryReader
import com.internal.tracker.report.ReportRun
import com.internal.tracker.schedule.WorkManagerReportScheduler
import com.internal.tracker.schedule.AppLaunchReconcilePolicy
import com.internal.tracker.schedule.ReconcileAction
import com.internal.tracker.schedule.ScheduleReceiverPolicy
import com.internal.tracker.schedule.RecoveryCause
import com.internal.tracker.history.RecordType
import com.internal.tracker.tracking.MovementDetector
import com.internal.tracker.tracking.TrackingCoordinator
import com.internal.tracker.tracking.TrackingFix
import com.internal.tracker.tracking.TrackingPreferences
import com.internal.tracker.tracking.TrackingService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Suppress("DEPRECATION")
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = Room.databaseBuilder(appContext, AppDatabase::class.java, "tracker.db")
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
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
    val diagnostics = DiagnosticRepository(database.diagnosticDao())
    val csv = DailyCsvStore(appContext)
    val gmail = GmailSmtpSender()
    private val diagnosticAlertScheduler = WorkManagerDiagnosticAlertScheduler(appContext)
    val diagnosticAlertDelivery = DiagnosticAlertDelivery(
        repository = diagnostics,
        config = pilotConfig::load,
        sender = gmail,
        appVersion = BuildConfig.VERSION_NAME,
    )
    val trackingIntegrityMonitor = TrackingIntegrityMonitor(
        gapDetector = GpsGapDetector(),
        trajectoryDetector = TrajectoryAnomalyDetector(),
        sequenceValidator = EventSequenceValidator(),
        repository = diagnostics,
        alertScheduler = diagnosticAlertScheduler,
        onError = { trackingPreferences.lastError = it },
    )
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
        diagnostics = diagnostics,
        config = pilotConfig::load,
        deviceId = deviceId::get,
        telemetry = trackingPreferences,
        appVersion = BuildConfig.VERSION_NAME,
        sender = gmail,
        refreshBackup = ::refreshBackups,
    )
    val reportRun = ReportRun(
        cleanup = {
            val zone = ZoneId.systemDefault()
            val summaryBefore = LocalDate.now(zone).minusYears(1)
                .atStartOfDay(zone).toInstant().toEpochMilli()
            val samplesBefore = Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli()
            val routeFailure = runCatching {
                history.deleteOlderThan(LocalDate.now(zone).minusYears(1))
            }.exceptionOrNull()
            val diagnosticFailure = runCatching {
                diagnostics.cleanup(summaryBefore, samplesBefore)
            }.exceptionOrNull()
            (routeFailure ?: diagnosticFailure)?.let { throw it }
        },
        deliver = { scheduledFor ->
            delivery.deliverPending(scheduledFor).also { outcome ->
                trackingPreferences.lastError = outcome.publicError
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
            trackingPreferences.lastGpsCallbackAt = 0
            trackingPreferences.recoveryCause = null
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

    fun reconcileTracking(cause: RecoveryCause? = null) {
        if (trackingPreferences.enabled) {
            if (cause != null) trackingPreferences.recoveryCause = cause.name
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, TrackingService::class.java).setAction(TrackingService.ACTION_START),
            )
        }
    }

    fun reconcileBackgroundWork(cause: RecoveryCause) {
        ScheduleReceiverPolicy.actions(trackingPreferences.enabled).forEach { action ->
            when (action) {
                ReconcileAction.TRACKING -> reconcileTracking(cause)
                ReconcileAction.SCHEDULE -> reconcileSchedule()
            }
        }
    }

    fun reconcileAppLaunch() {
        if (ReconcileAction.TRACKING in AppLaunchReconcilePolicy.actions(trackingPreferences.enabled)) {
            reconcileTracking(RecoveryCause.APP_LAUNCH)
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
