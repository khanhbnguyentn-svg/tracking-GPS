package com.internal.tracker.diagnostics

import java.util.UUID

class DiagnosticRepository(
    private val store: DiagnosticStore,
    private val incidentIds: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun incident(id: String) = store.incident(id)

    suspend fun openGap(
        openedAt: Long,
        condition: DeviceCondition,
        lastFix: DiagnosticLocation?,
    ): DiagnosticIncident {
        store.openIncident(IncidentType.GPS_GAP)?.let { return it }
        return DiagnosticIncident(
            incidentId = incidentIds(),
            type = IncidentType.GPS_GAP,
            reasonCodes = "NO_CALLBACK_30_SECONDS",
            openedAt = openedAt,
            deviceCondition = condition,
            lastCapturedAt = lastFix?.capturedAt,
            lastLatitude = lastFix?.latitude,
            lastLongitude = lastFix?.longitude,
            openedAlertState = DiagnosticDeliveryState.PENDING,
        ).also { store.upsertIncident(it) }
    }

    suspend fun recoverGap(
        incidentId: String,
        recoveredAt: Long,
        firstFix: DiagnosticLocation?,
        evidenceComplete: Boolean,
    ): DiagnosticIncident {
        val current = requireNotNull(store.incident(incidentId))
        return current.copy(
            recoveredAt = recoveredAt,
            state = IncidentState.RECOVERED,
            evidenceComplete = evidenceComplete,
            firstCapturedAt = firstFix?.capturedAt,
            firstLatitude = firstFix?.latitude,
            firstLongitude = firstFix?.longitude,
            recoveredAlertState = DiagnosticDeliveryState.PENDING,
        ).also { store.upsertIncident(it) }
    }

    suspend fun save(incident: DiagnosticIncident, samples: List<DiagnosticSample> = emptyList()) {
        store.upsertIncident(incident)
        if (samples.isNotEmpty()) store.insertSamples(samples)
    }

    suspend fun pendingBundle(limit: Int): DiagnosticBundle {
        val incidents = store.pendingForReport(limit)
        val samples = if (incidents.isEmpty()) emptyList() else store.samplesFor(incidents.map { it.incidentId })
        return DiagnosticBundle(incidents, samples)
    }

    suspend fun markOpenedResult(id: String, acceptedAt: Long?, error: String?) = update(id) {
        it.copy(
            openedAlertState = if (error == null) DiagnosticDeliveryState.ACCEPTED else DiagnosticDeliveryState.FAILED,
            openedAlertAttempts = it.openedAlertAttempts + 1,
            openedAlertSentAt = acceptedAt,
            openedAlertError = error,
        )
    }

    suspend fun markRecoveredResult(id: String, acceptedAt: Long?, error: String?) = update(id) {
        it.copy(
            recoveredAlertState = if (error == null) DiagnosticDeliveryState.ACCEPTED else DiagnosticDeliveryState.FAILED,
            recoveredAlertAttempts = it.recoveredAlertAttempts + 1,
            recoveredAlertSentAt = acceptedAt,
            recoveredAlertError = error,
        )
    }

    suspend fun markReported(ids: List<String>, reportedAt: Long) {
        ids.forEach { id -> update(id) { it.copy(reportedAt = reportedAt) } }
    }

    suspend fun cleanup(summaryBefore: Long, reportedSamplesBefore: Long) {
        store.deleteReportedSamplesBefore(reportedSamplesBefore)
        store.deleteIncidentsBefore(summaryBefore)
    }

    private suspend fun update(id: String, transform: (DiagnosticIncident) -> DiagnosticIncident): DiagnosticIncident {
        val updated = transform(requireNotNull(store.incident(id)))
        store.upsertIncident(updated)
        return updated
    }
}
