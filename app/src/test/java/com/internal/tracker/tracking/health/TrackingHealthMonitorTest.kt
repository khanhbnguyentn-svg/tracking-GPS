package com.internal.tracker.tracking.health

import com.internal.tracker.tracking.model.HealthDirective
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingHealthMonitorTest {
    @Test
    fun opensOneGapAtThirtySecondsAndReregistersAfterFiveMinutes() {
        val monitor = TrackingHealthMonitor()
        monitor.onStarted(0L)

        assertEquals(HealthDirective.OpenGap(reRegisterNow = true), monitor.onTick(30_000_000_000L))
        assertEquals(HealthDirective.None, monitor.onTick(40_000_000_000L))
        assertEquals(HealthDirective.ReRegister, monitor.onTick(330_000_000_000L))
    }

    @Test
    fun closesGapWhenLocationCallbackResumes() {
        val monitor = TrackingHealthMonitor()
        monitor.onStarted(0L)
        monitor.onTick(30_000_000_000L)

        assertEquals(HealthDirective.CloseGap, monitor.onLocationCallback(31_000_000_000L))
    }
}
