package com.internal.tracker.mail

import com.internal.tracker.config.PilotConfig
import com.internal.tracker.diagnostics.ConfidenceBand
import com.internal.tracker.diagnostics.DeviceCondition
import com.internal.tracker.diagnostics.DiagnosticBundle
import com.internal.tracker.diagnostics.DiagnosticIncident
import com.internal.tracker.diagnostics.DiagnosticReportStore
import com.internal.tracker.diagnostics.IncidentState
import com.internal.tracker.diagnostics.IncidentType
import com.internal.tracker.history.DeliveryState
import com.internal.tracker.history.LocationRecord
import com.internal.tracker.history.LocationRecordStore
import com.internal.tracker.history.RecordType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportDeliveryTest {
    @Test
    fun oneMessageContainsEveryPendingRecord() = runTest {
        val store = FakeMailHistoryStore(listOf(record(1), record(2)))
        val sender = FakeMailSender(MailResult.Accepted)
        val delivery = delivery(store, sender)

        delivery.deliverPending(100_000)

        assertEquals(1, sender.messages.size)
        assertEquals(listOf(1L, 2L), sender.messages.single().recordIds)
        assertEquals(DeliveryState.SENT, store.get(1)!!.state)
        assertEquals(DeliveryState.SENT, store.get(2)!!.state)
    }

    @Test
    fun rejectedAuthenticationRetainsBacklogAndRedactsSecret() = runTest {
        val store = FakeMailHistoryStore(listOf(record(1)))
        val delivery = delivery(store, FakeMailSender(MailResult.AuthenticationRejected))

        val result = delivery.deliverPending(100_000)

        assertEquals("AUTHENTICATION", result.publicError)
        assertEquals(DeliveryState.RETRYING, store.get(1)!!.state)
        assertEquals(1, store.unsent(10).size)
    }

    @Test
    fun unfinishedCandidateIsNotEmailedOrMarkedSent() = runTest {
        val store = FakeMailHistoryStore(
            listOf(
                record(1),
                record(2).copy(recordType = RecordType.TEMP_STOP, isFinalized = false),
            ),
        )
        val sender = FakeMailSender(MailResult.Accepted)

        delivery(store, sender).deliverPending(100_000)

        assertEquals(listOf(1L), sender.messages.single().recordIds)
        assertEquals(DeliveryState.SENT, store.get(1)!!.state)
        assertEquals(DeliveryState.PENDING, store.get(2)!!.state)
    }

    @Test
    fun diagnosticsOnlyRunSendsAndMarksIncluded() = runTest {
        val diagnostics = FakeDiagnosticReportStore(listOf(incident("gap-1")))
        val sender = FakeMailSender(MailResult.Accepted)

        val result = delivery(FakeMailHistoryStore(emptyList()), sender, diagnostics).deliverPending(100_000)

        assertEquals(0, result.sent)
        assertEquals(1, result.diagnosticsSent)
        assertEquals(listOf("gap-1"), diagnostics.reportedIds)
        assertEquals(listOf("gap-1"), sender.messages.single().incidentIds)
    }

    @Test
    fun acceptedCombinedMailMarksRoutesAndDiagnostics() = runTest {
        val routes = FakeMailHistoryStore(listOf(record(1)))
        val diagnostics = FakeDiagnosticReportStore(listOf(incident("gap-1")))

        val result = delivery(routes, FakeMailSender(MailResult.Accepted), diagnostics).deliverPending(100_000)

        assertEquals(1, result.sent)
        assertEquals(1, result.diagnosticsSent)
        assertEquals(DeliveryState.SENT, routes.get(1)!!.state)
        assertEquals(listOf("gap-1"), diagnostics.reportedIds)
    }

    @Test
    fun failedCombinedMailMarksNeitherKindAsDelivered() = runTest {
        val routes = FakeMailHistoryStore(listOf(record(1)))
        val diagnostics = FakeDiagnosticReportStore(listOf(incident("gap-1")))
        val telemetry = FakeDeliveryTelemetryStore()

        val result = delivery(
            routes,
            FakeMailSender(MailResult.NetworkFailure),
            diagnostics,
            telemetry,
        ).deliverPending(100_000)

        assertEquals("NETWORK", result.publicError)
        assertEquals(DeliveryState.RETRYING, routes.get(1)!!.state)
        assertEquals(emptyList<String>(), diagnostics.reportedIds)
        assertEquals(1, telemetry.snapshot().consecutiveFailures)
        assertEquals("NETWORK", telemetry.snapshot().lastFailure)
    }

    @Test
    fun diagnosticReadFailureStillSendsRouteOnly() = runTest {
        val sender = FakeMailSender(MailResult.Accepted)
        val diagnostics = FakeDiagnosticReportStore(emptyList(), failRead = true)

        val result = delivery(FakeMailHistoryStore(listOf(record(1))), sender, diagnostics).deliverPending(100_000)

        assertEquals(1, result.sent)
        assertEquals(0, result.diagnosticsSent)
        assertEquals("DIAGNOSTICS_READ", result.publicError)
        assertEquals(1, sender.messages.single().attachments.size)
    }

    private fun delivery(
        store: LocationRecordStore,
        sender: FakeMailSender,
        diagnostics: DiagnosticReportStore = FakeDiagnosticReportStore(emptyList()),
        telemetry: DeliveryTelemetryStore = FakeDeliveryTelemetryStore(),
    ) = ReportDelivery(
        history = store,
        diagnostics = diagnostics,
        config = { PilotConfig("001", "pic@example.com", 6, "sender@gmail.com", "abcdefghijklmnop") },
        deviceId = { "AND-1" },
        telemetry = telemetry,
        appVersion = "1.0-test",
        sender = sender,
        refreshBackup = {},
        nowMillis = { 2_000 },
    )

    private fun incident(id: String) = DiagnosticIncident(
        incidentId = id,
        type = IncidentType.GPS_GAP,
        reasonCodes = "NO_CALLBACK_30_SECONDS",
        openedAt = 1_000,
        recoveredAt = 41_000,
        state = IncidentState.RECOVERED,
        confidenceScore = 80,
        confidenceBand = ConfidenceBand.HIGH,
        deviceCondition = DeviceCondition.PROVIDER_SILENT,
        evidenceComplete = true,
    )

    private fun record(id: Long) = LocationRecord(
        id = id,
        deviceNumber = "001",
        deviceId = "AND-1",
        capturedAt = 1_000 + id,
        timezone = "Asia/Ho_Chi_Minh",
        latitude = 10.5,
        longitude = 20.25,
        accuracy = 4.5,
        batteryPercent = 82,
        trackedDurationMillis = 60_000,
    )
}

private class FakeDiagnosticReportStore(
    private val incidents: List<DiagnosticIncident>,
    private val failRead: Boolean = false,
) : DiagnosticReportStore {
    val reportedIds = mutableListOf<String>()
    override suspend fun pendingBundle(limit: Int): DiagnosticBundle {
        if (failRead) error("database details")
        return DiagnosticBundle(incidents.take(limit), emptyList())
    }
    override suspend fun markReported(ids: List<String>, reportedAt: Long) { reportedIds += ids }
}

private class FakeDeliveryTelemetryStore : DeliveryTelemetryStore {
    private var value = DeliveryTelemetry(0, 0, 0, null)
    override fun snapshot() = value
    override fun accepted(at: Long) { value = DeliveryTelemetry(at, at, 0, null) }
    override fun failed(at: Long, category: String) {
        value = value.copy(lastAttemptAt = at, consecutiveFailures = value.consecutiveFailures + 1, lastFailure = category)
    }
}

private class FakeMailSender(private val result: MailResult) : MailSender {
    val messages = mutableListOf<ReportMessage>()
    override suspend fun send(config: PilotConfig, message: ReportMessage): MailResult {
        messages += message
        return result
    }
}

private class FakeMailHistoryStore(records: List<LocationRecord>) : LocationRecordStore {
    private val rows = records.associateByTo(linkedMapOf(), LocationRecord::id)
    override suspend fun insert(record: LocationRecord): Long = error("unused")
    override suspend fun get(id: Long): LocationRecord? = rows[id]
    override suspend fun unsent(limit: Int) = rows.values
        .filter { it.state != DeliveryState.SENT && it.isFinalized }
        .take(limit)
    override suspend fun count() = rows.size
    override fun observeBetween(from: Long, until: Long): Flow<List<LocationRecord>> = flowOf(emptyList())
    override fun observeOldestCapturedAt(): Flow<Long?> = flowOf(rows.values.minOfOrNull(LocationRecord::capturedAt))
    override suspend fun between(from: Long, until: Long): List<LocationRecord> = emptyList()
    override suspend fun activeStopCandidate(since: Long): LocationRecord? = rows.values
        .filter { it.recordType == RecordType.TEMP_STOP && !it.isFinalized && it.capturedAt >= since }
        .maxWithOrNull(compareBy<LocationRecord> { it.capturedAt }.thenBy { it.id })
    override suspend fun latestSince(since: Long): LocationRecord? = rows.values
        .filter { it.capturedAt >= since }
        .maxWithOrNull(compareBy<LocationRecord> { it.capturedAt }.thenBy { it.id })
    override suspend fun deleteOlderThan(beforeMillis: Long) {
        rows.entries.removeAll { it.value.capturedAt < beforeMillis }
    }
    override suspend fun deleteBetween(from: Long, until: Long) {
        rows.entries.removeAll { it.value.capturedAt in from until until }
    }
    override suspend fun deleteAll() = rows.clear()
    override suspend fun finalizeStopCandidate(id: Long, recordType: RecordType) {
        rows[id]?.takeIf { !it.isFinalized }?.let {
            rows[id] = it.copy(recordType = recordType, isFinalized = true)
        }
    }
    override suspend fun markRetrying(ids: List<Long>, error: String) {
        ids.forEach { id -> rows[id]?.let { rows[id] = it.copy(state = DeliveryState.RETRYING, attemptCount = it.attemptCount + 1, lastError = error) } }
    }
    override suspend fun markSent(ids: List<Long>, sentAt: Long) {
        ids.forEach { id -> rows[id]?.let { rows[id] = it.copy(state = DeliveryState.SENT, sentAt = sentAt, lastError = null) } }
    }
}
