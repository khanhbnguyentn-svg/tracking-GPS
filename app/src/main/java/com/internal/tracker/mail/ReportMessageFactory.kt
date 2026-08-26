package com.internal.tracker.mail

import com.internal.tracker.config.PilotConfig
import com.internal.tracker.diagnostics.DiagnosticBundle
import com.internal.tracker.diagnostics.DiagnosticCsv
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
    fun create(
        config: PilotConfig,
        deviceId: String,
        routes: List<LocationRecord>,
        diagnostics: DiagnosticBundle,
        telemetry: DeliveryTelemetry,
        appVersion: String,
        scheduledFor: Long,
        nowMillis: Long,
    ): ReportMessage {
        require(routes.isNotEmpty() || diagnostics.incidents.isNotEmpty())
        val latest = routes.maxByOrNull(LocationRecord::capturedAt)
        val zone = latest?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        val sentAt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochMilli(nowMillis).atZone(zone))
        val windowStart = scheduledFor - config.intervalHours * 60L * 60L * 1_000L
        val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val reportId = ReportId.create(
            deviceId,
            scheduledFor,
            routes.map(LocationRecord::id),
            diagnostics.incidents.map { it.incidentId },
        )
        val attachments = buildList {
            if (routes.isNotEmpty()) add(MailAttachment(
                name = "GPS-${config.deviceNumber}-$sentAt.csv".replace(':', '-').replace(' ', '_'),
                contentType = "text/csv; charset=UTF-8",
                bytes = LocationCsv.encode(routes).toByteArray(Charsets.UTF_8),
            ))
            if (diagnostics.incidents.isNotEmpty()) {
                add(MailAttachment(
                    name = "diagnostic-summary-${config.deviceNumber}-$scheduledFor.csv",
                    contentType = "text/csv; charset=UTF-8",
                    bytes = DiagnosticCsv.summary(diagnostics, zone).toByteArray(Charsets.UTF_8),
                ))
                add(MailAttachment(
                    name = "diagnostic-samples-${config.deviceNumber}-$scheduledFor.csv",
                    contentType = "text/csv; charset=UTF-8",
                    bytes = DiagnosticCsv.samples(diagnostics, zone).toByteArray(Charsets.UTF_8),
                ))
            }
        }
        return ReportMessage(
            subject = "[GPS][Thiet bi ${config.deviceNumber}] Bao cao $sentAt",
            body = """
                Thiet bi: ${config.deviceNumber}
                Device ID: $deviceId
                Report ID: $reportId
                Thoi diem gui: $sentAt
                Scheduled window: ${formatter.format(Instant.ofEpochMilli(windowStart).atZone(zone))} - ${formatter.format(Instant.ofEpochMilli(scheduledFor).atZone(zone))}
                So ban ghi: ${routes.size}
                Diagnostic incidents: ${diagnostics.incidents.size}
                Ban ghi ton: ${routes.count { it.attemptCount > 0 }}
                Previous SMTP success: ${telemetry.lastSuccessAt.takeIf { it > 0 }?.let { formatter.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "NONE"}
                Consecutive failures: ${telemetry.consecutiveFailures}
                Vi tri moi nhat: ${latest?.let { "https://maps.google.com/?q=${it.latitude},${it.longitude}" } ?: "NONE"}
                Phien ban ung dung: $appVersion
            """.trimIndent(),
            attachments = attachments,
            recordIds = routes.map(LocationRecord::id),
            incidentIds = diagnostics.incidents.map { it.incidentId },
            reportId = reportId,
        )
    }
}
