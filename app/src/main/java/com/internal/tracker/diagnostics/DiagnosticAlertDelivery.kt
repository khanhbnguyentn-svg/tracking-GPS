package com.internal.tracker.diagnostics

import com.internal.tracker.config.PilotConfig
import com.internal.tracker.mail.MailAttachment
import com.internal.tracker.mail.MailResult
import com.internal.tracker.mail.MailSender
import com.internal.tracker.mail.ReportMessage
import java.time.ZoneId

data class DiagnosticAlertOutcome(val sent: Boolean, val publicError: String?)

class DiagnosticAlertDelivery(
    private val repository: DiagnosticRepository,
    private val config: () -> PilotConfig,
    private val sender: MailSender,
    private val appVersion: String,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    suspend fun deliver(incidentId: String, phase: DiagnosticAlertPhase): DiagnosticAlertOutcome {
        val bundle = repository.bundle(incidentId)
        val incident = bundle.incidents.singleOrNull() ?: return DiagnosticAlertOutcome(false, "NOT_FOUND")
        val state = when (phase) {
            DiagnosticAlertPhase.OPENED -> incident.openedAlertState
            DiagnosticAlertPhase.RECOVERED -> incident.recoveredAlertState
        }
        if (state != DiagnosticDeliveryState.PENDING) {
            val error = when (phase) {
                DiagnosticAlertPhase.OPENED -> incident.openedAlertError
                DiagnosticAlertPhase.RECOVERED -> incident.recoveredAlertError
            }
            return DiagnosticAlertOutcome(state == DiagnosticDeliveryState.ACCEPTED, error)
        }

        val mailConfig = config()
        val phaseName = if (phase == DiagnosticAlertPhase.OPENED) "GPS_GAP_OPENED" else "GPS_GAP_RECOVERED"
        val duration = incident.recoveredAt?.let { "\nDuration: ${it - incident.openedAt} ms" }.orEmpty()
        val message = ReportMessage(
            subject = "[GPS][Device ${mailConfig.deviceNumber}] $phaseName ${incident.incidentId}",
            body = """
                Incident ID: ${incident.incidentId}
                Phase: $phaseName
                Reason: ${incident.reasonCodes}
                Device condition: ${incident.deviceCondition}
                App version: $appVersion$duration
            """.trimIndent(),
            attachments = listOf(
                MailAttachment(
                    "diagnostic-summary-${incident.incidentId}.csv",
                    "text/csv; charset=UTF-8",
                    DiagnosticCsv.summary(bundle, zone()).toByteArray(Charsets.UTF_8),
                ),
                MailAttachment(
                    "diagnostic-samples-${incident.incidentId}.csv",
                    "text/csv; charset=UTF-8",
                    DiagnosticCsv.samples(bundle, zone()).toByteArray(Charsets.UTF_8),
                ),
            ),
            incidentIds = listOf(incident.incidentId),
        )
        val result = sender.send(mailConfig, message)
        val acceptedAt = nowMillis().takeIf { result == MailResult.Accepted }
        val error = result.errorCategory()
        when (phase) {
            DiagnosticAlertPhase.OPENED -> repository.markOpenedResult(incidentId, acceptedAt, error)
            DiagnosticAlertPhase.RECOVERED -> repository.markRecoveredResult(incidentId, acceptedAt, error)
        }
        return DiagnosticAlertOutcome(result == MailResult.Accepted, error)
    }

    private fun MailResult.errorCategory(): String? = when (this) {
        MailResult.Accepted -> null
        MailResult.AuthenticationRejected -> "AUTHENTICATION"
        MailResult.NetworkFailure -> "NETWORK"
        MailResult.TlsFailure -> "TLS"
        MailResult.RateLimited -> "RATE_LIMIT"
        MailResult.UnknownFailure -> "UNKNOWN"
    }
}
