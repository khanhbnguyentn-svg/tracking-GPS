package com.internal.tracker.ui

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class HistoryTimeRange(
    val from: Long,
    val until: Long,
)

data class HistoryFilter(
    val year: Int? = null,
    val month: Int? = null,
) {
    init {
        require(month == null || month in 1..12)
        require(month == null || year != null)
    }

    fun range(zone: ZoneId): HistoryTimeRange {
        if (year == null) return HistoryTimeRange(0L, Long.MAX_VALUE)
        val start = if (month == null) {
            LocalDate.of(year, 1, 1)
        } else {
            YearMonth.of(year, month).atDay(1)
        }
        val end = if (month == null) start.plusYears(1) else start.plusMonths(1)
        return HistoryTimeRange(
            from = start.atStartOfDay(zone).toInstant().toEpochMilli(),
            until = end.atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }

    val canDeleteFiltered: Boolean get() = year != null
}

fun normalizeMonthSelection(
    month: Int?,
    selectedYear: Int?,
    currentYear: Int,
): HistoryFilter = HistoryFilter(
    year = if (month != null && selectedYear == null) currentYear else selectedYear,
    month = month,
)

fun deleteConfirmationLabel(filter: HistoryFilter): String = when {
    filter.year == null -> error("A filtered delete requires a selected year")
    filter.month != null -> "Xóa dữ liệu tháng %02d/%d?".format(filter.month, filter.year)
    else -> "Xóa toàn bộ dữ liệu năm ${filter.year}?"
}
