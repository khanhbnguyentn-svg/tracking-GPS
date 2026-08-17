package com.internal.tracker.diagnostics

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticCsvTest {
    private val zone = ZoneId.of("Asia/Bangkok")

    @Test fun emptyExportsStillContainExactHeaders() {
        val empty = DiagnosticBundle(emptyList(), emptyList())

        assertEquals(
            "incident_id,type,state,opened_at,recovered_at,duration_ms,reason_codes,confidence_score,confidence_band,device_condition,evidence_complete,opened_alert_state,recovered_alert_state,reported_at\r\n",
            DiagnosticCsv.summary(empty, zone),
        )
        assertEquals(
            "incident_id,sequence,role,captured_at,received_at,latitude,longitude,accuracy,speed_mps,derived_distance_m,derived_speed_mps,signal_flags\r\n",
            DiagnosticCsv.samples(empty, zone),
        )
    }

    @Test fun summaryIsStableEscapedAndOmitsStoredErrors() {
        val later = incident("b", openedAt = 2_000, reasonCodes = "plain")
        val earlier = incident(
            "a",
            openedAt = 1_000,
            reasonCodes = "jump,\"return\"\nflag",
            openedAlertError = "password=secret stack trace",
        )

        val csv = DiagnosticCsv.summary(DiagnosticBundle(listOf(later, earlier), emptyList()), zone)
        val rows = csv.split("\r\n")

        assertTrue(rows[1].startsWith("a,GPS_GAP,RECOVERED,"))
        assertTrue(rows[1].contains("\"jump,\"\"return\"\"\nflag\""))
        assertTrue(rows[2].startsWith("b,GPS_GAP,RECOVERED,"))
        assertFalse(csv.contains("password"))
        assertFalse(csv.contains("stack trace"))
    }

    @Test fun samplesAreOrderedByIncidentAndSequenceWithOffsetTimes() {
        val samples = listOf(
            sample("b", 0, 2_000),
            sample("a", 2, 3_000),
            sample("a", 1, 1_000),
        )

        val rows = DiagnosticCsv.samples(DiagnosticBundle(emptyList(), samples), zone).split("\r\n")

        assertTrue(rows[1].startsWith("a,1,BEFORE,1970-01-01T07:00:01+07:00,"))
        assertTrue(rows[2].startsWith("a,2,BEFORE,1970-01-01T07:00:03+07:00,"))
        assertTrue(rows[3].startsWith("b,0,BEFORE,1970-01-01T07:00:02+07:00,"))
    }

    private fun incident(
        id: String,
        openedAt: Long,
        reasonCodes: String,
        openedAlertError: String? = null,
    ) = DiagnosticIncident(
        incidentId = id,
        type = IncidentType.GPS_GAP,
        reasonCodes = reasonCodes,
        openedAt = openedAt,
        recoveredAt = openedAt + 30_000,
        state = IncidentState.RECOVERED,
        confidenceScore = 80,
        confidenceBand = ConfidenceBand.HIGH,
        deviceCondition = DeviceCondition.PROVIDER_SILENT,
        evidenceComplete = true,
        openedAlertState = DiagnosticDeliveryState.ACCEPTED,
        openedAlertError = openedAlertError,
        recoveredAlertState = DiagnosticDeliveryState.PENDING,
    )

    private fun sample(id: String, sequence: Int, capturedAt: Long) = DiagnosticSample(
        incidentId = id,
        sequence = sequence,
        role = EvidenceRole.BEFORE,
        capturedAt = capturedAt,
        receivedAt = capturedAt + 100,
        latitude = 10.5,
        longitude = 106.25,
        accuracy = 5.0,
        speedMetersPerSecond = 12.0,
        derivedDistanceMeters = 120.0,
        derivedSpeedMetersPerSecond = 12.5,
        signalFlags = "SPEED,RETURN",
    )
}
