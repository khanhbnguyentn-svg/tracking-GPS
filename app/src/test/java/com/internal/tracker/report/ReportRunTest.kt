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
            deliver = { events += "mail"; DeliveryOutcome(3, 0, null) },
            scheduleNext = { events += "schedule" },
        )

        assertEquals(3, run.execute().sent)
        assertEquals(listOf("cleanup", "mail", "schedule"), events)
    }

    @Test
    fun cleanupFailureDoesNotPreventDelivery() = runTest {
        val events = mutableListOf<String>()
        val run = ReportRun(
            cleanup = { events += "cleanup"; error("CLEANUP_FAILED") },
            deliver = { events += "mail"; DeliveryOutcome(2, 0, null) },
            scheduleNext = { events += "schedule" },
        )

        val result = run.execute()

        assertEquals(2, result.sent)
        assertEquals("CLEANUP_FAILED", result.error)
        assertEquals(listOf("cleanup", "mail", "schedule"), events)
    }

    @Test
    fun deliveryErrorWinsOverCleanupErrorAndStillSchedules() = runTest {
        val events = mutableListOf<String>()
        val run = ReportRun(
            cleanup = { events += "cleanup"; error("CLEANUP_FAILED") },
            deliver = { events += "mail"; error("MAIL_FAILED") },
            scheduleNext = { events += "schedule" },
        )

        val result = run.execute()

        assertEquals(0, result.sent)
        assertEquals("MAIL_FAILED", result.error)
        assertEquals(listOf("cleanup", "mail", "schedule"), events)
    }
}
