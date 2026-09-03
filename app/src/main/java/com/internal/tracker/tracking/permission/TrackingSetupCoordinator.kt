package com.internal.tracker.tracking.permission

import com.internal.tracker.tracking.PermissionAction
import com.internal.tracker.tracking.PermissionPolicy
import com.internal.tracker.tracking.PermissionSnapshot
import com.internal.tracker.tracking.service.TrackingExpectation

data class TrackingSetupState(val action: PermissionAction, val expectation: TrackingExpectation)

class TrackingSetupCoordinator {
    fun evaluate(snapshot: PermissionSnapshot, currentExpectation: TrackingExpectation): TrackingSetupState =
        TrackingSetupState(PermissionPolicy.next(snapshot), if (PermissionPolicy.next(snapshot) == PermissionAction.Ready) TrackingExpectation.EXPECTED else currentExpectation)
}
