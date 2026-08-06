package com.internal.tracker.tracking

data class PermissionSnapshot(
    val locationEnabled: Boolean,
    val fineLocation: Boolean,
    val backgroundLocation: Boolean,
    val notifications: Boolean,
    val finePermanentlyDenied: Boolean,
)

enum class PermissionAction {
    OpenLocationSettings,
    RequestFine,
    RequestBackground,
    RequestNotifications,
    OpenAppSettings,
    Ready,
}

object PermissionPolicy {
    fun next(state: PermissionSnapshot): PermissionAction = when {
        !state.locationEnabled -> PermissionAction.OpenLocationSettings
        !state.fineLocation && state.finePermanentlyDenied -> PermissionAction.OpenAppSettings
        !state.fineLocation -> PermissionAction.RequestFine
        !state.backgroundLocation -> PermissionAction.RequestBackground
        !state.notifications -> PermissionAction.RequestNotifications
        else -> PermissionAction.Ready
    }
}
