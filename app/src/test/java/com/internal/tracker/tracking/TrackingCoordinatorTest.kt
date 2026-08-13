package com.internal.tracker.tracking

import com.internal.tracker.history.CapturedLocation
import com.internal.tracker.history.DeliveryState
import com.internal.tracker.history.LocationHistoryRepository
import com.internal.tracker.history.LocationRecord
import com.internal.tracker.history.LocationRecordStore
import com.internal.tracker.history.RecordType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingCoordinatorTest {
    @Test
    fun persistsStartAndPromotesStopCandidateOnSameRow() = runTest {
        val store = FakeTrackingStore()
        val coordinator = coordinator(store)
        coordinator.restore(startedAt = 1_000)

        coordinator.onFix(fix(2_000, speedMps = 2.0), inVehicle = false)
        coordinator.onFix(fix(12_000, speedMps = 2.0), inVehicle = false)
        assertEquals(listOf(RecordType.START), store.rows.map { it.recordType })

        coordinator.onFix(fix(20_000, speedMps = 0.0), inVehicle = false)
        assertFalse(store.rows.last().isFinalized)

        coordinator.onFix(fix(140_000, speedMps = 0.0), inVehicle = false)
        assertEquals(2, store.rows.size)
        assertEquals(RecordType.STOP, store.rows.last().recordType)
        assertTrue(store.rows.last().isFinalized)
    }

    @Test
    fun restoreReusesUnfinishedCandidateIdAndAnchor() = runTest {
        val store = FakeTrackingStore()
        val candidateId = store.insert(record(at = 20_000, RecordType.TEMP_STOP, finalized = false))
        val coordinator = coordinator(store)

        assertEquals(MovementMode.STOP_CANDIDATE, coordinator.restore(startedAt = 1_000))
        coordinator.onFix(fix(140_000, speedMps = 0.0), inVehicle = false)

        assertEquals(1, store.rows.size)
        assertEquals(candidateId, store.rows.single().id)
        assertEquals(RecordType.STOP, store.rows.single().recordType)
        assertTrue(store.rows.single().isFinalized)
    }

    @Test
    fun stopFinalizesOpenCandidateAsTempStop() = runTest {
        val store = FakeTrackingStore()
        val coordinator = coordinator(store)
        coordinator.restore(startedAt = 1_000)
        coordinator.onFix(fix(2_000, speedMps = 2.0), inVehicle = true)
        coordinator.onFix(fix(10_000, speedMps = 0.0), inVehicle = false)

        coordinator.stop()

        assertEquals(RecordType.TEMP_STOP, store.rows.last().recordType)
        assertTrue(store.rows.last().isFinalized)
    }

    @Test
    fun notifiesOnlyWhenARecordIsInserted() = runTest {
        val store = FakeTrackingStore()
        val persisted = mutableListOf<TrackingFix>()
        val coordinator = coordinator(store, persisted::add)
        coordinator.restore(startedAt = 1_000)

        coordinator.onFix(fix(2_000, speedMps = 2.0), inVehicle = true)
        coordinator.onFix(fix(12_000, speedMps = 2.0), inVehicle = false)

        assertEquals(listOf(2_000L), persisted.map { it.capturedAt })
    }

    private fun coordinator(
        store: FakeTrackingStore,
        onPersisted: (TrackingFix) -> Unit = {},
    ): TrackingCoordinator {
        val history = LocationHistoryRepository(store)
        return TrackingCoordinator(
            history = history,
            detector = MovementDetector(),
            persist = { fix, type, finalized ->
                history.capture(
                    location = CapturedLocation(
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        accuracy = fix.accuracy,
                        capturedAt = fix.capturedAt,
                        timezone = fix.timezone,
                    ),
                    batteryPercent = 80,
                    trackedMillis = 0,
                    deviceNumber = "001",
                    deviceId = "device",
                    recordType = type,
                    isFinalized = finalized,
                )
            },
            onPersisted = onPersisted,
        )
    }

    private fun fix(at: Long, speedMps: Double?) = TrackingFix(
        latitude = 10.0,
        longitude = 106.0,
        accuracy = 5.0,
        capturedAt = at,
        timezone = "Asia/Bangkok",
        speedMetersPerSecond = speedMps,
    )

    private fun record(at: Long, type: RecordType, finalized: Boolean) = LocationRecord(
        deviceNumber = "001",
        deviceId = "device",
        capturedAt = at,
        timezone = "Asia/Bangkok",
        latitude = 10.0,
        longitude = 106.0,
        accuracy = 5.0,
        batteryPercent = 80,
        trackedDurationMillis = 0,
        recordType = type,
        isFinalized = finalized,
    )
}

private class FakeTrackingStore : LocationRecordStore {
    val rows = mutableListOf<LocationRecord>()
    private var nextId = 1L

    override suspend fun insert(record: LocationRecord): Long = nextId++.also { id ->
        rows += record.copy(id = id)
    }

    override suspend fun get(id: Long): LocationRecord? = rows.find { it.id == id }
    override suspend fun unsent(limit: Int): List<LocationRecord> = rows
        .filter { it.state != DeliveryState.SENT && it.isFinalized }
        .take(limit)
    override suspend fun count(): Int = rows.size
    override fun observeBetween(from: Long, until: Long): Flow<List<LocationRecord>> =
        flowOf(rows.filter { it.capturedAt in from until until })
    override fun observeOldestCapturedAt(): Flow<Long?> = flowOf(rows.minOfOrNull { it.capturedAt })
    override suspend fun between(from: Long, until: Long): List<LocationRecord> =
        rows.filter { it.capturedAt in from until until }
    override suspend fun activeStopCandidate(since: Long): LocationRecord? = rows
        .filter { it.capturedAt >= since && it.recordType == RecordType.TEMP_STOP && !it.isFinalized }
        .maxByOrNull { it.capturedAt }
    override suspend fun latestSince(since: Long): LocationRecord? = rows
        .filter { it.capturedAt >= since }
        .maxByOrNull { it.capturedAt }
    override suspend fun deleteOlderThan(beforeMillis: Long) = Unit
    override suspend fun deleteBetween(from: Long, until: Long) = Unit
    override suspend fun deleteAll() = rows.clear()
    override suspend fun finalizeStopCandidate(id: Long, recordType: RecordType) {
        val index = rows.indexOfFirst { it.id == id }
        if (index >= 0 && !rows[index].isFinalized) {
            rows[index] = rows[index].copy(recordType = recordType, isFinalized = true)
        }
    }
    override suspend fun markRetrying(ids: List<Long>, error: String) = Unit
    override suspend fun markSent(ids: List<Long>, sentAt: Long) = Unit
}
