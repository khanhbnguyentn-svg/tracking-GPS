package com.internal.tracker.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationHistoryRepositoryTest {
    @Test
    fun sentRowsRemainInHistory() = runTest {
        val store = FakeLocationRecordStore()
        val repository = LocationHistoryRepository(store)
        val id = repository.capture(sample(), 82, 10_000, "001", "AND-1")

        repository.markSent(listOf(id), 20_000)

        assertEquals(DeliveryState.SENT, repository.get(id)!!.state)
        assertEquals(1, repository.count())
    }

    @Test
    fun retryKeepsRecordEligible() = runTest {
        val repository = LocationHistoryRepository(FakeLocationRecordStore())
        val id = repository.capture(sample(), 82, 10_000, "001", "AND-1")

        repository.markRetrying(listOf(id), "NETWORK")

        assertEquals(listOf(id), repository.unsent(100).map { it.id })
        assertEquals(DeliveryState.RETRYING, repository.get(id)!!.state)
    }

    private fun sample() = CapturedLocation(10.0, 20.0, 4.5, 1_000, "Asia/Ho_Chi_Minh")
}

private class FakeLocationRecordStore : LocationRecordStore {
    private val rows = linkedMapOf<Long, LocationRecord>()
    private var nextId = 1L

    override suspend fun insert(record: LocationRecord): Long = nextId++.also { rows[it] = record.copy(id = it) }
    override suspend fun get(id: Long): LocationRecord? = rows[id]
    override suspend fun unsent(limit: Int): List<LocationRecord> = rows.values.filter { it.state != DeliveryState.SENT }.take(limit)
    override suspend fun count(): Int = rows.size
    override fun observeBetween(from: Long, until: Long): Flow<List<LocationRecord>> = flowOf(rows.values.filter { it.capturedAt in from until until })

    override suspend fun markRetrying(ids: List<Long>, error: String) {
        ids.forEach { id -> rows[id]?.let { rows[id] = it.copy(state = DeliveryState.RETRYING, attemptCount = it.attemptCount + 1, lastError = error) } }
    }

    override suspend fun markSent(ids: List<Long>, sentAt: Long) {
        ids.forEach { id -> rows[id]?.let { rows[id] = it.copy(state = DeliveryState.SENT, sentAt = sentAt, lastError = null) } }
    }
}
