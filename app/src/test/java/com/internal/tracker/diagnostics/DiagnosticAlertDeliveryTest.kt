package com.internal.tracker.diagnostics

import com.internal.tracker.config.PilotConfig
import com.internal.tracker.mail.MailResult
import com.internal.tracker.mail.MailSender
import com.internal.tracker.mail.ReportMessage
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticAlertDeliveryTest {
    @Test fun openedAndRecoveredAlertsShareIdAndMarkOnlyRequestedPhase() = runTest {
        val fixture = fixture(MailResult.Accepted)

        fixture.delivery.deliver("gap-1", DiagnosticAlertPhase.OPENED)
        val afterOpened = fixture.repository.incident("gap-1")!!

        assertTrue(fixture.sender.messages.single().subject.contains("GPS_GAP_OPENED gap-1"))
        assertEquals(2, fixture.sender.messages.single().attachments.size)
        assertEquals(DiagnosticDeliveryState.ACCEPTED, afterOpened.openedAlertState)
        assertEquals(DiagnosticDeliveryState.PENDING, afterOpened.recoveredAlertState)

        fixture.delivery.deliver("gap-1", DiagnosticAlertPhase.RECOVERED)
        val afterRecovered = fixture.repository.incident("gap-1")!!

        assertTrue(fixture.sender.messages.last().subject.contains("GPS_GAP_RECOVERED gap-1"))
        assertTrue(fixture.sender.messages.last().body.contains("Duration: 40000 ms"))
        assertEquals(DiagnosticDeliveryState.ACCEPTED, afterRecovered.recoveredAlertState)
    }

    @Test fun networkFailureIsPersistedOnceWithoutMarkingReported() = runTest {
        val fixture = fixture(MailResult.NetworkFailure)

        val outcome = fixture.delivery.deliver("gap-1", DiagnosticAlertPhase.OPENED)
        val incident = fixture.repository.incident("gap-1")!!

        assertEquals("NETWORK", outcome.publicError)
        assertEquals(DiagnosticDeliveryState.FAILED, incident.openedAlertState)
        assertEquals(1, incident.openedAlertAttempts)
        assertEquals("NETWORK", incident.openedAlertError)
        assertEquals(DiagnosticDeliveryState.PENDING, incident.recoveredAlertState)
        assertNull(incident.reportedAt)
        assertEquals(1, fixture.sender.messages.size)
    }

    @Test fun completedPhaseIsNotSentTwice() = runTest {
        val fixture = fixture(MailResult.Accepted)

        fixture.delivery.deliver("gap-1", DiagnosticAlertPhase.OPENED)
        fixture.delivery.deliver("gap-1", DiagnosticAlertPhase.OPENED)

        assertEquals(1, fixture.sender.messages.size)
        assertEquals(1, fixture.repository.incident("gap-1")!!.openedAlertAttempts)
    }

    private suspend fun fixture(result: MailResult): Fixture {
        val store = AlertDiagnosticStore()
        val repository = DiagnosticRepository(store)
        repository.save(
            DiagnosticIncident(
                incidentId = "gap-1",
                type = IncidentType.GPS_GAP,
                reasonCodes = "NO_CALLBACK_30_SECONDS",
                openedAt = 1_000,
                recoveredAt = 41_000,
                state = IncidentState.RECOVERED,
                deviceCondition = DeviceCondition.PROVIDER_SILENT,
                evidenceComplete = true,
                openedAlertState = DiagnosticDeliveryState.PENDING,
                recoveredAlertState = DiagnosticDeliveryState.PENDING,
            ),
            listOf(sample(0, EvidenceRole.BEFORE), sample(1, EvidenceRole.AFTER)),
        )
        val sender = RecordingDiagnosticSender(result)
        val delivery = DiagnosticAlertDelivery(
            repository = repository,
            config = { PilotConfig("001", "admin@example.com", 6, "sender@gmail.com", "abcdefghijklmnop") },
            sender = sender,
            appVersion = "2.1-test",
            nowMillis = { 50_000 },
            zone = { ZoneId.of("Asia/Bangkok") },
        )
        return Fixture(repository, sender, delivery)
    }

    private fun sample(sequence: Int, role: EvidenceRole) = DiagnosticSample(
        incidentId = "gap-1",
        sequence = sequence,
        role = role,
        capturedAt = 900L + sequence * 41_000L,
        receivedAt = 900L + sequence * 41_000L,
        latitude = 10.0,
        longitude = 106.0,
        accuracy = 5.0,
        speedMetersPerSecond = null,
        derivedDistanceMeters = null,
        derivedSpeedMetersPerSecond = null,
        signalFlags = "",
    )

    private data class Fixture(
        val repository: DiagnosticRepository,
        val sender: RecordingDiagnosticSender,
        val delivery: DiagnosticAlertDelivery,
    )
}

private class RecordingDiagnosticSender(private val result: MailResult) : MailSender {
    val messages = mutableListOf<ReportMessage>()
    override suspend fun send(config: PilotConfig, message: ReportMessage): MailResult {
        messages += message
        return result
    }
}

private class AlertDiagnosticStore : DiagnosticStore {
    private val incidents = linkedMapOf<String, DiagnosticIncident>()
    private val samples = mutableListOf<DiagnosticSample>()
    override suspend fun upsertIncident(incident: DiagnosticIncident) { incidents[incident.incidentId] = incident }
    override suspend fun incident(id: String) = incidents[id]
    override suspend fun openIncident(type: IncidentType) = incidents.values.firstOrNull { it.type == type && it.state == IncidentState.OPEN }
    override suspend fun insertSamples(values: List<DiagnosticSample>) {
        values.forEach { value ->
            samples.removeAll { it.incidentId == value.incidentId && it.sequence == value.sequence }
            samples += value
        }
    }
    override suspend fun pendingForReport(limit: Int) = incidents.values.filter { it.reportedAt == null }.take(limit)
    override suspend fun samplesFor(incidentIds: List<String>) = samples.filter { it.incidentId in incidentIds }
    override suspend fun deleteIncidentsBefore(before: Long) = Unit
    override suspend fun deleteReportedSamplesBefore(before: Long) = Unit
}
