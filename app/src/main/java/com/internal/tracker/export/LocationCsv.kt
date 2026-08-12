package com.internal.tracker.export

import com.internal.tracker.history.LocationRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object LocationCsv {
    private const val HEADER = "record_number,device_number,device_id,captured_at,timezone,latitude,longitude,accuracy_m,battery_percent,tracked_duration,delivery_state"

    fun encode(records: List<LocationRecord>): String = buildString {
        append(HEADER).append("\r\n")
        records.forEach { record ->
            append(
                listOf(
                    record.id,
                    record.deviceNumber,
                    record.deviceId,
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                        Instant.ofEpochMilli(record.capturedAt).atZone(ZoneId.of(record.timezone)),
                    ),
                    record.timezone,
                    record.latitude,
                    record.longitude,
                    record.accuracy ?: "",
                    record.batteryPercent ?: "",
                    formatDuration(record.trackedDurationMillis),
                    record.state.name.lowercase(),
                ).joinToString(",") { escape(it.toString()) },
            )
            append("\r\n")
        }
    }

    private fun formatDuration(milliseconds: Long): String {
        val totalMinutes = milliseconds.coerceAtLeast(0) / 60_000
        val days = totalMinutes / (24 * 60)
        val hours = totalMinutes / 60 % 24
        val minutes = totalMinutes % 60
        return "${days}d ${hours}h ${minutes}m"
    }

    private fun escape(value: String): String = if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }
}
