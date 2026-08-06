package com.internal.tracker.worker

import com.internal.tracker.config.Scheme
import com.internal.tracker.config.TlsMode
import com.internal.tracker.network.LocationSender
import com.internal.tracker.network.SendResult
import com.internal.tracker.profile.Profile
import com.internal.tracker.queue.PendingLocation
import com.internal.tracker.queue.QueueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueUploaderTest {
    @Test
    fun drainsFifoAndDeletesSuccessfulRows() = runTest {
        val store = FakeQueueStore(3)
        val uploader = QueueUploader(store, { profile() }, { "AND-0123456789abcdef" }, LocationSender { _, _, _ -> SendResult.Success })

        val summary = uploader.drain()

        assertEquals(UploadSummary(3, 0), summary)
        assertEquals(listOf(1L, 2L, 3L), store.deleted)
    }

    @Test
    fun stopsAndIncrementsRetryOnFirstFailure() = runTest {
        val store = FakeQueueStore(3)
        val uploader = QueueUploader(store, { profile() }, { "AND-0123456789abcdef" }, LocationSender { _, _, location ->
            if (location.id == 2L) SendResult.Timeout else SendResult.Success
        })

        val summary = uploader.drain()

        assertEquals(UploadSummary(1, 1), summary)
        assertEquals(listOf(1L), store.deleted)
        assertEquals(listOf(2L), store.retried)
    }

    @Test
    fun limitsBatchToOneHundred() = runTest {
        val store = FakeQueueStore(150)
        val uploader = QueueUploader(store, { profile() }, { "AND-0123456789abcdef" }, LocationSender { _, _, _ -> SendResult.Success })

        assertEquals(100, uploader.drain().sent)
    }

    private fun profile() = Profile(1, "P", "example.com", 443, Scheme.HTTPS, 60, TlsMode.SYSTEM, null, null, true)
}

private class FakeQueueStore(size: Int) : QueueStore {
    private val rows = (1..size).map { PendingLocation(it.toLong(), 1.0, 2.0, it.toLong(), 0.0, 3.0) }
    val deleted = mutableListOf<Long>()
    val retried = mutableListOf<Long>()
    override suspend fun enqueue(sample: com.internal.tracker.queue.LocationSample): Long = error("unused")
    override suspend fun oldest(limit: Int): List<PendingLocation> = rows.take(limit)
    override suspend fun markSent(id: Long) { deleted += id }
    override suspend fun incrementRetry(id: Long) { retried += id }
    override fun count(): Flow<Int> = flowOf(rows.size)
}
