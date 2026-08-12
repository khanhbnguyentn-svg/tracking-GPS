package com.internal.tracker.schedule

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.internal.tracker.report.ReportWorker
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class WorkManagerReportScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context)
    private val scheduler = ReportScheduler(
        now = ZonedDateTime::now,
        enqueue = { time ->
            val delay = (time.toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<ReportWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        },
        cancel = { workManager.cancelUniqueWork(UNIQUE_NAME) },
    )

    fun reconcile(enabled: Boolean, intervalHours: Int, deviceNumber: Int) =
        scheduler.reconcile(enabled, intervalHours, deviceNumber)

    companion object { const val UNIQUE_NAME = "scheduled-location-report" }
}
