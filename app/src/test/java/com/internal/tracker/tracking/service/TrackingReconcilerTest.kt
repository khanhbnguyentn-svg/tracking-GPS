package com.internal.tracker.tracking.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingReconcilerTest {
    @Test fun startsWhenExpectedAndReady() {
        assertEquals(TrackingEffect.Start, TrackingReconciler().reconcile(TrackingExpectation.EXPECTED, permissionsReady = true, providerEnabled = true, serviceRunning = false))
    }
    @Test fun doesNothingWhenNotConfiguredOrAlreadyRunning() {
        val reconciler = TrackingReconciler()
        assertEquals(TrackingEffect.None, reconciler.reconcile(TrackingExpectation.NOT_CONFIGURED, true, true, false))
        assertEquals(TrackingEffect.None, reconciler.reconcile(TrackingExpectation.EXPECTED, true, true, true))
    }
    @Test fun requestsRepairInsteadOfStartingWhenPrerequisitesAreMissing() {
        val reconciler = TrackingReconciler()
        assertEquals(TrackingEffect.RepairPermission, reconciler.reconcile(TrackingExpectation.EXPECTED, false, true, false))
        assertEquals(TrackingEffect.RepairProvider, reconciler.reconcile(TrackingExpectation.EXPECTED, true, false, false))
    }
}
