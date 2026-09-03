package com.internal.tracker.tracking.movement

import com.internal.tracker.tracking.model.MovementEventType
import com.internal.tracker.tracking.model.PersistedRawSample
import com.internal.tracker.tracking.model.RawLocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementClassifierTest {
    @Test
    fun confirmsTempStopOnlyAfterSixtySecondsInsideTwentyMeters() {
        val classifier = MovementClassifier()

        assertTrue(classifier.onPersistedSample(persisted(stopped(0), 1)).isEmpty())
        assertTrue(classifier.onPersistedSample(persisted(stopped(50), 2)).isEmpty())

        val event = classifier.onPersistedSample(persisted(stopped(60), 3)).single()
        assertEquals(MovementEventType.TEMP_STOP_STARTED, event.type)
        assertEquals(0L, event.effectiveAtUtcMillis)
        assertEquals(60_000L, event.confirmedAtUtcMillis)
        assertEquals(1L, event.firstSourceSequenceNumber)
        assertEquals(3L, event.confirmingSourceSequenceNumber)
        assertEquals(1, event.algorithmVersion)
    }

    @Test
    fun resumesAfterTwoMovingSamples() {
        val classifier = MovementClassifier()
        classifier.onPersistedSample(persisted(stopped(0), 1))
        classifier.onPersistedSample(persisted(stopped(60), 2))

        assertTrue(classifier.onPersistedSample(persisted(moving(70), 3)).isEmpty())
        val resumed = classifier.onPersistedSample(persisted(moving(80), 4)).single()

        assertEquals(MovementEventType.RESUME, resumed.type)
        assertEquals(70_000L, resumed.effectiveAtUtcMillis)
        assertEquals(80_000L, resumed.confirmedAtUtcMillis)
        assertEquals(3L, resumed.firstSourceSequenceNumber)
        assertEquals(4L, resumed.confirmingSourceSequenceNumber)
    }

    @Test
    fun derivesStoppedClassifierSpeedWhenSourceSpeedIsMissing() {
        val classifier = MovementClassifier()

        assertTrue(classifier.onSample(sample(0, 10.7769, 106.7009, speed = null)).isEmpty())
        assertTrue(classifier.onSample(sample(60, 10.7769, 106.7009, speed = null)).isEmpty())

        assertEquals(
            MovementEventType.TEMP_STOP_STARTED,
            classifier.onSample(sample(120, 10.7769, 106.7009, speed = null)).single().type,
        )
    }

    @Test
    fun invalidCoordinatesCannotConfirmTemporaryStop() {
        val classifier = MovementClassifier()

        assertTrue(classifier.onSample(sample(0, Double.NaN, 106.7009, speed = 0.5f)).isEmpty())
        assertTrue(classifier.onSample(sample(60, Double.NaN, 106.7009, speed = 0.5f)).isEmpty())
        assertTrue(classifier.onSample(sample(120, Double.NaN, 106.7009, speed = 0.5f)).isEmpty())
    }

    @Test
    fun nonIncreasingElapsedTimeInvalidatesPendingTemporaryStopCandidate() {
        val classifier = MovementClassifier()

        assertTrue(classifier.onPersistedSample(persisted(stopped(0), 1)).isEmpty())
        assertTrue(classifier.onPersistedSample(persisted(stopped(60).copy(elapsedRealtimeNanos = 0L), 2)).isEmpty())

        assertTrue(classifier.onPersistedSample(persisted(stopped(60), 3)).isEmpty())
    }

    @Test
    fun resumesAfterTwoLowSpeedSamplesOutsideStopRadius() {
        val classifier = MovementClassifier()
        classifier.onSample(stopped(0))
        classifier.onSample(stopped(60))

        assertTrue(classifier.onSample(sample(70, 10.7773, 106.7009, speed = 0.5f)).isEmpty())

        val resumed = classifier.onSample(sample(80, 10.7773, 106.7009, speed = 0.5f)).single()
        assertEquals(MovementEventType.RESUME, resumed.type)
        assertEquals(70_000L, resumed.effectiveAtUtcMillis)
    }

    @Test
    fun confirmsTemporaryStopWhenSamplesStayWithinNineteenPointNineMeters() {
        val classifier = MovementClassifier()

        assertTrue(classifier.onSample(stopped(0)).isEmpty())
        assertTrue(classifier.onSample(sample(30, 10.777079, 106.7009, speed = 0.5f)).isEmpty())

        assertEquals(
            MovementEventType.TEMP_STOP_STARTED,
            classifier.onSample(sample(60, 10.777079, 106.7009, speed = 0.5f)).single().type,
        )
    }

    @Test
    fun cancelsTemporaryStopWhenSampleExceedsTwentyPointOneMeters() {
        val classifier = MovementClassifier()

        assertTrue(classifier.onSample(stopped(0)).isEmpty())
        assertTrue(classifier.onSample(sample(30, 10.777081, 106.7009, speed = 0.5f)).isEmpty())
        assertTrue(classifier.onSample(sample(60, 10.777081, 106.7009, speed = 0.5f)).isEmpty())
    }

    private fun stopped(seconds: Long) = sample(seconds, latitude = 10.7769, longitude = 106.7009, speed = 0.5f)
    private fun moving(seconds: Long) = sample(seconds, latitude = 10.7773, longitude = 106.7009, speed = 5f)
    private fun persisted(sample: RawLocationSample, sequenceNumber: Long) = PersistedRawSample(sequenceNumber, sample)

    private fun sample(seconds: Long, latitude: Double, longitude: Double, speed: Float?) = RawLocationSample(
        capturedUtcMillis = seconds * 1_000,
        capturedOffsetMinutes = 420,
        elapsedRealtimeNanos = seconds * 1_000_000_000L,
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = null,
        horizontalAccuracyMeters = null,
        verticalAccuracyMeters = null,
        speedMetersPerSecond = speed,
        speedAccuracyMetersPerSecond = null,
        bearingDegrees = null,
        bearingAccuracyDegrees = null,
        provider = "fused",
        isMock = false,
    )
}
