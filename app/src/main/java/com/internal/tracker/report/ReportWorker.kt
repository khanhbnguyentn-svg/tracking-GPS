package com.internal.tracker.report

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

interface ReportRunOwner { val reportRun: ReportRun }

class ReportWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val owner = applicationContext as? ReportRunOwner ?: return Result.failure()
        val scheduledFor = inputData.getLong(KEY_SCHEDULED_FOR, 0)
        if (scheduledFor <= 0) return Result.failure()
        owner.reportRun.execute(scheduledFor)
        return Result.success()
    }

    companion object { const val KEY_SCHEDULED_FOR = "scheduled_for" }
}
