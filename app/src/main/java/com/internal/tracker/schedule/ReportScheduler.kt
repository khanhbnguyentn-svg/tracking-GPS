package com.internal.tracker.schedule

import java.time.ZonedDateTime

class ReportScheduler(
    private val now: () -> ZonedDateTime,
    private val enqueue: (ZonedDateTime) -> Unit,
    private val cancel: () -> Unit,
) {
    fun reconcile(enabled: Boolean, intervalHours: Int, deviceNumber: Int) {
        if (!enabled) {
            cancel()
            return
        }
        enqueue(ReportSchedule.nextRun(now(), intervalHours, deviceNumber))
    }
}
