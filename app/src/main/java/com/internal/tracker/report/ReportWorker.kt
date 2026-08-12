package com.internal.tracker.report

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

interface ReportRunOwner { val reportRun: ReportRun }

class ReportWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val owner = applicationContext as? ReportRunOwner ?: return Result.failure()
        owner.reportRun.execute()
        return Result.success()
    }
}
