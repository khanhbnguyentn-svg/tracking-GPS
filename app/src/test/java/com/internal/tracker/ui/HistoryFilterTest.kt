package com.internal.tracker.ui

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryFilterTest {
    private val zone = ZoneId.of("Asia/Bangkok")

    @Test fun allRangeCoversEveryStoredTimestamp() {
        assertEquals(HistoryTimeRange(0, Long.MAX_VALUE), HistoryFilter().range(zone))
    }

    @Test fun yearRangeUsesDeviceZoneAndHalfOpenBoundary() {
        assertEquals(
            HistoryTimeRange(epoch(2026, 1, 1), epoch(2027, 1, 1)),
            HistoryFilter(year = 2026).range(zone),
        )
    }

    @Test fun decemberRangeRollsIntoNextYear() {
        assertEquals(
            HistoryTimeRange(epoch(2026, 12, 1), epoch(2027, 1, 1)),
            HistoryFilter(year = 2026, month = 12).range(zone),
        )
    }

    @Test fun selectingMonthWithoutYearSelectsCurrentYear() {
        assertEquals(
            HistoryFilter(year = 2026, month = 8),
            normalizeMonthSelection(month = 8, selectedYear = null, currentYear = 2026),
        )
    }

    @Test fun filteredDeleteRequiresASelectedYear() {
        assertFalse(HistoryFilter().canDeleteFiltered)
        assertTrue(HistoryFilter(year = 2026).canDeleteFiltered)
    }

    @Test fun deleteLabelsDescribeExactScope() {
        assertEquals(
            "Xóa dữ liệu tháng 08/2026?",
            deleteConfirmationLabel(HistoryFilter(2026, 8)),
        )
        assertEquals(
            "Xóa toàn bộ dữ liệu năm 2026?",
            deleteConfirmationLabel(HistoryFilter(2026)),
        )
    }

    private fun epoch(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(zone).toInstant().toEpochMilli()
}
