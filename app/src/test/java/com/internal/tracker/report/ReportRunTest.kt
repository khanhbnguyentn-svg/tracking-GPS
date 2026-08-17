package com.internal.tracker.report

import com.internal.tracker.mail.DeliveryOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportRunTest {
    @Test
    fun cleanupRunsBeforeDeliveryAndSchedule() = runTest {
        val events = mutableListOf<String>()
        val run = ReportRun(
            cleanup = { events += "cleanup" },
            deliver = { scheduledFor -> events += "mail:$scheduledFor"; DeliveryOutcome(3, 0, 0, 0, null) },
            scheduleNext = { events += "schedule" },
        )

        assertEquals(3, run.execute(100_000).sent)
        assertEquals(listOf("cleanup", "mail:100000", "schedule"), events)
    }

    @Test
    fun cleanupFailureDoesNotPreventDelivery() = runTest {
        val events = mutableListOf<String>()
        val run = ReportRun(
            cleanup = { events += "cleanup"; error("CLEANUP_FAILED") },
            deliver = { scheduledFor -> events += "mail:$scheduledFor"; DeliveryOutcome(2, 0, 0, 0, null) },
            scheduleNext = { events += "schedule" },
        )

        val result = run.execute(100_000)

        assertEquals(2, result.sent)
        assertEquals("CLEANUP_FAILED", result.error)
        assertEquals(listOf("cleanup", "mail:100000", "schedule"), events)
    }

    @Test
    fun deliveryErrorWinsOverCleanupErrorAndStillSchedules() = runTest {
        val events = mutableListOf<String>()
        val run = ReportRun(
            cleanup = { events += "cleanup"; error("CLEANUP_FAILED") },
            deliver = { scheduledFor -> events += "mail:$scheduledFor"; error("MAIL_FAILED") },
            scheduleNext = { events += "schedule" },
        )

        val result = run.execute(100_000)

        assertEquals(0, result.sent)
        assertEquals("MAIL_FAILED", result.error)
        assertEquals(listOf("cleanup", "mail:100000", "schedule"), events)
    }
}
