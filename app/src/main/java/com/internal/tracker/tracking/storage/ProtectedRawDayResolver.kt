package com.internal.tracker.tracking.storage

import java.time.LocalDate

fun interface ProtectedRawDayResolver { fun protectedDays(): Set<LocalDate> }
