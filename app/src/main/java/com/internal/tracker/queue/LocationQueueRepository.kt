package com.internal.tracker.queue

import kotlinx.coroutines.flow.Flow

interface QueueStore {
    suspend fun enqueue(sample: LocationSample): Long
    suspend fun oldest(limit: Int): List<PendingLocation>
    suspend fun markSent(id: Long)
    suspend fun incrementRetry(id: Long)
    fun count(): Flow<Int>
}

class LocationQueueRepository(private val dao: PendingLocationDao) : QueueStore {
    override suspend fun enqueue(sample: LocationSample): Long {
        val id = dao.insert(
            PendingLocation(
                latitude = sample.latitude,
                longitude = sample.longitude,
                timestamp = sample.timestamp,
                speed = sample.speed,
                accuracy = sample.accuracy,
            ),
        )
        val excess = dao.countNow() - MAX_PENDING
        if (excess > 0) dao.deleteOldest(excess)
        return id
    }

    override suspend fun oldest(limit: Int): List<PendingLocation> = dao.oldest(limit)
    override suspend fun markSent(id: Long) = dao.delete(id)
    override suspend fun incrementRetry(id: Long) = dao.incrementRetry(id)
    override fun count(): Flow<Int> = dao.observeCount()

    companion object {
        const val MAX_PENDING = 10_000
    }
}
