package com.internal.tracker.schedule

import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportScheduleTest {
    @Test
    fun sixHourScheduleReturnsNextCalendarAnchor() {
        val now = ZonedDateTime.parse("2026-08-12T01:00:00+07:00[Asia/Ho_Chi_Minh]")

        assertEquals(
            ZonedDateTime.parse("2026-08-12T06:00:00+07:00[Asia/Ho_Chi_Minh]"),
            ReportSchedule.nextRun(now, 6, 1),
        )
    }

    @Test
    fun deviceOneAndOneHundredSpanApprovedWindow() {
        val now = ZonedDateTime.parse("2026-08-12T23:00:00+07:00[Asia/Ho_Chi_Minh]")

        assertEquals(LocalTime.of(0, 0), ReportSchedule.nextRun(now, 24, 1).toLocalTime())
        assertEquals(LocalTime.of(0, 59), ReportSchedule.nextRun(now, 24, 100).toLocalTime())
    }

    @Test
    fun adjacentDeviceNumbersHaveDistinctOffsets() {
        val now = ZonedDateTime.parse("2026-08-12T23:00:00+07:00[Asia/Ho_Chi_Minh]")

        val first = ReportSchedule.nextRun(now, 24, 1)
        val second = ReportSchedule.nextRun(now, 24, 2)

        assertEquals(35, second.toEpochSecond() - first.toEpochSecond())
    }

    @Test
    fun delayedRunDoesNotDriftFollowingAnchor() {
        val delayed = ZonedDateTime.parse("2026-08-12T06:40:00+07:00[Asia/Ho_Chi_Minh]")

        assertEquals(12, ReportSchedule.nextRun(delayed, 6, 1).hour)
    }

    @Test
    fun disabledSchedulerCancelsInsteadOfEnqueuing() {
        var scheduled: ZonedDateTime? = null
        var cancellations = 0
        val scheduler = ReportScheduler(
            now = { ZonedDateTime.parse("2026-08-12T01:00:00+07:00[Asia/Ho_Chi_Minh]") },
            enqueue = { scheduled = it },
            cancel = { cancellations++ },
        )

        scheduler.reconcile(enabled = false, intervalHours = 6, deviceNumber = 1)

        assertNull(scheduled)
        assertEquals(1, cancellations)
    }
}
