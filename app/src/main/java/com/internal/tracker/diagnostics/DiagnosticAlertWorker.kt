package com.internal.tracker.diagnostics

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters

interface DiagnosticAlertOwner {
    val diagnosticAlertDelivery: DiagnosticAlertDelivery
}

object DiagnosticAlertWork {
    const val KEY_INCIDENT_ID = "incident_id"
    const val KEY_PHASE = "phase"
    val existingWorkPolicy = ExistingWorkPolicy.KEEP

    fun uniqueName(id: String, phase: DiagnosticAlertPhase) = "diagnostic-alert-$id-${phase.name}"
}

class DiagnosticAlertWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val incidentId = inputData.getString(DiagnosticAlertWork.KEY_INCIDENT_ID)
            ?.takeIf(String::isNotBlank) ?: return Result.failure()
        val phase = inputData.getString(DiagnosticAlertWork.KEY_PHASE)
            ?.let { runCatching { DiagnosticAlertPhase.valueOf(it) }.getOrNull() }
            ?: return Result.failure()
        val owner = applicationContext as? DiagnosticAlertOwner ?: return Result.failure()
        owner.diagnosticAlertDelivery.deliver(incidentId, phase)
        return Result.success()
    }
}
