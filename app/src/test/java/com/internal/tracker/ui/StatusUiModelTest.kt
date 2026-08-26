package com.internal.tracker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StatusUiModelTest {
    @Test
    fun statusRowsExcludeDeviceId() {
        val model = StatusUiModel.create(
            tracking = true,
            deviceNumber = "001",
            lastLocationTime = 1_000,
            lastSendTime = 2_000,
            nextRunTime = 3_000,
        )

        assertEquals(
            listOf("Trạng thái", "Thiết bị", "GPS cuối", "Email cuối", "Kỳ gửi dự kiến"),
            model.rows.map { it.label },
        )
        assertFalse(model.rows.any { it.label == "Device ID" })
    }
}
