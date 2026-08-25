package com.internal.tracker.core.time

import java.time.Instant
import java.time.ZoneId

object BusinessTime {
    val ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    fun expenseLockAt(endActionAt: Instant): Instant =
        endActionAt.atZone(ZONE).toLocalDate().plusDays(2).atStartOfDay(ZONE).toInstant()
}
