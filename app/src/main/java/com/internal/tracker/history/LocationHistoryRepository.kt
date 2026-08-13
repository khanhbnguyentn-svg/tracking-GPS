package com.internal.tracker.history

import java.time.LocalDate
import java.time.ZoneId

class LocationHistoryRepository(
    private val store: LocationRecordStore,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
) {
    suspend fun capture(
        location: CapturedLocation,
        batteryPercent: Int?,
        trackedMillis: Long,
        deviceNumber: String,
        deviceId: String,
        recordType: RecordType = RecordType.PERIODIC,
        isFinalized: Boolean = true,
    ): Long = store.insert(
        LocationRecord(
            deviceNumber = deviceNumber,
            deviceId = deviceId,
            capturedAt = location.capturedAt,
            timezone = location.timezone,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            batteryPercent = batteryPercent,
            trackedDurationMillis = trackedMillis,
            recordType = recordType,
            isFinalized = isFinalized,
        ),
    )

    suspend fun get(id: Long) = store.get(id)
    suspend fun unsent(limit: Int) = store.unsent(limit)
    suspend fun count() = store.count()
    fun observeBetween(from: Long, until: Long) = store.observeBetween(from, until)
    fun observeOldestCapturedAt() = store.observeOldestCapturedAt()
    suspend fun between(from: Long, until: Long) = store.between(from, until)
    suspend fun activeStopCandidate(since: Long) = store.activeStopCandidate(since)
    suspend fun latestSince(since: Long) = store.latestSince(since)
    suspend fun deleteOlderThan(date: LocalDate) = store.deleteOlderThan(
        date.atStartOfDay(zoneId()).toInstant().toEpochMilli(),
    )
    suspend fun deleteBetween(from: Long, until: Long) = store.deleteBetween(from, until)
    suspend fun deleteAll() = store.deleteAll()
    suspend fun finalizeStopCandidate(id: Long, recordType: RecordType) =
        store.finalizeStopCandidate(id, recordType)
    suspend fun markRetrying(ids: List<Long>, error: String) = store.markRetrying(ids, error)
    suspend fun markSent(ids: List<Long>, sentAt: Long) = store.markSent(ids, sentAt)
}
