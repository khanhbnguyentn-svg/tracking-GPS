package com.internal.tracker

import android.content.Context
import android.provider.Settings
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
import com.internal.tracker.report.LocationSnapshotProvider
import com.internal.tracker.report.ReportRun
import com.internal.tracker.schedule.WorkManagerReportScheduler
import com.internal.tracker.tracking.TrackingPreferences
import java.time.Instant
import java.time.ZoneId

@Suppress("DEPRECATION")
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = Room.databaseBuilder(appContext, AppDatabase::class.java, "tracker.db")
        .addMigrations(AppDatabase.MIGRATION_1_2)
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
    private val location = LocationSnapshotProvider(appContext)
    private val battery = BatteryReader(appContext)
    private val reportScheduler = WorkManagerReportScheduler(appContext) { trackingPreferences.nextRunTime = it }
    private val delivery = ReportDelivery(
        history = database.locationRecordDao(),
        config = pilotConfig::load,
        appVersion = BuildConfig.VERSION_NAME,
        sender = gmail,
        refreshBackup = ::refreshBackups,
    )
    val reportRun = ReportRun(
        capture = location::capture,
        persist = { captured, batteryPercent ->
            val config = pilotConfig.load()
            val id = history.capture(
                captured,
                batteryPercent,
                (captured.capturedAt - trackingPreferences.startedAt).coerceAtLeast(0),
                config.deviceNumber,
                deviceId.get(),
            )
            trackingPreferences.lastLocationTime = captured.capturedAt
            id
        },
        backup = { id ->
            history.get(id)?.let { refreshBackups(listOf(it)) }
        },
        deliver = {
            delivery.deliverPending().also { outcome ->
                trackingPreferences.lastError = outcome.publicError
                if (outcome.sent > 0) trackingPreferences.lastSendTime = System.currentTimeMillis()
            }
        },
        batteryPercent = battery::percent,
        scheduleNext = ::reconcileSchedule,
    )

    fun reconcileSchedule() {
        val config = pilotConfig.load()
        reportScheduler.reconcile(trackingPreferences.enabled, config.intervalHours, config.deviceNumber.toIntOrNull() ?: 1)
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
