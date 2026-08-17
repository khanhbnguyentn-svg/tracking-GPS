package com.internal.tracker.diagnostics

import java.util.UUID

interface DiagnosticReportStore {
    suspend fun pendingBundle(limit: Int): DiagnosticBundle
    suspend fun markReported(ids: List<String>, reportedAt: Long)
}

class DiagnosticRepository(
    private val store: DiagnosticStore,
    private val incidentIds: () -> String = { UUID.randomUUID().toString() },
) : DiagnosticReportStore {
    suspend fun incident(id: String) = store.incident(id)
    suspend fun openIncident(type: IncidentType) = store.openIncident(type)

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

    suspend fun saveFinding(finding: DiagnosticFinding): DiagnosticIncident {
        val id = incidentIds()
        val incident = DiagnosticIncident(
            incidentId = id,
            type = finding.type,
            reasonCodes = finding.reasonCodes.sorted().joinToString(","),
            openedAt = finding.openedAt,
            recoveredAt = finding.recoveredAt,
            state = IncidentState.RECOVERED,
            confidenceScore = finding.confidenceScore,
            confidenceBand = finding.confidenceBand,
            deviceCondition = DeviceCondition.NORMAL,
            evidenceComplete = true,
        )
        val triggerIndex = (finding.samples.size - 4).coerceAtLeast(0)
        val samples = finding.samples.mapIndexed { index, observed ->
            DiagnosticSample(
                incidentId = id,
                sequence = index,
                role = when {
                    index < triggerIndex -> EvidenceRole.BEFORE
                    index == triggerIndex -> EvidenceRole.TRIGGER
                    else -> EvidenceRole.AFTER
                },
                capturedAt = observed.fix.capturedAt,
                receivedAt = observed.receivedAt,
                latitude = observed.fix.latitude,
                longitude = observed.fix.longitude,
                accuracy = observed.fix.accuracy,
                speedMetersPerSecond = observed.fix.speedMetersPerSecond,
                derivedDistanceMeters = null,
                derivedSpeedMetersPerSecond = null,
                signalFlags = if (index == triggerIndex) incident.reasonCodes else "",
            )
        }
        save(incident, samples)
        return incident
    }

    override suspend fun pendingBundle(limit: Int): DiagnosticBundle {
        val incidents = store.pendingForReport(limit)
        val samples = if (incidents.isEmpty()) emptyList() else store.samplesFor(incidents.map { it.incidentId })
        return DiagnosticBundle(incidents, samples)
    }

    suspend fun bundle(id: String): DiagnosticBundle {
        val incident = store.incident(id) ?: return DiagnosticBundle(emptyList(), emptyList())
        return DiagnosticBundle(listOf(incident), store.samplesFor(listOf(id)))
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

    override suspend fun markReported(ids: List<String>, reportedAt: Long) {
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
