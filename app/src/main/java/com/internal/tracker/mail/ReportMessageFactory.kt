package com.internal.tracker.mail

import com.internal.tracker.config.PilotConfig
import com.internal.tracker.export.LocationCsv
import com.internal.tracker.history.LocationRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class MailAttachment(
    val name: String,
    val contentType: String,
    val bytes: ByteArray,
)

data class ReportMessage(
    val subject: String,
    val body: String,
    val attachments: List<MailAttachment>,
    val recordIds: List<Long> = emptyList(),
    val incidentIds: List<String> = emptyList(),
    val reportId: String? = null,
)

object ReportMessageFactory {
    fun create(config: PilotConfig, records: List<LocationRecord>, appVersion: String, nowMillis: Long): ReportMessage {
        require(records.isNotEmpty())
        val latest = records.maxBy(LocationRecord::capturedAt)
        val zone = runCatching { ZoneId.of(latest.timezone) }.getOrDefault(ZoneId.systemDefault())
        val sentAt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochMilli(nowMillis).atZone(zone))
        val backlog = records.count { it.attemptCount > 0 }
        return ReportMessage(
            subject = "[GPS][Thiet bi ${config.deviceNumber}] Bao cao $sentAt",
            body = """
                Thiet bi: ${config.deviceNumber}
                Device ID: ${latest.deviceId}
                Thoi diem gui: $sentAt
                So ban ghi: ${records.size}
                Ban ghi ton: $backlog
                Vi tri moi nhat: https://maps.google.com/?q=${latest.latitude},${latest.longitude}
                Phien ban ung dung: $appVersion
            """.trimIndent(),
            attachments = listOf(MailAttachment(
                name = "GPS-${config.deviceNumber}-$sentAt.csv".replace(':', '-').replace(' ', '_'),
                contentType = "text/csv; charset=UTF-8",
                bytes = LocationCsv.encode(records).toByteArray(Charsets.UTF_8),
            )),
            recordIds = records.map(LocationRecord::id),
        )
    }
}
