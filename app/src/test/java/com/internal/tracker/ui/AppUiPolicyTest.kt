package com.internal.tracker.ui

import org.junit.Assert.assertEquals
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
}
