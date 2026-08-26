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

    suspend fun restore(startedAt: Long): MovementState = mutex.withLock {
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
        state
    }

    suspend fun onFix(fix: TrackingFix, inVehicle: Boolean): TrackingOutcome = mutex.withLock {
        val previous = state
        val transition = detector.onFix(state, fix, inVehicle)
        state = transition.state
        val persisted = execute(transition.actions)
        TrackingOutcome(previous, state, persisted)
    }

    suspend fun stop() = mutex.withLock {
        val transition = detector.onTrackingStopped(state)
        state = transition.state
        execute(transition.actions)
    }

    private suspend fun execute(actions: List<MovementAction>): List<PersistedMovementAction> = buildList {
        actions.forEach { action ->
            when (action) {
                is MovementAction.Insert -> {
                    val id = persist(action.fix, action.type, action.finalized)
                    onPersisted(action.fix)
                    add(PersistedMovementAction.Inserted(id, action.type, action.finalized))
                    if (action.type == RecordType.TEMP_STOP && !action.finalized) {
                        state = detector.attachCandidateId(state, id)
                    }
                }

                is MovementAction.FinalizeCandidate -> history.finalizeStopCandidate(
                    action.recordId,
                    action.type,
                ).also { add(PersistedMovementAction.Finalized(action.recordId, action.type)) }
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

sealed interface PersistedMovementAction {
    val type: RecordType

    data class Inserted(
        val recordId: Long,
        override val type: RecordType,
        val finalized: Boolean,
    ) : PersistedMovementAction

    data class Finalized(val recordId: Long, override val type: RecordType) : PersistedMovementAction
}

data class TrackingOutcome(
    val previousState: MovementState,
    val currentState: MovementState,
    val actions: List<PersistedMovementAction>,
)
