package com.internal.tracker.diagnostics

import com.internal.tracker.tracking.TrackingFix
import com.internal.tracker.tracking.TrackingOutcome

enum class DiagnosticAlertPhase { OPENED, RECOVERED }

fun interface DiagnosticAlertScheduler {
    fun enqueue(incidentId: String, phase: DiagnosticAlertPhase)
}

enum class IntegrityDirective { NONE, RE_REGISTER_LOCATION }

class TrackingIntegrityMonitor(
    private val gapDetector: GpsGapDetector,
    private val trajectoryDetector: TrajectoryAnomalyDetector,
    private val sequenceValidator: EventSequenceValidator,
    private val repository: DiagnosticRepository,
    private val alertScheduler: DiagnosticAlertScheduler,
    private val onError: (String) -> Unit,
) {
    private var gapState = GapState(0, false, null)
    private var openGapId: String? = null
    private var lastObserved: ObservedFix? = null

    suspend fun onStarted(
        trackingEnabled: Boolean,
        nowWall: Long,
        nowElapsed: Long,
        lastCallbackWall: Long,
        condition: DeviceCondition,
    ) {
        if (!trackingEnabled) {
            gapState = gapDetector.onStarted(nowElapsed, false)
            return
        }
        val existing = safe { repository.openIncident(IncidentType.GPS_GAP) }
        openGapId = existing?.incidentId
        gapState = gapDetector.onStarted(nowElapsed, existing != null)
        if (existing == null && lastCallbackWall > 0 && nowWall - lastCallbackWall >= GpsGapDetector.GAP_THRESHOLD_MILLIS) {
            val opened = safe { repository.openGap(lastCallbackWall, condition, null) } ?: return
            openGapId = opened.incidentId
            schedule(opened.incidentId, DiagnosticAlertPhase.OPENED)
            val recovered = safe { repository.recoverGap(opened.incidentId, nowWall, null, false) } ?: return
            openGapId = null
            gapState = gapDetector.onStarted(nowElapsed, false)
            schedule(recovered.incidentId, DiagnosticAlertPhase.RECOVERED)
        }
    }

    suspend fun onHealthTick(nowWall: Long, nowElapsed: Long, condition: DeviceCondition): IntegrityDirective {
        val transition = gapDetector.onTick(gapState, nowElapsed)
        gapState = transition.state
        return when {
            GapAction.OPEN in transition.actions -> {
                val last = lastObserved?.fix?.let { DiagnosticLocation(it.capturedAt, it.latitude, it.longitude) }
                val opened = safe { repository.openGap(nowWall - GpsGapDetector.GAP_THRESHOLD_MILLIS, condition, last) }
                if (opened != null) {
                    openGapId = opened.incidentId
                    val samples = trajectoryDetector.recentFixes().mapIndexed { index, observed ->
                        observed.toSample(opened.incidentId, index, EvidenceRole.BEFORE)
                    }
                    safe { repository.save(opened, samples) }
                    schedule(opened.incidentId, DiagnosticAlertPhase.OPENED)
                }
                IntegrityDirective.RE_REGISTER_LOCATION
            }
            GapAction.RETRY_REGISTRATION in transition.actions -> IntegrityDirective.RE_REGISTER_LOCATION
            else -> IntegrityDirective.NONE
        }
    }

    suspend fun onLocationReceived(fix: TrackingFix, receivedAt: Long, elapsedAt: Long) {
        val observed = ObservedFix(fix, receivedAt)
        lastObserved = observed
        val transition = gapDetector.onCallback(gapState, elapsedAt)
        gapState = transition.state
        if (GapAction.RECOVER in transition.actions) {
            val id = openGapId
            if (id != null) {
                val location = DiagnosticLocation(fix.capturedAt, fix.latitude, fix.longitude)
                val recovered = safe { repository.recoverGap(id, receivedAt, location, true) }
                if (recovered != null) {
                    safe { repository.save(recovered, listOf(observed.toSample(id, Int.MAX_VALUE, EvidenceRole.AFTER))) }
                    schedule(id, DiagnosticAlertPhase.RECOVERED)
                }
            }
            openGapId = null
        }
        trajectoryDetector.onFix(observed).forEach { finding -> safe { repository.saveFinding(finding) } }
    }

    suspend fun onMovementProcessed(outcome: TrackingOutcome) {
        sequenceValidator.onOutcome(outcome).forEach { finding -> safe { repository.saveFinding(finding) } }
    }

    fun onRestoredMovement(outcomeState: com.internal.tracker.tracking.MovementState) {
        sequenceValidator.onRestored(outcomeState)
    }

    private fun schedule(id: String, phase: DiagnosticAlertPhase) {
        runCatching { alertScheduler.enqueue(id, phase) }.onFailure { onError("DIAGNOSTIC_ALERT_SCHEDULE") }
    }

    private suspend fun <T> safe(block: suspend () -> T): T? = runCatching { block() }
        .onFailure { onError("DIAGNOSTIC_PERSIST") }
        .getOrNull()

    private fun ObservedFix.toSample(id: String, sequence: Int, role: EvidenceRole) = DiagnosticSample(
        incidentId = id,
        sequence = sequence,
        role = role,
        capturedAt = fix.capturedAt,
        receivedAt = receivedAt,
        latitude = fix.latitude,
        longitude = fix.longitude,
        accuracy = fix.accuracy,
        speedMetersPerSecond = fix.speedMetersPerSecond,
        derivedDistanceMeters = null,
        derivedSpeedMetersPerSecond = null,
        signalFlags = "",
    )
}
