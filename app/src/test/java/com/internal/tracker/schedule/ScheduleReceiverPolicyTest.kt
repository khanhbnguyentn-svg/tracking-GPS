package com.internal.tracker.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleReceiverPolicyTest {
    @Test fun enabledTrackingRestoresServiceAndSchedule() {
        assertEquals(
            setOf(ReconcileAction.TRACKING, ReconcileAction.SCHEDULE),
            ScheduleReceiverPolicy.actions(trackingEnabled = true),
        )
    }

    @Test fun disabledTrackingOnlyReconcilesSchedule() {
        assertEquals(
            setOf(ReconcileAction.SCHEDULE),
            ScheduleReceiverPolicy.actions(trackingEnabled = false),
        )
    }
}
