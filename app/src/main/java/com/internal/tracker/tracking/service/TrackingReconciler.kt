package com.internal.tracker.tracking.service

enum class TrackingExpectation { NOT_CONFIGURED, EXPECTED }
sealed interface TrackingEffect { data object None : TrackingEffect; data object Start : TrackingEffect; data object RepairPermission : TrackingEffect; data object RepairProvider : TrackingEffect }

class TrackingReconciler {
    fun reconcile(expectation: TrackingExpectation, permissionsReady: Boolean, providerEnabled: Boolean, serviceRunning: Boolean): TrackingEffect =
        when {
            expectation != TrackingExpectation.EXPECTED || serviceRunning -> TrackingEffect.None
            !permissionsReady -> TrackingEffect.RepairPermission
            !providerEnabled -> TrackingEffect.RepairProvider
            else -> TrackingEffect.Start
        }
}
