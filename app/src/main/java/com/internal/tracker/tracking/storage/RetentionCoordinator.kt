package com.internal.tracker.tracking.storage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.room.withTransaction
import com.internal.tracker.tracking.database.TrackingDatabase

class RetentionCoordinator(private val protectedDays: ProtectedRawDayResolver, private val database: TrackingDatabase? = null) {
    fun eligibleDays(rawUtcMillis: Iterable<Long>, today: LocalDate): List<LocalDate> {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        return rawUtcMillis.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .distinct().filter { it != today && it !in protectedDays.protectedDays() }.sorted()
    }

    suspend fun deleteWholeBusinessDay(day: LocalDate): Int {
        require(day !in protectedDays.protectedDays())
        val db = checkNotNull(database) { "A database is required for cleanup" }
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return db.withTransaction {
            val dao = db.trackingDao()
            val derived = dao.deleteMovementEventsInRange(from, to)
            dao.deleteRawSamplesInRange(from, to)
            derived
        }
    }
}
