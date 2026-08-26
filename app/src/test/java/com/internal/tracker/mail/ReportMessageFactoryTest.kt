package com.internal.tracker.mail

import com.internal.tracker.config.PilotConfig
import com.internal.tracker.diagnostics.DiagnosticBundle
import com.internal.tracker.history.LocationRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportMessageFactoryTest {
    @Test
    fun messageIdentifiesDeviceAndIncludesLatestMapLink() {
        val message = ReportMessageFactory.create(
            config = config(),
            deviceId = "AND-1",
            routes = listOf(record()),
            diagnostics = DiagnosticBundle(emptyList(), emptyList()),
            telemetry = DeliveryTelemetry(1_500, 1_000, 2, "NETWORK"),
            appVersion = "1.0-test",
            scheduledFor = 100_000,
            nowMillis = 101_000,
        )

        assertTrue(message.subject.contains("Thiet bi 001"))
        assertTrue(message.body.contains("AND-1"))
        assertTrue(message.body.contains("https://maps.google.com/?q=10.5,20.25"))
        assertEquals(listOf(1L), message.recordIds)
        assertEquals(1, message.attachments.size)
        assertTrue(message.attachments.single().name.endsWith(".csv"))
        assertEquals("text/csv; charset=UTF-8", message.attachments.single().contentType)
        assertEquals(64, message.reportId!!.length)
        assertTrue(message.body.contains("Scheduled window:"))
        assertTrue(message.body.contains("Previous SMTP success:"))
        assertTrue(message.body.contains("Consecutive failures: 2"))
    }

    private fun config() = PilotConfig("001", "pic@example.com", 6, "sender@gmail.com", "abcdefghijklmnop")
    private fun record() = LocationRecord(
        id = 1,
        deviceNumber = "001",
        deviceId = "AND-1",
        capturedAt = 1_001,
        timezone = "Asia/Ho_Chi_Minh",
        latitude = 10.5,
        longitude = 20.25,
        accuracy = 4.5,
        batteryPercent = 82,
        trackedDurationMillis = 60_000,
    )
}
