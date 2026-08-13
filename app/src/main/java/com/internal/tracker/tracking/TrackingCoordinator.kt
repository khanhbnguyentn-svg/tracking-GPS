package com.internal.tracker.tracking

import com.internal.tracker.history.LocationHistoryRepository
import com.internal.tracker.history.LocationRecord
import com.internal.tracker.history.RecordType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TrackingCoordinator(
    private val history: LocationHistoryRepository,
    private val detector: MovementDetector,
    private val persist: suspend (TrackingFix, RecordType, Boolean) -> Long,
    private val onPersisted: (TrackingFix) -> Unit,
) {
    private val mutex = Mutex()
    private var state = MovementState()

    suspend fun restore(startedAt: Long): MovementMode = mutex.withLock {
        val candidate = history.activeStopCandidate(startedAt)
        val latest = history.latestSince(startedAt)
        state = when {
            candidate != null -> MovementState(
                mode = MovementMode.STOP_CANDIDATE,
                lastObservedFix = latest?.toTrackingFix() ?: candidate.toTrackingFix(),
                lastStoredAt = latest?.capturedAt,
                stopCandidate = StopCandidate(candidate.id, candidate.toTrackingFix()),
            )

            latest == null || latest.recordType == RecordType.STOP -> MovementState(
                mode = MovementMode.IDLE,
                lastObservedFix = latest?.toTrackingFix(),
                lastStoredAt = latest?.capturedAt,
            )

            else -> MovementState(
                mode = MovementMode.MOVING,
                lastObservedFix = latest.toTrackingFix(),
                lastStoredAt = latest.capturedAt,
            )
        }
        state.mode
    }

    suspend fun onFix(fix: TrackingFix, inVehicle: Boolean): MovementMode = mutex.withLock {
        val transition = detector.onFix(state, fix, inVehicle)
        state = transition.state
        execute(transition.actions)
        state.mode
    }

    suspend fun stop() = mutex.withLock {
        val transition = detector.onTrackingStopped(state)
        state = transition.state
        execute(transition.actions)
    }

    private suspend fun execute(actions: List<MovementAction>) {
        actions.forEach { action ->
            when (action) {
                is MovementAction.Insert -> {
                    val id = persist(action.fix, action.type, action.finalized)
                    onPersisted(action.fix)
                    if (action.type == RecordType.TEMP_STOP && !action.finalized) {
                        state = detector.attachCandidateId(state, id)
                    }
                }

                is MovementAction.FinalizeCandidate -> history.finalizeStopCandidate(
                    action.recordId,
                    action.type,
                )
            }
        }
    }

    private fun LocationRecord.toTrackingFix() = TrackingFix(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        capturedAt = capturedAt,
        timezone = timezone,
        speedMetersPerSecond = null,
    )
}
