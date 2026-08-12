package com.internal.tracker.ui

enum class Destination { PIN, STATUS, SETTINGS, HISTORY }
enum class StatusCommand { GRANT_PERMISSION, START_TRACKING, STOP_TRACKING }

object AppUiPolicy {
    fun destinations(unlocked: Boolean): Set<Destination> =
        if (unlocked) setOf(Destination.STATUS, Destination.SETTINGS, Destination.HISTORY) else setOf(Destination.PIN)

    fun commands(ready: Boolean, tracking: Boolean): Set<StatusCommand> = when {
        tracking -> setOf(StatusCommand.STOP_TRACKING)
        ready -> setOf(StatusCommand.START_TRACKING)
        else -> setOf(StatusCommand.GRANT_PERMISSION, StatusCommand.START_TRACKING)
    }
}
