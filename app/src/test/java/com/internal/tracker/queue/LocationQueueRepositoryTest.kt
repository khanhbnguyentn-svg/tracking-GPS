package com.internal.tracker.queue

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocationQueueRepositoryTest {
    @Test
    fun queueIsFifoAndDeletesOnlyAfterSuccess() = runTest {
        val dao = FakePendingLocationDao()
        val repository = LocationQueueRepository(dao)
        val first = repository.enqueue(sample(100))
        repository.enqueue(sample(200))

        assertEquals(listOf(100L, 200L), repository.oldest(10).map { it.timestamp })
        assertNotNull(dao.rows[first])
        repository.markSent(first)
        assertNull(dao.rows[first])
    }

    @Test
    fun failureIncrementsRetryCount() = runTest {
        val dao = FakePendingLocationDao()
        val repository = LocationQueueRepository(dao)
        val id = repository.enqueue(sample(100))

        repository.incrementRetry(id)

        assertEquals(1, dao.rows.getValue(id).retryCount)
    }

    @Test
    fun queueEvictsOldestBeyondTenThousand() = runTest {
        val dao = FakePendingLocationDao()
        val repository = LocationQueueRepository(dao)
        repeat(10_001) { repository.enqueue(sample(it.toLong())) }

        assertEquals(10_000, dao.rows.size)
        assertEquals(1L, repository.oldest(1).single().timestamp)
    }

    private fun sample(timestamp: Long) = LocationSample(10.0, 20.0, timestamp, 1.5, 4.0)
}

private class FakePendingLocationDao : PendingLocationDao {
    val rows = linkedMapOf<Long, PendingLocation>()
    private val count = MutableStateFlow(0)
    private var nextId = 1L
    override suspend fun insert(location: PendingLocation): Long = nextId++.also { rows[it] = location.copy(id = it); count.value = rows.size }
    override suspend fun oldest(limit: Int): List<PendingLocation> = rows.values.sortedBy { it.timestamp }.take(limit)
    override suspend fun delete(id: Long) { rows.remove(id); count.value = rows.size }
    override suspend fun incrementRetry(id: Long) { rows[id]?.let { rows[id] = it.copy(retryCount = it.retryCount + 1) } }
    override suspend fun countNow(): Int = rows.size
    override fun observeCount(): Flow<Int> = count
    override suspend fun deleteOldest(amount: Int) { rows.values.sortedBy { it.timestamp }.take(amount).forEach { rows.remove(it.id) }; count.value = rows.size }
}
