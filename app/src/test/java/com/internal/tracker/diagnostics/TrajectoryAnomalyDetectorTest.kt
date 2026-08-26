package com.internal.tracker.diagnostics

import com.internal.tracker.tracking.TrackingFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrajectoryAnomalyDetectorTest {
    @Test
    fun isolatedJumpAndReturnProducesOneFindingAfterFutureEvidence() {
        val detector = TrajectoryAnomalyDetector()
        val fixes = mutableListOf<ObservedFix>()
        repeat(6) { index -> fixes += observed(index * 10_000L, latitude = 10.0 + index * 0.0001) }
        fixes += observed(60_000, latitude = 10.05, speed = 5.0)
        fixes += observed(70_000, latitude = 10.0007)
        fixes += observed(80_000, latitude = 10.0008)
        fixes += observed(90_000, latitude = 10.0009)

        val findings = fixes.flatMap(detector::onFix)

        assertEquals(1, findings.size)
        assertEquals(IncidentType.SUSPECTED_GPS_JUMP, findings.single().type)
        assertTrue("SPATIAL_ISOLATION" in findings.single().reasonCodes)
        assertEquals(10, findings.single().samples.size)
    }

    @Test
    fun consistentHighSpeedTrajectoryIsNotSuspicious() {
        val detector = TrajectoryAnomalyDetector()
        val findings = (0..14).flatMap { index ->
            detector.onFix(observed(index * 10_000L, latitude = 10.0 + index * 0.0045, speed = 50.0))
        }
        assertTrue(findings.isEmpty())
    }

    @Test
    fun poorAccuracyAloneNeverCreatesAnIncident() {
        val detector = TrajectoryAnomalyDetector()
        val findings = (0..9).flatMap { index ->
            val latitude = if (index == 6) 10.05 else 10.0 + index * 0.0001
            detector.onFix(observed(index * 10_000L, latitude, accuracy = 10_000.0))
        }
        assertTrue(findings.isEmpty())
    }

    @Test
    fun nonIncreasingAndStaleTimestampsAreReportedWithoutDroppingFixes() {
        val detector = TrajectoryAnomalyDetector()
        assertTrue(detector.onFix(observed(10_000, 10.0)).isEmpty())

        val reversed = detector.onFix(observed(9_000, 10.0001, receivedAt = 10_000))
        val stale = detector.onFix(observed(20_000, 10.0002, receivedAt = 90_001))

        assertEquals("TIMESTAMP_ORDER", reversed.single().reasonCodes.single())
        assertEquals("STALE_TIMESTAMP", stale.single().reasonCodes.single())
    }

    private fun observed(
        capturedAt: Long,
        latitude: Double,
        speed: Double? = 5.0,
        accuracy: Double = 5.0,
        receivedAt: Long = capturedAt,
    ) = ObservedFix(
        fix = TrackingFix(
            latitude = latitude,
            longitude = 106.0,
            accuracy = accuracy,
            capturedAt = capturedAt,
            timezone = "Asia/Bangkok",
            speedMetersPerSecond = speed,
        ),
        receivedAt = receivedAt,
    )
}
