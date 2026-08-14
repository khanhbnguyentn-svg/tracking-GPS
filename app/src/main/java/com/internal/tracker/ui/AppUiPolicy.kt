package com.internal.tracker.ui

enum class Destination { STATUS, SETTINGS, HISTORY }
enum class StatusCommand { GRANT_PERMISSION, START_TRACKING, STOP_TRACKING }
enum class ProtectedAction { OPEN_SETTINGS, STOP_TRACKING, DELETE_FILTERED, DELETE_ALL }

object AppUiPolicy {
    val initialDestination: Destination = Destination.STATUS

    fun destinations(): Set<Destination> =
        setOf(Destination.STATUS, Destination.SETTINGS, Destination.HISTORY)

    fun commands(ready: Boolean, tracking: Boolean): Set<StatusCommand> = when {
        tracking -> setOf(StatusCommand.STOP_TRACKING)
        ready -> setOf(StatusCommand.START_TRACKING)
        else -> setOf(StatusCommand.GRANT_PERMISSION, StatusCommand.START_TRACKING)
    }

    fun requiresPin(action: ProtectedAction, settingsUnlocked: Boolean): Boolean = when (action) {
        ProtectedAction.OPEN_SETTINGS -> !settingsUnlocked
        ProtectedAction.STOP_TRACKING,
        ProtectedAction.DELETE_FILTERED,
        ProtectedAction.DELETE_ALL,
        -> true
    }
}
