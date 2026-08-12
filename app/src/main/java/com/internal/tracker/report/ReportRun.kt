package com.internal.tracker.report

import com.internal.tracker.history.CapturedLocation
import com.internal.tracker.mail.DeliveryOutcome

data class ReportRunResult(val recordId: Long?, val sent: Int, val error: String?)

class ReportRun(
    private val capture: suspend () -> CapturedLocation,
    private val persist: suspend (CapturedLocation, Int?) -> Long,
    private val backup: suspend (Long) -> Unit,
    private val deliver: suspend () -> DeliveryOutcome,
    private val batteryPercent: () -> Int?,
    private val scheduleNext: () -> Unit,
) {
    suspend fun execute(): ReportRunResult {
        return try {
            val location = capture()
            val id = persist(location, batteryPercent())
            backup(id)
            val delivery = deliver()
            ReportRunResult(id, delivery.sent, delivery.publicError)
        } catch (error: Exception) {
            ReportRunResult(null, 0, error.message ?: "UNKNOWN")
        } finally {
            scheduleNext()
        }
    }
}
