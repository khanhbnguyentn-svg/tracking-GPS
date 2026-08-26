package com.internal.tracker.diagnostics

import com.internal.tracker.history.RecordType
import com.internal.tracker.tracking.MovementMode
import com.internal.tracker.tracking.MovementState
import com.internal.tracker.tracking.PersistedMovementAction
import com.internal.tracker.tracking.StopCandidate
import com.internal.tracker.tracking.TrackingFix
import com.internal.tracker.tracking.TrackingOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventSequenceValidatorTest {
    @Test
    fun normalShortAndLongStopsAreNotAnomalies() {
        val validator = EventSequenceValidator()
        assertTrue(validator.onOutcome(outcome(MovementMode.IDLE, MovementMode.MOVING, inserted(1, RecordType.START))).isEmpty())
        assertTrue(validator.onOutcome(outcome(MovementMode.MOVING, MovementMode.STOP_CANDIDATE, inserted(2, RecordType.TEMP_STOP, false))).isEmpty())
        assertTrue(validator.onOutcome(outcome(MovementMode.STOP_CANDIDATE, MovementMode.MOVING, finalized(2, RecordType.TEMP_STOP))).isEmpty())
        assertTrue(validator.onOutcome(outcome(MovementMode.MOVING, MovementMode.STOP_CANDIDATE, inserted(3, RecordType.TEMP_STOP, false))).isEmpty())
        assertTrue(validator.onOutcome(outcome(MovementMode.STOP_CANDIDATE, MovementMode.IDLE, finalized(3, RecordType.STOP))).isEmpty())
    }

    @Test
    fun invalidTransitionsUseStableReasonCodes() {
        val validator = EventSequenceValidator()
        assertEquals("DUPLICATE_START", validator.onOutcome(outcome(MovementMode.MOVING, MovementMode.MOVING, inserted(1, RecordType.START))).single().reasonCodes.single())
        assertEquals("ORPHAN_STOP", validator.onOutcome(outcome(MovementMode.IDLE, MovementMode.IDLE, finalized(2, RecordType.STOP))).single().reasonCodes.single())
    }

    @Test
    fun overdueCandidateIsReportedOnlyWhenStillUnresolved() {
        val candidateFix = fix(10_000)
        val previous = MovementState(
            mode = MovementMode.STOP_CANDIDATE,
            lastObservedFix = candidateFix,
            stopCandidate = StopCandidate(4L, candidateFix),
        )
        val current = previous.copy(lastObservedFix = fix(130_000))

        val finding = EventSequenceValidator().onOutcome(TrackingOutcome(previous, current, emptyList())).single()

        assertEquals(IncidentType.UNRESOLVED_TEMP_STOP, finding.type)
        assertEquals("TEMP_STOP_OVERDUE", finding.reasonCodes.single())
    }

    @Test
    fun restoredCandidateMustResolveOnNextCallback() {
        val candidateFix = fix(10_000)
        val state = MovementState(
            mode = MovementMode.STOP_CANDIDATE,
            lastObservedFix = candidateFix,
            stopCandidate = StopCandidate(8L, candidateFix),
        )
        val validator = EventSequenceValidator()
        validator.onRestored(state)

        val finding = validator.onOutcome(TrackingOutcome(state, state.copy(lastObservedFix = fix(20_000)), emptyList())).single()

        assertEquals("RESTORED_CANDIDATE_UNRESOLVED", finding.reasonCodes.single())
    }

    private fun outcome(before: MovementMode, after: MovementMode, action: PersistedMovementAction) = TrackingOutcome(
        previousState = MovementState(mode = before, lastObservedFix = fix(1_000)),
        currentState = MovementState(mode = after, lastObservedFix = fix(2_000)),
        actions = listOf(action),
    )

    private fun inserted(id: Long, type: RecordType, finalized: Boolean = true) =
        PersistedMovementAction.Inserted(id, type, finalized)

    private fun finalized(id: Long, type: RecordType) = PersistedMovementAction.Finalized(id, type)

    private fun fix(at: Long) = TrackingFix(10.0, 106.0, 5.0, at, "Asia/Bangkok", 0.0)
}
