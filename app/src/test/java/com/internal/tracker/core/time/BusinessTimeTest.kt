package com.internal.tracker.core.time

import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class BusinessTimeTest {
    @Test
    fun `business zone is Ho Chi Minh`() {
        assertEquals("Asia/Ho_Chi_Minh", BusinessTime.ZONE.id)
    }

    @Test
    fun `expense ending late day ten locks at start of day twelve`() {
        val end = ZonedDateTime.of(2026, 8, 10, 23, 30, 0, 0, BusinessTime.ZONE).toInstant()
        val expected = ZonedDateTime.of(2026, 8, 12, 0, 0, 0, 0, BusinessTime.ZONE).toInstant()

        assertEquals(expected, BusinessTime.expenseLockAt(end))
    }

    @Test
    fun `expense ending after midnight locks two dates later`() {
        val end = ZonedDateTime.of(2026, 8, 11, 0, 30, 0, 0, BusinessTime.ZONE).toInstant()
        val expected = ZonedDateTime.of(2026, 8, 13, 0, 0, 0, 0, BusinessTime.ZONE).toInstant()

        assertEquals(expected, BusinessTime.expenseLockAt(end))
    }
}
