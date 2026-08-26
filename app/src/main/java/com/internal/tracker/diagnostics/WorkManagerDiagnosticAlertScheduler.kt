package com.internal.tracker.diagnostics

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class WorkManagerDiagnosticAlertScheduler(context: Context) : DiagnosticAlertScheduler {
    private val workManager = WorkManager.getInstance(context)

    override fun enqueue(incidentId: String, phase: DiagnosticAlertPhase) {
        val request = OneTimeWorkRequestBuilder<DiagnosticAlertWorker>()
            .setInputData(workDataOf(
                DiagnosticAlertWork.KEY_INCIDENT_ID to incidentId,
                DiagnosticAlertWork.KEY_PHASE to phase.name,
            ))
            .build()
        workManager.enqueueUniqueWork(
            DiagnosticAlertWork.uniqueName(incidentId, phase),
            DiagnosticAlertWork.existingWorkPolicy,
            request,
        )
    }
}
