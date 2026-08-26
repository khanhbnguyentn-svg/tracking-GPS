package com.internal.tracker.diagnostics

import com.internal.tracker.tracking.TrackingFix
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class ObservedFix(val fix: TrackingFix, val receivedAt: Long)

data class DiagnosticFinding(
    val type: IncidentType,
    val reasonCodes: Set<String>,
    val confidenceScore: Int,
    val confidenceBand: ConfidenceBand,
    val openedAt: Long,
    val recoveredAt: Long?,
    val samples: List<ObservedFix>,
)

class TrajectoryAnomalyDetector {
    private val buffer = mutableListOf<ObservedFix>()
    private var lastCapturedAt: Long? = null

    fun onFix(observed: ObservedFix): List<DiagnosticFinding> {
        val immediate = timestampFinding(observed)
        lastCapturedAt = maxOf(lastCapturedAt ?: Long.MIN_VALUE, observed.fix.capturedAt)
        buffer += observed
        if (buffer.size < WINDOW_SIZE) return listOfNotNull(immediate)
        while (buffer.size > WINDOW_SIZE) buffer.removeAt(0)

        val spatial = spatialFinding(buffer.toList())
        buffer.removeAt(0)
        return listOfNotNull(immediate, spatial)
    }

    fun recentFixes(): List<ObservedFix> = buffer.takeLast(PRIOR_SAMPLE_COUNT)

    private fun timestampFinding(observed: ObservedFix): DiagnosticFinding? {
        val reason = when {
            lastCapturedAt != null && observed.fix.capturedAt <= lastCapturedAt!! -> "TIMESTAMP_ORDER"
            abs(observed.receivedAt - observed.fix.capturedAt) > STALE_MILLIS -> "STALE_TIMESTAMP"
            else -> return null
        }
        val score = if (reason == "TIMESTAMP_ORDER") 4 else 2
        return DiagnosticFinding(
            type = IncidentType.TIMESTAMP_ANOMALY,
            reasonCodes = setOf(reason),
            confidenceScore = score,
            confidenceBand = band(score),
            openedAt = observed.fix.capturedAt,
            recoveredAt = observed.fix.capturedAt,
            samples = listOf(observed),
        )
    }

    private fun spatialFinding(window: List<ObservedFix>): DiagnosticFinding? {
        val before = window[TRIGGER_INDEX - 1]
        val trigger = window[TRIGGER_INDEX]
        val after = window[TRIGGER_INDEX + 1]
        val outbound = effectiveDistance(before, trigger)
        val inbound = effectiveDistance(trigger, after)
        val continuity = effectiveDistance(before, after)
        val triggerSpeed = speed(outbound, before, trigger)
        val priorSpeeds = (1 until TRIGGER_INDEX).map { index ->
            speed(effectiveDistance(window[index - 1], window[index]), window[index - 1], window[index])
        }.sorted()
        val baseline = priorSpeeds.getOrElse(priorSpeeds.size / 2) { 0.0 }

        val reasons = linkedSetOf<String>()
        if (outbound > MIN_JUMP_METERS && inbound > MIN_JUMP_METERS && continuity < minOf(outbound, inbound) * RETURN_RATIO) {
            reasons += "SPATIAL_ISOLATION"
        }
        val deviceSpeed = trigger.fix.speedMetersPerSecond ?: baseline
        if (triggerSpeed > max(MIN_SPIKE_MPS, deviceSpeed * 3.0)) reasons += "SPEED_DISAGREEMENT"
        if (triggerSpeed > max(MIN_SPIKE_MPS, baseline * 3.0)) reasons += "VELOCITY_SPIKE"
        if (directionCosine(before, trigger, after) < DIRECTION_RETURN_COSINE) reasons += "DIRECTION_RETURN"
        if (reasons.size < 2 || "SPATIAL_ISOLATION" !in reasons) return null

        val score = reasons.sumOf {
            when (it) {
                "SPATIAL_ISOLATION" -> 3
                else -> 2
            }
        }
        return DiagnosticFinding(
            type = IncidentType.SUSPECTED_GPS_JUMP,
            reasonCodes = reasons,
            confidenceScore = score,
            confidenceBand = band(score),
            openedAt = trigger.fix.capturedAt,
            recoveredAt = window.last().fix.capturedAt,
            samples = window,
        )
    }

    private fun effectiveDistance(first: ObservedFix, second: ObservedFix): Double =
        (distanceMeters(first.fix, second.fix) - (first.fix.accuracy ?: 0.0) - (second.fix.accuracy ?: 0.0))
            .coerceAtLeast(0.0)

    private fun speed(distance: Double, first: ObservedFix, second: ObservedFix): Double {
        val seconds = (second.fix.capturedAt - first.fix.capturedAt) / 1_000.0
        return if (seconds > 0) distance / seconds else 0.0
    }

    private fun directionCosine(first: ObservedFix, middle: ObservedFix, last: ObservedFix): Double {
        val ax = middle.fix.longitude - first.fix.longitude
        val ay = middle.fix.latitude - first.fix.latitude
        val bx = last.fix.longitude - middle.fix.longitude
        val by = last.fix.latitude - middle.fix.latitude
        val denominator = sqrt(ax * ax + ay * ay) * sqrt(bx * bx + by * by)
        return if (denominator == 0.0) 1.0 else (ax * bx + ay * by) / denominator
    }

    private fun distanceMeters(first: TrackingFix, second: TrackingFix): Double {
        val latDelta = Math.toRadians(second.latitude - first.latitude)
        val lonDelta = Math.toRadians(second.longitude - first.longitude)
        val firstLat = Math.toRadians(first.latitude)
        val secondLat = Math.toRadians(second.latitude)
        val a = sin(latDelta / 2) * sin(latDelta / 2) +
            cos(firstLat) * cos(secondLat) * sin(lonDelta / 2) * sin(lonDelta / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun band(score: Int) = when {
        score >= 8 -> ConfidenceBand.HIGH
        score >= 6 -> ConfidenceBand.MEDIUM
        else -> ConfidenceBand.LOW
    }

    private companion object {
        const val WINDOW_SIZE = 10
        const val PRIOR_SAMPLE_COUNT = 6
        const val TRIGGER_INDEX = 6
        const val STALE_MILLIS = 60_000L
        const val MIN_JUMP_METERS = 100.0
        const val MIN_SPIKE_MPS = 15.0
        const val RETURN_RATIO = 0.35
        const val DIRECTION_RETURN_COSINE = -0.5
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
