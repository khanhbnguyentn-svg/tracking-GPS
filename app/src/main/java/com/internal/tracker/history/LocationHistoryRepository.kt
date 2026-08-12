package com.internal.tracker.history

class LocationHistoryRepository(private val store: LocationRecordStore) {
    suspend fun capture(
        location: CapturedLocation,
        batteryPercent: Int?,
        trackedMillis: Long,
        deviceNumber: String,
        deviceId: String,
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
        ),
    )

    suspend fun get(id: Long) = store.get(id)
    suspend fun unsent(limit: Int) = store.unsent(limit)
    suspend fun count() = store.count()
    fun observeBetween(from: Long, until: Long) = store.observeBetween(from, until)
    suspend fun markRetrying(ids: List<Long>, error: String) = store.markRetrying(ids, error)
    suspend fun markSent(ids: List<Long>, sentAt: Long) = store.markSent(ids, sentAt)
}
