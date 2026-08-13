package com.internal.tracker.report

import com.internal.tracker.mail.DeliveryOutcome

data class ReportRunResult(val sent: Int, val error: String?)

class ReportRun(
    private val cleanup: suspend () -> Unit,
    private val deliver: suspend () -> DeliveryOutcome,
    private val scheduleNext: () -> Unit,
) {
    suspend fun execute(): ReportRunResult {
        val cleanupFailure = runCatching { cleanup() }.exceptionOrNull()
        return try {
            runCatching { deliver() }.fold(
                onSuccess = { delivery ->
                    ReportRunResult(
                        sent = delivery.sent,
                        error = delivery.publicError ?: cleanupFailure?.publicMessage(),
                    )
                },
                onFailure = { error -> ReportRunResult(0, error.publicMessage()) },
            )
        } finally {
            scheduleNext()
        }
    }

    private fun Throwable.publicMessage(): String = message ?: "UNKNOWN"
}
