package com.internal.tracker.report

import com.internal.tracker.history.CapturedLocation
import com.internal.tracker.mail.DeliveryOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportRunTest {
    @Test
    fun captureIsPersistedAndBackedUpBeforeMail() = runTest {
        val events = mutableListOf<String>()
        val run = ReportRun(
            capture = { events += "capture"; location() },
            persist = { _, _ -> events += "room"; 7L },
            backup = { events += "csv" },
            deliver = { events += "mail"; events += "sent"; events += "csv"; DeliveryOutcome(1, 0, null) },
            batteryPercent = { 82 },
            scheduleNext = { events += "schedule" },
        )

        val result = run.execute()

        assertEquals(listOf("capture", "room", "csv", "mail", "sent", "csv", "schedule"), events)
        assertEquals(7L, result.recordId)
    }

    @Test
    fun offlineRunStillCapturesAndSchedulesNextAnchor() = runTest {
        var scheduled = false
        val run = ReportRun(
            capture = ::location,
            persist = { _, _ -> 7L },
            backup = {},
            deliver = { DeliveryOutcome(0, 1, "NETWORK") },
            batteryPercent = { 50 },
            scheduleNext = { scheduled = true },
        )

        val result = run.execute()

        assertEquals("NETWORK", result.error)
        assertTrue(scheduled)
    }

    @Test
    fun captureFailureStillSchedulesNextAnchor() = runTest {
        var scheduled = false
        val run = ReportRun(
            capture = { error("LOCATION_TIMEOUT") },
            persist = { _, _ -> error("unused") },
            backup = {},
            deliver = { error("unused") },
            batteryPercent = { 50 },
            scheduleNext = { scheduled = true },
        )

        val result = run.execute()

        assertEquals("LOCATION_TIMEOUT", result.error)
        assertTrue(scheduled)
    }

    private fun location() = CapturedLocation(10.0, 20.0, 4.5, 1_000, "Asia/Ho_Chi_Minh")
}
