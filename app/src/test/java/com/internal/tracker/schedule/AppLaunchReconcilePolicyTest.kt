package com.internal.tracker.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLaunchReconcilePolicyTest {
    @Test
    fun enabledTrackingRestoresOnlyTheService() {
        assertEquals(
            setOf(ReconcileAction.TRACKING),
            AppLaunchReconcilePolicy.actions(trackingEnabled = true),
        )
    }

    @Test
    fun disabledTrackingDoesNothing() {
        assertEquals(
            emptySet<ReconcileAction>(),
            AppLaunchReconcilePolicy.actions(trackingEnabled = false),
        )
    }
}
