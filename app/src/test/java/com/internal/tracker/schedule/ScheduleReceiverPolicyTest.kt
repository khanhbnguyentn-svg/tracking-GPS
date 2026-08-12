package com.internal.tracker.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleReceiverPolicyTest {
    @Test fun restoresOnlyWhenTrackingWasEnabled() {
        assertTrue(ScheduleReceiverPolicy.shouldReconcile(true))
        assertFalse(ScheduleReceiverPolicy.shouldReconcile(false))
    }
}
