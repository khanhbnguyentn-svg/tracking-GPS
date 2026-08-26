package com.internal.tracker.diagnostics

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DiagnosticCsv {
    private val summaryHeader = listOf(
        "incident_id", "type", "state", "opened_at", "recovered_at", "duration_ms",
        "reason_codes", "confidence_score", "confidence_band", "device_condition",
        "evidence_complete", "opened_alert_state", "recovered_alert_state", "reported_at",
    )
    private val sampleHeader = listOf(
        "incident_id", "sequence", "role", "captured_at", "received_at", "latitude",
        "longitude", "accuracy", "speed_mps", "derived_distance_m", "derived_speed_mps",
        "signal_flags",
    )

    fun summary(bundle: DiagnosticBundle, zone: ZoneId): String = encodeRows(
        summaryHeader,
        bundle.incidents
            .sortedWith(compareBy(DiagnosticIncident::openedAt, DiagnosticIncident::incidentId))
            .map { incident ->
                listOf(
                    incident.incidentId,
                    incident.type.name,
                    incident.state.name,
                    timestamp(incident.openedAt, zone),
                    incident.recoveredAt?.let { timestamp(it, zone) }.orEmpty(),
                    incident.recoveredAt?.let { (it - incident.openedAt).toString() }.orEmpty(),
                    incident.reasonCodes,
                    incident.confidenceScore.toString(),
                    incident.confidenceBand.name,
                    incident.deviceCondition.name,
                    incident.evidenceComplete.toString(),
                    incident.openedAlertState.name,
                    incident.recoveredAlertState.name,
                    incident.reportedAt?.let { timestamp(it, zone) }.orEmpty(),
                )
            },
    )

    fun samples(bundle: DiagnosticBundle, zone: ZoneId): String = encodeRows(
        sampleHeader,
        bundle.samples
            .sortedWith(compareBy(DiagnosticSample::incidentId, DiagnosticSample::sequence))
            .map { sample ->
                listOf(
                    sample.incidentId,
                    sample.sequence.toString(),
                    sample.role.name,
                    timestamp(sample.capturedAt, zone),
                    timestamp(sample.receivedAt, zone),
                    sample.latitude.toString(),
                    sample.longitude.toString(),
                    sample.accuracy?.toString().orEmpty(),
                    sample.speedMetersPerSecond?.toString().orEmpty(),
                    sample.derivedDistanceMeters?.toString().orEmpty(),
                    sample.derivedSpeedMetersPerSecond?.toString().orEmpty(),
                    sample.signalFlags,
                )
            },
    )

    private fun timestamp(epochMillis: Long, zone: ZoneId): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    private fun encodeRows(header: List<String>, rows: List<List<String>>): String = buildString {
        append(header.joinToString(","))
        append("\r\n")
        rows.forEach { row ->
            append(row.joinToString(",", transform = ::escape))
            append("\r\n")
        }
    }

    private fun escape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (value.any { it == ',' || it == '\"' || it == '\r' || it == '\n' }) "\"$escaped\"" else escaped
    }
}
