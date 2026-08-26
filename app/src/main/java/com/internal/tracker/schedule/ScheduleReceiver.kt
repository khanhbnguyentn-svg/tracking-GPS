package com.internal.tracker.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

enum class ReconcileAction { TRACKING, SCHEDULE }

enum class RecoveryCause {
    REBOOT,
    PACKAGE_REPLACED,
    TIME_CHANGED,
    TIMEZONE_CHANGED,
    APP_LAUNCH,
    PROCESS_RECREATED;

    companion object {
        fun fromIntentAction(action: String?): RecoveryCause = when (action) {
            Intent.ACTION_BOOT_COMPLETED -> REBOOT
            Intent.ACTION_MY_PACKAGE_REPLACED -> PACKAGE_REPLACED
            Intent.ACTION_TIME_CHANGED -> TIME_CHANGED
            Intent.ACTION_TIMEZONE_CHANGED -> TIMEZONE_CHANGED
            else -> PROCESS_RECREATED
        }
    }
}

object ScheduleReceiverPolicy {
    fun actions(trackingEnabled: Boolean): Set<ReconcileAction> = buildSet {
        if (trackingEnabled) add(ReconcileAction.TRACKING)
        add(ReconcileAction.SCHEDULE)
    }
}

object AppLaunchReconcilePolicy {
    fun actions(trackingEnabled: Boolean): Set<ReconcileAction> =
        if (trackingEnabled) setOf(ReconcileAction.TRACKING) else emptySet()
}

interface ScheduleOwner { fun reconcileBackgroundWork(cause: RecoveryCause) }

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        (context.applicationContext as? ScheduleOwner)
            ?.reconcileBackgroundWork(RecoveryCause.fromIntentAction(intent?.action))
    }
}
