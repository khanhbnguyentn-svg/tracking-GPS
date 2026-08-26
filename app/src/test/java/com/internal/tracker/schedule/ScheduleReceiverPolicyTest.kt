package com.internal.tracker.schedule

import android.content.Intent
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

    @Test fun broadcastActionsMapToExplicitRecoveryCauses() {
        assertEquals(RecoveryCause.REBOOT, RecoveryCause.fromIntentAction(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(RecoveryCause.PACKAGE_REPLACED, RecoveryCause.fromIntentAction(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertEquals(RecoveryCause.TIME_CHANGED, RecoveryCause.fromIntentAction(Intent.ACTION_TIME_CHANGED))
        assertEquals(RecoveryCause.TIMEZONE_CHANGED, RecoveryCause.fromIntentAction(Intent.ACTION_TIMEZONE_CHANGED))
        assertEquals(RecoveryCause.PROCESS_RECREATED, RecoveryCause.fromIntentAction("unexpected"))
    }
}
