package com.internal.tracker.tracking

import com.internal.tracker.history.RecordType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class TrackingFix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val capturedAt: Long,
    val timezone: String,
    val speedMetersPerSecond: Double?,
)

enum class MovementMode { IDLE, MOVING, STOP_CANDIDATE }

data class StopCandidate(
    val recordId: Long?,
    val fix: TrackingFix,
)

data class MovementState(
    val mode: MovementMode = MovementMode.IDLE,
    val consecutiveMovingFixes: Int = 0,
    val lastObservedFix: TrackingFix? = null,
    val lastStoredAt: Long? = null,
    val stopCandidate: StopCandidate? = null,
)

sealed interface MovementAction {
    data class Insert(
        val type: RecordType,
        val fix: TrackingFix,
        val finalized: Boolean,
    ) : MovementAction

    data class FinalizeCandidate(
        val recordId: Long,
        val type: RecordType,
    ) : MovementAction
}

data class MovementTransition(
    val state: MovementState,
    val actions: List<MovementAction>,
)

class MovementDetector {
    fun onFix(state: MovementState, fix: TrackingFix, inVehicle: Boolean): MovementTransition =
        when (state.mode) {
            MovementMode.IDLE -> onIdleFix(state, fix, inVehicle)
            MovementMode.MOVING -> onMovingFix(state, fix)
            MovementMode.STOP_CANDIDATE -> onStopCandidateFix(state, fix)
        }

    fun attachCandidateId(state: MovementState, id: Long): MovementState = state.copy(
        stopCandidate = state.stopCandidate?.copy(recordId = id),
    )

    fun onTrackingStopped(state: MovementState): MovementTransition {
        val candidateId = state.stopCandidate?.recordId
        return MovementTransition(
            state = state.copy(
                mode = MovementMode.IDLE,
                consecutiveMovingFixes = 0,
                stopCandidate = null,
            ),
            actions = if (candidateId != null) {
                listOf(MovementAction.FinalizeCandidate(candidateId, RecordType.TEMP_STOP))
            } else {
                emptyList()
            },
        )
    }

    private fun onIdleFix(
        state: MovementState,
        fix: TrackingFix,
        inVehicle: Boolean,
    ): MovementTransition {
        val isMoving = inVehicle || (fix.speedMetersPerSecond ?: 0.0) >= MOVING_SPEED_MPS
        val movingFixCount = if (isMoving) state.consecutiveMovingFixes + 1 else 0
        val shouldStart = inVehicle || movingFixCount >= REQUIRED_MOVING_FIXES

        return if (shouldStart) {
            MovementTransition(
                state = state.copy(
                    mode = MovementMode.MOVING,
                    consecutiveMovingFixes = 0,
                    lastObservedFix = fix,
                    lastStoredAt = fix.capturedAt,
                    stopCandidate = null,
                ),
                actions = listOf(MovementAction.Insert(RecordType.START, fix, finalized = true)),
            )
        } else {
            MovementTransition(
                state = state.copy(
                    consecutiveMovingFixes = movingFixCount,
                    lastObservedFix = fix,
                ),
                actions = emptyList(),
            )
        }
    }

    private fun onMovingFix(state: MovementState, fix: TrackingFix): MovementTransition {
        if (isBelowStoppedSpeed(fix)) {
            return MovementTransition(
                state = state.copy(
                    mode = MovementMode.STOP_CANDIDATE,
                    lastObservedFix = fix,
                    stopCandidate = StopCandidate(recordId = null, fix = fix),
                ),
                actions = listOf(
                    MovementAction.Insert(RecordType.TEMP_STOP, fix, finalized = false),
                ),
            )
        }

        val periodicDue = fix.capturedAt - (state.lastStoredAt ?: fix.capturedAt) >= PERIODIC_MILLIS
        return MovementTransition(
            state = state.copy(
                lastObservedFix = fix,
                lastStoredAt = if (periodicDue) fix.capturedAt else state.lastStoredAt,
            ),
            actions = if (periodicDue) {
                listOf(MovementAction.Insert(RecordType.PERIODIC, fix, finalized = true))
            } else {
                emptyList()
            },
        )
    }

    private fun onStopCandidateFix(
        state: MovementState,
        fix: TrackingFix,
    ): MovementTransition {
        val candidate = requireNotNull(state.stopCandidate)
        val remainsStopped = isBelowStoppedSpeed(fix) &&
            distanceMeters(candidate.fix, fix) <= STOP_RADIUS_METERS
        val stoppedLongEnough = fix.capturedAt - candidate.fix.capturedAt >= STOP_DURATION_MILLIS

        if (remainsStopped && !stoppedLongEnough) {
            return MovementTransition(state.copy(lastObservedFix = fix), emptyList())
        }

        val finalType = if (remainsStopped) RecordType.STOP else RecordType.TEMP_STOP
        val action = candidate.recordId?.let {
            MovementAction.FinalizeCandidate(it, finalType)
        }
        return MovementTransition(
            state = state.copy(
                mode = if (finalType == RecordType.STOP) MovementMode.IDLE else MovementMode.MOVING,
                consecutiveMovingFixes = 0,
                lastObservedFix = fix,
                stopCandidate = null,
            ),
            actions = listOfNotNull(action),
        )
    }

    private fun isBelowStoppedSpeed(fix: TrackingFix): Boolean =
        fix.speedMetersPerSecond?.let { it < STOPPED_SPEED_MPS } == true

    private fun distanceMeters(first: TrackingFix, second: TrackingFix): Double {
        val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(firstLatitude) * cos(secondLatitude) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    companion object {
        const val PERIODIC_MILLIS = 120_000L
        const val MOVING_SPEED_MPS = 5.0 / 3.6
        const val STOPPED_SPEED_MPS = 3.0 / 3.6
        const val STOP_RADIUS_METERS = 30.0
        private const val STOP_DURATION_MILLIS = 120_000L
        private const val REQUIRED_MOVING_FIXES = 2
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
