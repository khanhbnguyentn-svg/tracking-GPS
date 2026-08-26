package com.internal.tracker.tracking

import com.internal.tracker.diagnostics.DeviceCondition
import com.internal.tracker.schedule.RecoveryCause
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingServicePolicyTest {
    @Test fun startupRecoveryCausesMapWithoutGuessingFromCoordinates() {
        assertEquals(DeviceCondition.REBOOT, TrackingHealthPolicy.startupCondition(RecoveryCause.REBOOT))
        assertEquals(DeviceCondition.PACKAGE_REPLACED, TrackingHealthPolicy.startupCondition(RecoveryCause.PACKAGE_REPLACED))
        assertEquals(DeviceCondition.PROCESS_RECREATED, TrackingHealthPolicy.startupCondition(RecoveryCause.TIME_CHANGED))
        assertEquals(DeviceCondition.PROCESS_RECREATED, TrackingHealthPolicy.startupCondition(RecoveryCause.TIMEZONE_CHANGED))
        assertEquals(DeviceCondition.PROCESS_RECREATED, TrackingHealthPolicy.startupCondition(RecoveryCause.APP_LAUNCH))
        assertEquals(DeviceCondition.PROCESS_RECREATED, TrackingHealthPolicy.startupCondition(RecoveryCause.PROCESS_RECREATED))
    }

    @Test fun missingPermissionTakesPrecedenceOverAllOtherConditions() {
        assertEquals(
            DeviceCondition.PERMISSION_MISSING,
            TrackingHealthPolicy.condition(
                hasLocationPermission = false,
                isLocationEnabled = false,
                callbackGapOpen = true,
            ),
        )
    }

    @Test fun disabledLocationTakesPrecedenceOverSilentProvider() {
        assertEquals(
            DeviceCondition.LOCATION_DISABLED,
            TrackingHealthPolicy.condition(
                hasLocationPermission = true,
                isLocationEnabled = false,
                callbackGapOpen = true,
            ),
        )
    }

    @Test fun enabledLocationWithOpenGapMeansSilentProvider() {
        assertEquals(
            DeviceCondition.PROVIDER_SILENT,
            TrackingHealthPolicy.condition(
                hasLocationPermission = true,
                isLocationEnabled = true,
                callbackGapOpen = true,
            ),
        )
    }

    @Test fun healthyCallbacksProduceNormalCondition() {
        assertEquals(
            DeviceCondition.NORMAL,
            TrackingHealthPolicy.condition(
                hasLocationPermission = true,
                isLocationEnabled = true,
                callbackGapOpen = false,
            ),
        )
    }
}
