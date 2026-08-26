package com.internal.tracker.diagnostics

import com.internal.tracker.history.RecordType
import com.internal.tracker.tracking.MovementMode
import com.internal.tracker.tracking.MovementState
import com.internal.tracker.tracking.PersistedMovementAction
import com.internal.tracker.tracking.TrackingFix
import com.internal.tracker.tracking.TrackingOutcome

class EventSequenceValidator {
    private var restoredCandidateId: Long? = null

    fun onRestored(state: MovementState) {
        restoredCandidateId = state.stopCandidate?.recordId
    }

    fun onOutcome(outcome: TrackingOutcome): List<DiagnosticFinding> = buildList {
        outcome.actions.forEach { action ->
            val reason = when (action) {
                is PersistedMovementAction.Inserted -> when {
                    action.type == RecordType.START && outcome.previousState.mode != MovementMode.IDLE -> "DUPLICATE_START"
                    action.type == RecordType.TEMP_STOP && outcome.previousState.mode != MovementMode.MOVING -> "ORPHAN_STOP"
                    else -> null
                }
                is PersistedMovementAction.Finalized -> when {
                    action.type in setOf(RecordType.STOP, RecordType.TEMP_STOP) &&
                        outcome.previousState.mode != MovementMode.STOP_CANDIDATE -> "ORPHAN_STOP"
                    else -> null
                }
            }
            if (reason != null) add(sequenceFinding(reason, outcome.currentState.lastObservedFix))
        }

        val candidate = outcome.currentState.stopCandidate
        val observed = outcome.currentState.lastObservedFix
        if (restoredCandidateId != null) {
            if (candidate?.recordId == restoredCandidateId) {
                add(unresolvedFinding("RESTORED_CANDIDATE_UNRESOLVED", observed))
            }
            restoredCandidateId = null
        } else if (candidate != null && observed != null &&
            observed.capturedAt - candidate.fix.capturedAt >= TEMP_STOP_MILLIS
        ) {
            add(unresolvedFinding("TEMP_STOP_OVERDUE", observed))
        }
    }

    private fun sequenceFinding(reason: String, fix: TrackingFix?) = finding(
        IncidentType.EVENT_SEQUENCE_ANOMALY,
        reason,
        fix,
    )

    private fun unresolvedFinding(reason: String, fix: TrackingFix?) = finding(
        IncidentType.UNRESOLVED_TEMP_STOP,
        reason,
        fix,
    )

    private fun finding(type: IncidentType, reason: String, fix: TrackingFix?) = DiagnosticFinding(
        type = type,
        reasonCodes = setOf(reason),
        confidenceScore = 8,
        confidenceBand = ConfidenceBand.HIGH,
        openedAt = fix?.capturedAt ?: 0,
        recoveredAt = fix?.capturedAt,
        samples = listOfNotNull(fix?.let { ObservedFix(it, it.capturedAt) }),
    )

    private companion object { const val TEMP_STOP_MILLIS = 120_000L }
}
