package com.internal.tracker.tracking

import com.google.android.gms.location.Priority
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationPriorityPolicyTest {
    @Test
    fun everyTrackingModeKeepsHighAccuracyGpsActive() {
        MovementMode.entries.forEach { mode ->
            assertEquals(
                "$mode must not disable high-accuracy GPS",
                Priority.PRIORITY_HIGH_ACCURACY,
                LocationPriorityPolicy.forMode(mode),
            )
        }
    }
}
