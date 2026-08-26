package com.internal.tracker.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReportIdTest {
    @Test fun equivalentSortedInputsProduceTheSameId() {
        val first = ReportId.create("device-a", 100_000, listOf(2, 1), listOf("b", "a"))
        val second = ReportId.create("device-a", 100_000, listOf(1, 2), listOf("a", "b"))

        assertEquals(first, second)
        assertEquals(64, first.length)
    }

    @Test fun scheduleOrContentChangesProduceAnotherId() {
        val base = ReportId.create("device-a", 100_000, listOf(1), listOf("a"))

        assertNotEquals(base, ReportId.create("device-a", 100_001, listOf(1), listOf("a")))
        assertNotEquals(base, ReportId.create("device-a", 100_000, listOf(2), listOf("a")))
        assertNotEquals(base, ReportId.create("device-a", 100_000, listOf(1), listOf("b")))
    }
}
