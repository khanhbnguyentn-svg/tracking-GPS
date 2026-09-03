package com.internal.tracker.tracking.location

import com.internal.tracker.tracking.model.RawLocationSample

interface LocationSource {
    fun startOrdinary(onLocation: (RawLocationSample) -> Unit)
    fun stopOrdinary()
}
