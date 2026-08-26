package com.internal.tracker.tracking

import com.internal.tracker.diagnostics.DeviceCondition
import com.internal.tracker.schedule.RecoveryCause

object TrackingHealthPolicy {
    fun condition(
        hasLocationPermission: Boolean,
        isLocationEnabled: Boolean,
        callbackGapOpen: Boolean,
    ): DeviceCondition = when {
        !hasLocationPermission -> DeviceCondition.PERMISSION_MISSING
        !isLocationEnabled -> DeviceCondition.LOCATION_DISABLED
        callbackGapOpen -> DeviceCondition.PROVIDER_SILENT
        else -> DeviceCondition.NORMAL
    }

    fun startupCondition(cause: RecoveryCause?): DeviceCondition = when (cause) {
        RecoveryCause.REBOOT -> DeviceCondition.REBOOT
        RecoveryCause.PACKAGE_REPLACED -> DeviceCondition.PACKAGE_REPLACED
        RecoveryCause.TIME_CHANGED,
        RecoveryCause.TIMEZONE_CHANGED,
        RecoveryCause.APP_LAUNCH,
        RecoveryCause.PROCESS_RECREATED,
        -> DeviceCondition.PROCESS_RECREATED
        null -> DeviceCondition.NORMAL
    }
}
