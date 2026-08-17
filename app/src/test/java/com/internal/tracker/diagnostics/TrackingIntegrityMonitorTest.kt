package com.internal.tracker.diagnostics

import com.internal.tracker.tracking.TrackingFix
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class TrackingIntegrityMonitorTest {
    @Test
    fun gapOpenAndRecoveryShareIncidentIdWithoutDuplicates() = runTest {
        val fixture = fixture()
        fixture.monitor.onStarted(true, nowWall = 1_000, nowElapsed = 0, lastCallbackWall = 0, condition = DeviceCondition.NORMAL)

        assertEquals(
            IntegrityDirective.RE_REGISTER_LOCATION,
            fixture.monitor.onHealthTick(nowWall = 31_000, nowElapsed = 30_000, condition = DeviceCondition.PROVIDER_SILENT),
        )
        assertEquals(IntegrityDirective.NONE, fixture.monitor.onHealthTick(41_000, 40_000, DeviceCondition.PROVIDER_SILENT))
        fixture.monitor.onLocationReceived(fix(41_000), receivedAt = 41_000, elapsedAt = 40_000)

        assertEquals(listOf("gap-1:OPENED", "gap-1:RECOVERED"), fixture.scheduler.keys)
        assertEquals(IncidentState.RECOVERED, fixture.repository.incident("gap-1")!!.state)
        assertEquals(1, fixture.store.incidents.size)
    }

    @Test
    fun startupInfersRecoveredGapOnlyFromPositiveHeartbeat() = runTest {
        val withHeartbeat = fixture()
        withHeartbeat.monitor.onStarted(true, 41_000, 500, 1_000, DeviceCondition.REBOOT)
        assertEquals(listOf("gap-1:OPENED", "gap-1:RECOVERED"), withHeartbeat.scheduler.keys)
        assertFalse(withHeartbeat.repository.incident("gap-1")!!.evidenceComplete)

        val firstStart = fixture()
        firstStart.monitor.onStarted(true, 41_000, 500, 0, DeviceCondition.NORMAL)
        assertEquals(emptyList<String>(), firstStart.scheduler.keys)
    }

    @Test
    fun disabledTrackingCreatesNoHistoricalGap() = runTest {
        val fixture = fixture()
        fixture.monitor.onStarted(false, 41_000, 500, 1_000, DeviceCondition.REBOOT)
        assertEquals(0, fixture.store.incidents.size)
    }

    @Test
    fun suspiciousTrajectoryIsPersistedWithoutImmediateAlert() = runTest {
        val fixture = fixture()
        fixture.monitor.onStarted(true, 0, 0, 0, DeviceCondition.NORMAL)
        repeat(6) { index -> fixture.monitor.onLocationReceived(fix(index * 10_000L, 10.0 + index * .0001), index * 10_000L, index * 10_000L) }
        fixture.monitor.onLocationReceived(fix(60_000, 10.05), 60_000, 60_000)
        fixture.monitor.onLocationReceived(fix(70_000, 10.0007), 70_000, 70_000)
        fixture.monitor.onLocationReceived(fix(80_000, 10.0008), 80_000, 80_000)
        fixture.monitor.onLocationReceived(fix(90_000, 10.0009), 90_000, 90_000)

        val anomaly = fixture.store.incidents.values.single { it.type == IncidentType.SUSPECTED_GPS_JUMP }
        assertNotNull(anomaly)
        assertEquals(emptyList<String>(), fixture.scheduler.keys)
    }

    private fun fixture(): Fixture {
        val store = MonitorDiagnosticStore()
        val repository = DiagnosticRepository(store, incidentIds = sequenceOf("gap-1", "finding-1", "finding-2").iterator()::next)
        val scheduler = RecordingAlertScheduler()
        val monitor = TrackingIntegrityMonitor(
            gapDetector = GpsGapDetector(),
            trajectoryDetector = TrajectoryAnomalyDetector(),
            sequenceValidator = EventSequenceValidator(),
            repository = repository,
            alertScheduler = scheduler,
            onError = { throw AssertionError(it) },
        )
        return Fixture(store, repository, scheduler, monitor)
    }

    private fun fix(at: Long, latitude: Double = 10.0) = TrackingFix(
        latitude, 106.0, 5.0, at, "Asia/Bangkok", 5.0,
    )

    private data class Fixture(
        val store: MonitorDiagnosticStore,
        val repository: DiagnosticRepository,
        val scheduler: RecordingAlertScheduler,
        val monitor: TrackingIntegrityMonitor,
    )
}

private class RecordingAlertScheduler : DiagnosticAlertScheduler {
    val keys = mutableListOf<String>()
    override fun enqueue(incidentId: String, phase: DiagnosticAlertPhase) {
        keys += "$incidentId:${phase.name}"
    }
}

private class MonitorDiagnosticStore : DiagnosticStore {
    val incidents = linkedMapOf<String, DiagnosticIncident>()
    private val samples = mutableListOf<DiagnosticSample>()
    override suspend fun upsertIncident(incident: DiagnosticIncident) { incidents[incident.incidentId] = incident }
    override suspend fun incident(id: String) = incidents[id]
    override suspend fun openIncident(type: IncidentType) = incidents.values.firstOrNull { it.type == type && it.state == IncidentState.OPEN }
    override suspend fun insertSamples(values: List<DiagnosticSample>) { samples += values }
    override suspend fun pendingForReport(limit: Int) = incidents.values.filter { it.reportedAt == null }.take(limit)
    override suspend fun samplesFor(incidentIds: List<String>) = samples.filter { it.incidentId in incidentIds }
    override suspend fun deleteIncidentsBefore(before: Long) = Unit
    override suspend fun deleteReportedSamplesBefore(before: Long) = Unit
}
