package com.internal.tracker.mail

import com.internal.tracker.config.PilotConfig
import com.internal.tracker.history.LocationRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportMessageFactoryTest {
    @Test
    fun messageIdentifiesDeviceAndIncludesLatestMapLink() {
        val message = ReportMessageFactory.create(config(), listOf(record()), "1.0-test", 2_000)

        assertTrue(message.subject.contains("Thiet bi 001"))
        assertTrue(message.body.contains("AND-1"))
        assertTrue(message.body.contains("https://maps.google.com/?q=10.5,20.25"))
        assertEquals(listOf(1L), message.recordIds)
        assertEquals(1, message.attachments.size)
        assertTrue(message.attachments.single().name.endsWith(".csv"))
        assertEquals("text/csv; charset=UTF-8", message.attachments.single().contentType)
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
