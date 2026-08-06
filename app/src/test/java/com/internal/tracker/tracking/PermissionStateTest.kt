package com.internal.tracker.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionStateTest {
    @Test
    fun missingRequirementsAreRequestedInOrder() {
        assertEquals(PermissionAction.OpenLocationSettings, PermissionPolicy.next(snapshot(locationEnabled = false)))
        assertEquals(PermissionAction.RequestFine, PermissionPolicy.next(snapshot(fine = false)))
        assertEquals(PermissionAction.RequestBackground, PermissionPolicy.next(snapshot(background = false)))
        assertEquals(PermissionAction.RequestNotifications, PermissionPolicy.next(snapshot(notifications = false)))
        assertEquals(PermissionAction.Ready, PermissionPolicy.next(snapshot()))
    }

    @Test
    fun permanentlyDeniedPermissionOpensAppSettings() {
        assertEquals(PermissionAction.OpenAppSettings, PermissionPolicy.next(snapshot(fine = false, finePermanentlyDenied = true)))
    }

    private fun snapshot(
        locationEnabled: Boolean = true,
        fine: Boolean = true,
        background: Boolean = true,
        notifications: Boolean = true,
        finePermanentlyDenied: Boolean = false,
    ) = PermissionSnapshot(locationEnabled, fine, background, notifications, finePermanentlyDenied)
}
