package com.internal.tracker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUiPolicyTest {
    @Test fun lockedAppExposesOnlyPinDestination() {
        assertEquals(setOf(Destination.PIN), AppUiPolicy.destinations(unlocked = false))
    }

    @Test fun normalStatusCommandsStayMinimal() {
        assertEquals(
            setOf(StatusCommand.GRANT_PERMISSION, StatusCommand.START_TRACKING),
            AppUiPolicy.commands(ready = false, tracking = false),
        )
    }

    @Test fun settingsPinIsRequiredOnlyUntilSessionUnlock() {
        assertTrue(AppUiPolicy.requiresPin(ProtectedAction.OPEN_SETTINGS, settingsUnlocked = false))
        assertFalse(AppUiPolicy.requiresPin(ProtectedAction.OPEN_SETTINGS, settingsUnlocked = true))
    }

    @Test fun sensitiveActionsAlwaysRequireTheirOwnPin() {
        assertTrue(AppUiPolicy.requiresPin(ProtectedAction.STOP_TRACKING, settingsUnlocked = true))
        assertTrue(AppUiPolicy.requiresPin(ProtectedAction.DELETE_FILTERED, settingsUnlocked = true))
        assertTrue(AppUiPolicy.requiresPin(ProtectedAction.DELETE_ALL, settingsUnlocked = true))
    }
}
