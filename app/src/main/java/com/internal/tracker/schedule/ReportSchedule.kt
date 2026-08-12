package com.internal.tracker.schedule

import java.time.ZonedDateTime

object ReportSchedule {
    fun nextRun(now: ZonedDateTime, intervalHours: Int, deviceNumber: Int): ZonedDateTime {
        require(intervalHours in setOf(6, 12, 24))
        require(deviceNumber in 1..100)

        val offsetSeconds = ((deviceNumber - 1) * 3_540L) / 99L
        val hours = (0 until 24 step intervalHours).toList()
        for (dayOffset in 0..1) {
            val date = now.toLocalDate().plusDays(dayOffset.toLong())
            for (hour in hours) {
                val candidate = date.atTime(hour, 0).atZone(now.zone).plusSeconds(offsetSeconds)
                if (candidate.isAfter(now)) return candidate
            }
        }
        error("No future report anchor")
    }
}
