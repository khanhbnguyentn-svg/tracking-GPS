package com.internal.tracker.tracking.location

import android.location.Location
import android.os.Build
import com.internal.tracker.tracking.model.RawLocationSample

object AndroidLocationMapper {
    fun map(location: Location, capturedOffsetMinutes: Int) = RawLocationSample(location.time, capturedOffsetMinutes, location.elapsedRealtimeNanos, location.latitude, location.longitude, if (location.hasAltitude()) location.altitude else null, if (location.hasAccuracy()) location.accuracy else null, if (Build.VERSION.SDK_INT >= 26 && location.hasVerticalAccuracy()) location.verticalAccuracyMeters else null, if (location.hasSpeed()) location.speed else null, if (Build.VERSION.SDK_INT >= 26 && location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond else null, if (location.hasBearing()) location.bearing else null, if (Build.VERSION.SDK_INT >= 26 && location.hasBearingAccuracy()) location.bearingAccuracyDegrees else null, location.provider, Build.VERSION.SDK_INT >= 31 && location.isMock)
}
