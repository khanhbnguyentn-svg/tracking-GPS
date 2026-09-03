package com.internal.tracker.tracking.movement

import com.internal.tracker.tracking.model.MovementEvent
import com.internal.tracker.tracking.model.MovementEventType
import com.internal.tracker.tracking.model.PersistedRawSample
import com.internal.tracker.tracking.model.MovementState
import com.internal.tracker.tracking.model.RawLocationSample
import kotlin.math.max

class MovementClassifier {
    private var state = MovementState.MOVING
    private var stopCandidate: RawLocationSample? = null
    private var stopCandidateSequenceNumber: Long? = null
    private var consecutiveMovingSamples = 0
    private var firstMovingSample: RawLocationSample? = null
    private var firstMovingSequenceNumber: Long? = null
    private var previousSample: RawLocationSample? = null

    fun onPersistedSample(sample: PersistedRawSample): List<MovementEvent> =
        onSample(sample.sample, sample.sequenceNumber)

    fun onSample(sample: RawLocationSample): List<MovementEvent> = onSample(sample, null)

    private fun onSample(sample: RawLocationSample, sourceSequenceNumber: Long?): List<MovementEvent> {
        if (!isValidObservation(sample)) {
            clearTransientState()
            previousSample = sample
            return emptyList()
        }
        val events = when (state) {
            MovementState.MOVING -> beginStopCandidateIfEligible(sample, sourceSequenceNumber)
            MovementState.STOP_CANDIDATE -> advanceStopCandidate(sample, sourceSequenceNumber)
            MovementState.TEMP_STOP -> evaluateResume(sample, sourceSequenceNumber)
        }
        previousSample = sample
        return events
    }

    private fun isValidObservation(sample: RawLocationSample): Boolean =
        hasValidCoordinates(sample) && (previousSample?.let { sample.elapsedRealtimeNanos > it.elapsedRealtimeNanos } ?: true)

    private fun clearTransientState() {
        state = MovementState.MOVING
        stopCandidate = null
        stopCandidateSequenceNumber = null
        consecutiveMovingSamples = 0
        firstMovingSample = null
        firstMovingSequenceNumber = null
    }

    private fun beginStopCandidateIfEligible(sample: RawLocationSample, sourceSequenceNumber: Long?): List<MovementEvent> {
        if (isStopped(sample)) {
            state = MovementState.STOP_CANDIDATE
            stopCandidate = sample
            stopCandidateSequenceNumber = sourceSequenceNumber
        }
        return emptyList()
    }

    private fun advanceStopCandidate(sample: RawLocationSample, sourceSequenceNumber: Long?): List<MovementEvent> {
        val candidate = checkNotNull(stopCandidate)
        if (!isStopped(sample) || Haversine.meters(candidate.latitude, candidate.longitude, sample.latitude, sample.longitude) > STOP_RADIUS_METERS) {
            state = MovementState.MOVING
            stopCandidate = null
            stopCandidateSequenceNumber = null
            return emptyList()
        }
        if (sample.elapsedRealtimeNanos - candidate.elapsedRealtimeNanos < TEMP_STOP_NANOS) return emptyList()

        state = MovementState.TEMP_STOP
        consecutiveMovingSamples = 0
        firstMovingSample = null
        firstMovingSequenceNumber = null
        return listOf(
            MovementEvent(
                type = MovementEventType.TEMP_STOP_STARTED,
                effectiveAtUtcMillis = candidate.capturedUtcMillis,
                confirmedAtUtcMillis = sample.capturedUtcMillis,
                firstSourceSequenceNumber = stopCandidateSequenceNumber ?: 0L,
                confirmingSourceSequenceNumber = sourceSequenceNumber ?: 0L,
            ),
        )
    }

    private fun evaluateResume(sample: RawLocationSample, sourceSequenceNumber: Long?): List<MovementEvent> {
        if (!isMoving(sample) && !isOutsideStopRadius(sample)) {
            consecutiveMovingSamples = 0
            firstMovingSample = null
            firstMovingSequenceNumber = null
            return emptyList()
        }
        if (consecutiveMovingSamples == 0) {
            firstMovingSample = sample
            firstMovingSequenceNumber = sourceSequenceNumber
        }
        consecutiveMovingSamples += 1
        if (consecutiveMovingSamples < RESUME_SAMPLE_COUNT) return emptyList()

        val resumeSample = checkNotNull(firstMovingSample)
        state = MovementState.MOVING
        stopCandidate = null
        consecutiveMovingSamples = 0
        firstMovingSample = null
        val resumeSequenceNumber = firstMovingSequenceNumber
        firstMovingSequenceNumber = null
        return listOf(
            MovementEvent(
                type = MovementEventType.RESUME,
                effectiveAtUtcMillis = resumeSample.capturedUtcMillis,
                confirmedAtUtcMillis = sample.capturedUtcMillis,
                firstSourceSequenceNumber = resumeSequenceNumber ?: 0L,
                confirmingSourceSequenceNumber = sourceSequenceNumber ?: 0L,
            ),
        )
    }

    private fun isStopped(sample: RawLocationSample): Boolean =
        hasValidCoordinates(sample) && classifierSpeed(sample)?.let { it < MOVING_SPEED_METERS_PER_SECOND } == true

    private fun isMoving(sample: RawLocationSample): Boolean =
        hasValidCoordinates(sample) && classifierSpeed(sample)?.let { it >= MOVING_SPEED_METERS_PER_SECOND } == true

    private fun hasValidCoordinates(sample: RawLocationSample): Boolean =
        sample.latitude.isFinite() && sample.longitude.isFinite() &&
            sample.latitude in -90.0..90.0 && sample.longitude in -180.0..180.0

    private fun isOutsideStopRadius(sample: RawLocationSample): Boolean {
        val candidate = stopCandidate ?: return false
        return hasValidCoordinates(sample) &&
            Haversine.meters(candidate.latitude, candidate.longitude, sample.latitude, sample.longitude) > STOP_RADIUS_METERS
    }

    private fun classifierSpeed(sample: RawLocationSample): Float? {
        sample.speedMetersPerSecond?.let { return it }
        val previous = previousSample ?: return null
        val elapsedNanos = sample.elapsedRealtimeNanos - previous.elapsedRealtimeNanos
        if (elapsedNanos <= 0L) return null
        val distance = Haversine.meters(previous.latitude, previous.longitude, sample.latitude, sample.longitude)
        val accuracy = (previous.horizontalAccuracyMeters ?: 0f) + (sample.horizontalAccuracyMeters ?: 0f)
        return (max(0.0, distance - accuracy) / (elapsedNanos / NANOS_PER_SECOND.toDouble())).toFloat()
    }

    private companion object {
        const val MOVING_SPEED_METERS_PER_SECOND = 1f
        const val STOP_RADIUS_METERS = 20.0
        const val TEMP_STOP_NANOS = 60_000_000_000L
        const val RESUME_SAMPLE_COUNT = 2
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
