package com.internal.tracker.tracking

import com.internal.tracker.history.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementDetectorTest {
    private val detector = MovementDetector()

    @Test
    fun gpsFallbackRequiresTwoMovingFixesBeforeStart() {
        val first = detector.onFix(MovementState(), fix(at = 0, speedKmh = 5.0), inVehicle = false)

        assertEquals(MovementMode.IDLE, first.state.mode)
        assertEquals(1, first.state.consecutiveMovingFixes)
        assertTrue(first.actions.isEmpty())

        val second = detector.onFix(first.state, fix(at = 10_000, speedKmh = 5.1), inVehicle = false)

        assertEquals(MovementMode.MOVING, second.state.mode)
        assertEquals(
            listOf(MovementAction.Insert(RecordType.START, fix(at = 10_000, speedKmh = 5.1), true)),
            second.actions,
        )
    }

    @Test
    fun inVehicleStartsImmediately() {
        val transition = detector.onFix(
            MovementState(),
            fix(at = 1_000, speedKmh = 0.0),
            inVehicle = true,
        )

        assertEquals(MovementMode.MOVING, transition.state.mode)
        assertEquals(RecordType.START, (transition.actions.single() as MovementAction.Insert).type)
    }

    @Test
    fun movingStoresPeriodicFixOnlyAfterTwoMinutes() {
        val start = detector.onFix(
            MovementState(),
            fix(at = 0, speedKmh = 10.0),
            inVehicle = true,
        )

        val early = detector.onFix(start.state, fix(at = 119_999, speedKmh = 10.0), inVehicle = true)
        assertTrue(early.actions.isEmpty())

        val due = detector.onFix(early.state, fix(at = 120_000, speedKmh = 10.0), inVehicle = true)
        assertEquals(RecordType.PERIODIC, (due.actions.single() as MovementAction.Insert).type)
        assertEquals(120_000L, due.state.lastStoredAt)
    }

    @Test
    fun stoppingCreatesUnfinishedCandidateImmediately() {
        val moving = movingState(at = 0)

        val stopped = detector.onFix(moving, fix(at = 10_000, speedKmh = 2.9), inVehicle = false)

        val insert = stopped.actions.single() as MovementAction.Insert
        assertEquals(MovementMode.STOP_CANDIDATE, stopped.state.mode)
        assertEquals(RecordType.TEMP_STOP, insert.type)
        assertFalse(insert.finalized)
    }

    @Test
    fun resumingBeforeTwoMinutesFinalizesCandidateAsTempStop() {
        val candidate = beginCandidate().let { transition ->
            detector.attachCandidateId(transition.state, id = 41)
        }

        val resumed = detector.onFix(
            candidate,
            fix(at = 129_999, speedKmh = 5.0),
            inVehicle = false,
        )

        val action = resumed.actions.single() as MovementAction.FinalizeCandidate
        assertEquals(MovementMode.MOVING, resumed.state.mode)
        assertEquals(RecordType.TEMP_STOP, action.type)
        assertEquals(41L, action.recordId)
    }

    @Test
    fun remainingStoppedForTwoMinutesFinalizesSameCandidateAsStop() {
        val candidate = beginCandidate(at = 10_000).let { transition ->
            detector.attachCandidateId(transition.state, id = 42)
        }

        val stopped = detector.onFix(
            candidate,
            fix(at = 130_000, speedKmh = 0.0, latitude = 10.00001),
            inVehicle = false,
        )

        val actions = stopped.actions.filterIsInstance<MovementAction.FinalizeCandidate>()
        assertEquals(MovementMode.IDLE, stopped.state.mode)
        assertEquals(1, actions.size)
        assertEquals(RecordType.STOP, actions.single().type)
        assertEquals(42L, actions.single().recordId)
    }

    @Test
    fun staleAndInaccurateFixIsStillStored() {
        val oldTimestamp = 500_000L
        val state = MovementState(
            mode = MovementMode.MOVING,
            lastStoredAt = oldTimestamp - MovementDetector.PERIODIC_MILLIS,
        )
        val inaccurateFix = fix(
            at = oldTimestamp,
            speedKmh = 10.0,
            accuracy = 9_999.0,
        )

        val storedAction = detector.onFix(state, inaccurateFix, inVehicle = false)
            .actions.single() as MovementAction.Insert

        assertEquals(9_999.0, storedAction.fix.accuracy)
        assertEquals(oldTimestamp, storedAction.fix.capturedAt)
    }

    @Test
    fun stoppingTrackingFinalizesActiveCandidateAsTempStop() {
        val candidate = beginCandidate().let { transition ->
            detector.attachCandidateId(transition.state, id = 43)
        }

        val stopped = detector.onTrackingStopped(candidate)

        assertEquals(
            MovementAction.FinalizeCandidate(43, RecordType.TEMP_STOP),
            stopped.actions.single(),
        )
        assertEquals(MovementMode.IDLE, stopped.state.mode)
    }

    private fun movingState(at: Long): MovementState = detector.onFix(
        MovementState(),
        fix(at = at, speedKmh = 10.0),
        inVehicle = true,
    ).state

    private fun beginCandidate(at: Long = 10_000): MovementTransition = detector.onFix(
        movingState(at = 0),
        fix(at = at, speedKmh = 0.0),
        inVehicle = false,
    )

    private fun fix(
        at: Long,
        speedKmh: Double,
        latitude: Double = 10.0,
        longitude: Double = 106.0,
        accuracy: Double? = 5.0,
    ) = TrackingFix(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        capturedAt = at,
        timezone = "Asia/Bangkok",
        speedMetersPerSecond = speedKmh / 3.6,
    )
}
