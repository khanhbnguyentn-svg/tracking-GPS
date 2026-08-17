package com.internal.tracker.diagnostics

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DiagnosticRepositoryTest {
    @Test
    fun recoveringGapUpdatesTheSameIncident() = runTest {
        val store = FakeDiagnosticStore()
        val repository = DiagnosticRepository(store, incidentIds = { "gap-1" })

        val opened = repository.openGap(
            openedAt = 1_000,
            condition = DeviceCondition.PROVIDER_SILENT,
            lastFix = DiagnosticLocation(900, 10.0, 106.0),
        )
        val recovered = repository.recoverGap(
            incidentId = opened.incidentId,
            recoveredAt = 41_000,
            firstFix = DiagnosticLocation(41_000, 10.1, 106.1),
            evidenceComplete = true,
        )

        assertEquals("gap-1", recovered.incidentId)
        assertEquals(IncidentState.RECOVERED, recovered.state)
        assertEquals(40_000L, recovered.recoveredAt!! - recovered.openedAt)
        assertEquals(1, store.incidents.size)
    }

    @Test
    fun reusesExistingOpenGap() = runTest {
        val store = FakeDiagnosticStore()
        val repository = DiagnosticRepository(store, incidentIds = { "unused" })

        val first = repository.openGap(1_000, DeviceCondition.PROVIDER_SILENT, null)
        val second = repository.openGap(2_000, DeviceCondition.LOCATION_DISABLED, null)

        assertEquals(first.incidentId, second.incidentId)
        assertEquals(1, store.incidents.size)
    }

    @Test
    fun pendingBundleIsOldestFirstWithMatchingSamples() = runTest {
        val store = FakeDiagnosticStore()
        val repository = DiagnosticRepository(store)
        repository.save(incident("later", 2_000), listOf(sample("later", 0)))
        repository.save(incident("earlier", 1_000), listOf(sample("earlier", 0)))

        val bundle = repository.pendingBundle(1)

        assertEquals(listOf("earlier"), bundle.incidents.map { it.incidentId })
        assertEquals(listOf("earlier"), bundle.samples.map { it.incidentId })
    }

    @Test
    fun deliveryPhasesAndRetentionAreIndependent() = runTest {
        val store = FakeDiagnosticStore()
        val repository = DiagnosticRepository(store)
        repository.save(incident("reported-old", 1_000), listOf(sample("reported-old", 0)))
        repository.markOpenedResult("reported-old", acceptedAt = 2_000, error = null)
        repository.markReported(listOf("reported-old"), reportedAt = 3_000)
        repository.save(incident("unreported-recent", 900_000), listOf(sample("unreported-recent", 0)))
        repository.save(incident("unreported-old", 500), listOf(sample("unreported-old", 0)))

        assertEquals(DiagnosticDeliveryState.ACCEPTED, repository.incident("reported-old")!!.openedAlertState)
        assertEquals(DiagnosticDeliveryState.NOT_REQUIRED, repository.incident("reported-old")!!.recoveredAlertState)

        repository.cleanup(summaryBefore = 800, reportedSamplesBefore = 10_000)

        assertNull(repository.incident("unreported-old"))
        assertNotNull(repository.incident("reported-old"))
        assertEquals(emptyList<DiagnosticSample>(), store.samplesFor(listOf("reported-old")))
        assertEquals(1, store.samplesFor(listOf("unreported-recent")).size)
    }

    @Test
    fun reportedIncidentSummaryIsDeletedAfterOneYearBoundary() = runTest {
        val store = FakeDiagnosticStore()
        val repository = DiagnosticRepository(store)
        repository.save(incident("reported-expired", 100), listOf(sample("reported-expired", 0)))
        repository.markReported(listOf("reported-expired"), reportedAt = 200)

        repository.cleanup(summaryBefore = 800, reportedSamplesBefore = 10_000)

        assertNull(repository.incident("reported-expired"))
    }

    private fun incident(id: String, openedAt: Long) = DiagnosticIncident(
        incidentId = id,
        type = IncidentType.SUSPECTED_GPS_JUMP,
        reasonCodes = "SPATIAL_ISOLATION,SPEED_DISAGREEMENT",
        openedAt = openedAt,
        recoveredAt = openedAt + 30_000,
        state = IncidentState.RECOVERED,
        confidenceScore = 5,
        confidenceBand = ConfidenceBand.LOW,
        deviceCondition = DeviceCondition.NORMAL,
        evidenceComplete = true,
    )

    private fun sample(id: String, sequence: Int) = DiagnosticSample(
        incidentId = id,
        sequence = sequence,
        role = EvidenceRole.TRIGGER,
        capturedAt = 1_000,
        receivedAt = 1_000,
        latitude = 10.0,
        longitude = 106.0,
        accuracy = 5.0,
        speedMetersPerSecond = 1.0,
        derivedDistanceMeters = null,
        derivedSpeedMetersPerSecond = null,
        signalFlags = "",
    )
}

private class FakeDiagnosticStore : DiagnosticStore {
    val incidents = linkedMapOf<String, DiagnosticIncident>()
    private val samples = mutableListOf<DiagnosticSample>()

    override suspend fun upsertIncident(incident: DiagnosticIncident) {
        incidents[incident.incidentId] = incident
    }

    override suspend fun incident(id: String): DiagnosticIncident? = incidents[id]

    override suspend fun openIncident(type: IncidentType): DiagnosticIncident? = incidents.values
        .filter { it.type == type && it.state == IncidentState.OPEN }
        .minWithOrNull(compareBy(DiagnosticIncident::openedAt, DiagnosticIncident::incidentId))

    override suspend fun insertSamples(values: List<DiagnosticSample>) {
        values.forEach { value ->
            samples.removeAll { it.incidentId == value.incidentId && it.sequence == value.sequence }
            samples += value
        }
    }

    override suspend fun pendingForReport(limit: Int): List<DiagnosticIncident> = incidents.values
        .filter { it.reportedAt == null }
        .sortedWith(compareBy(DiagnosticIncident::openedAt, DiagnosticIncident::incidentId))
        .take(limit)

    override suspend fun samplesFor(incidentIds: List<String>): List<DiagnosticSample> = samples
        .filter { it.incidentId in incidentIds }
        .sortedWith(compareBy(DiagnosticSample::incidentId, DiagnosticSample::sequence))

    override suspend fun deleteIncidentsBefore(before: Long) {
        val ids = incidents.values.filter { it.openedAt < before }.map { it.incidentId }
        ids.forEach(incidents::remove)
        samples.removeAll { it.incidentId in ids }
    }

    override suspend fun deleteReportedSamplesBefore(before: Long) {
        samples.removeAll { sample ->
            incidents[sample.incidentId]?.reportedAt?.let { it < before } == true
        }
    }
}
