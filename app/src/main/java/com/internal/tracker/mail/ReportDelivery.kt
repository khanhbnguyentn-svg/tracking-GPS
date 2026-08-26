package com.internal.tracker.mail

import com.internal.tracker.config.PilotConfig
import com.internal.tracker.diagnostics.DiagnosticBundle
import com.internal.tracker.diagnostics.DiagnosticCsv
import com.internal.tracker.diagnostics.DiagnosticReportStore
import com.internal.tracker.export.LocationCsv
import com.internal.tracker.history.LocationRecord
import com.internal.tracker.history.LocationRecordStore
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DeliveryOutcome(
    val sent: Int,
    val diagnosticsSent: Int,
    val remaining: Int,
    val diagnosticsRemaining: Int,
    val publicError: String?,
)

class ReportDelivery(
    private val history: LocationRecordStore,
    private val diagnostics: DiagnosticReportStore,
    private val config: () -> PilotConfig,
    private val deviceId: () -> String,
    private val telemetry: DeliveryTelemetryStore,
    private val appVersion: String,
    private val sender: MailSender,
    private val refreshBackup: suspend (List<LocationRecord>) -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    suspend fun deliverPending(scheduledFor: Long): DeliveryOutcome = mutex.withLock {
        val routes = selectRouteBatch(history.unsent(MAX_RECORDS))
        val diagnosticResult = runCatching { diagnostics.pendingBundle(MAX_INCIDENTS) }
        val pendingDiagnostics = diagnosticResult.getOrDefault(DiagnosticBundle(emptyList(), emptyList()))
        val diagnosticReadError = diagnosticResult.exceptionOrNull()?.let { "DIAGNOSTICS_READ" }
        val selectedResult = runCatching { selectDiagnosticBatch(routes, pendingDiagnostics) }
        val selectedDiagnostics = selectedResult.getOrDefault(DiagnosticBundle(emptyList(), emptyList()))
        val diagnosticError = diagnosticReadError
            ?: selectedResult.exceptionOrNull()?.let { "DIAGNOSTICS_EXPORT" }

        if (routes.isEmpty() && selectedDiagnostics.incidents.isEmpty()) {
            return@withLock DeliveryOutcome(
                sent = 0,
                diagnosticsSent = 0,
                remaining = 0,
                diagnosticsRemaining = pendingDiagnostics.incidents.size,
                publicError = diagnosticError,
            )
        }

        if (routes.isNotEmpty()) refreshBackup(routes)
        val attemptAt = nowMillis()
        val mailConfig = config()
        val message = ReportMessageFactory.create(
            config = mailConfig,
            deviceId = deviceId(),
            routes = routes,
            diagnostics = selectedDiagnostics,
            telemetry = telemetry.snapshot(),
            appVersion = appVersion,
            scheduledFor = scheduledFor,
            nowMillis = attemptAt,
        )
        when (val result = sender.send(mailConfig, message)) {
            MailResult.Accepted -> {
                history.markSent(message.recordIds, attemptAt)
                diagnostics.markReported(message.incidentIds, attemptAt)
                telemetry.accepted(attemptAt)
                val sentRows = message.recordIds.mapNotNull { history.get(it) }
                if (sentRows.isNotEmpty()) refreshBackup(sentRows)
                DeliveryOutcome(
                    sent = sentRows.size,
                    diagnosticsSent = message.incidentIds.size,
                    remaining = history.unsent(MAX_RECORDS).size,
                    diagnosticsRemaining = pendingDiagnostics.incidents.size - message.incidentIds.size,
                    publicError = diagnosticError,
                )
            }
            else -> {
                val error = result.publicError()
                if (message.recordIds.isNotEmpty()) history.markRetrying(message.recordIds, error)
                telemetry.failed(attemptAt, error)
                DeliveryOutcome(
                    sent = 0,
                    diagnosticsSent = 0,
                    remaining = history.unsent(MAX_RECORDS).size,
                    diagnosticsRemaining = pendingDiagnostics.incidents.size,
                    publicError = error,
                )
            }
        }
    }

    private fun selectRouteBatch(records: List<LocationRecord>): List<LocationRecord> {
        val selected = mutableListOf<LocationRecord>()
        for (record in records) {
            val candidate = selected + record
            if (LocationCsv.encode(candidate).toByteArray(Charsets.UTF_8).size > MAX_ATTACHMENT_BYTES) break
            selected += record
        }
        return selected
    }

    private fun selectDiagnosticBatch(routes: List<LocationRecord>, bundle: DiagnosticBundle): DiagnosticBundle {
        if (bundle.incidents.isEmpty()) return bundle
        val routeBytes = if (routes.isEmpty()) 0 else LocationCsv.encode(routes).toByteArray(Charsets.UTF_8).size
        val zone = routes.maxByOrNull(LocationRecord::capturedAt)?.timezone
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        val selectedIds = mutableListOf<String>()
        bundle.incidents.forEach { incident ->
            val candidateIds = selectedIds + incident.incidentId
            val candidate = DiagnosticBundle(
                incidents = bundle.incidents.filter { it.incidentId in candidateIds },
                samples = bundle.samples.filter { it.incidentId in candidateIds },
            )
            val bytes = routeBytes +
                DiagnosticCsv.summary(candidate, zone).toByteArray(Charsets.UTF_8).size +
                DiagnosticCsv.samples(candidate, zone).toByteArray(Charsets.UTF_8).size
            if (bytes <= MAX_ATTACHMENT_BYTES) selectedIds += incident.incidentId
        }
        return DiagnosticBundle(
            incidents = bundle.incidents.filter { it.incidentId in selectedIds },
            samples = bundle.samples.filter { it.incidentId in selectedIds },
        )
    }

    private fun MailResult.publicError() = when (this) {
        MailResult.AuthenticationRejected -> "AUTHENTICATION"
        MailResult.NetworkFailure -> "NETWORK"
        MailResult.TlsFailure -> "TLS"
        MailResult.RateLimited -> "RATE_LIMIT"
        MailResult.UnknownFailure -> "UNKNOWN"
        MailResult.Accepted -> error("Accepted has no public error")
    }

    private companion object {
        const val MAX_RECORDS = 10_000
        const val MAX_INCIDENTS = 1_000
        const val MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024
    }
}
