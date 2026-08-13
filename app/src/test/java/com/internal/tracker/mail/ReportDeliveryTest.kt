package com.internal.tracker.mail

import com.internal.tracker.config.PilotConfig
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

        delivery.deliverPending()

        assertEquals(1, sender.messages.size)
        assertEquals(listOf(1L, 2L), sender.messages.single().recordIds)
        assertEquals(DeliveryState.SENT, store.get(1)!!.state)
        assertEquals(DeliveryState.SENT, store.get(2)!!.state)
    }

    @Test
    fun rejectedAuthenticationRetainsBacklogAndRedactsSecret() = runTest {
        val store = FakeMailHistoryStore(listOf(record(1)))
        val delivery = delivery(store, FakeMailSender(MailResult.AuthenticationRejected))

        val result = delivery.deliverPending()

        assertEquals("AUTHENTICATION", result.publicError)
        assertEquals(DeliveryState.RETRYING, store.get(1)!!.state)
        assertEquals(1, store.unsent(10).size)
    }

    private fun delivery(store: LocationRecordStore, sender: FakeMailSender) = ReportDelivery(
        history = store,
        config = { PilotConfig("001", "pic@example.com", 6, "sender@gmail.com", "abcdefghijklmnop") },
        appVersion = "1.0-test",
        sender = sender,
        refreshBackup = {},
        nowMillis = { 2_000 },
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
