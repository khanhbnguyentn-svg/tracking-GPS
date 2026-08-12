package com.internal.tracker.mail

import com.internal.tracker.config.PilotConfig
import com.internal.tracker.export.LocationCsv
import com.internal.tracker.history.LocationRecord
import com.internal.tracker.history.LocationRecordStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DeliveryOutcome(val sent: Int, val remaining: Int, val publicError: String?)

class ReportDelivery(
    private val history: LocationRecordStore,
    private val config: () -> PilotConfig,
    private val appVersion: String,
    private val sender: MailSender,
    private val refreshBackup: suspend (List<LocationRecord>) -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    suspend fun deliverPending(): DeliveryOutcome = mutex.withLock {
        val pending = selectBatch(history.unsent(MAX_RECORDS))
        if (pending.isEmpty()) return@withLock DeliveryOutcome(0, 0, null)

        refreshBackup(pending)
        val message = ReportMessageFactory.create(config(), pending, appVersion, nowMillis())
        when (val result = sender.send(config(), message)) {
            MailResult.Accepted -> {
                history.markSent(message.recordIds, nowMillis())
                val sentRows = message.recordIds.mapNotNull { history.get(it) }
                refreshBackup(sentRows)
                DeliveryOutcome(sentRows.size, history.unsent(MAX_RECORDS).size, null)
            }
            else -> {
                val error = result.publicError()
                history.markRetrying(message.recordIds, error)
                DeliveryOutcome(0, history.unsent(MAX_RECORDS).size, error)
            }
        }
    }

    private fun selectBatch(records: List<LocationRecord>): List<LocationRecord> {
        val selected = mutableListOf<LocationRecord>()
        for (record in records) {
            // ponytail: quadratic sizing is fine for the 100-device pilot; stream CSV if per-device backlog grows large.
            val candidate = selected + record
            if (LocationCsv.encode(candidate).toByteArray(Charsets.UTF_8).size > MAX_ATTACHMENT_BYTES) break
            selected += record
        }
        return selected
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
        const val MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024
    }
}
