package com.internal.tracker.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

enum class ReconcileAction { TRACKING, SCHEDULE }

object ScheduleReceiverPolicy {
    fun actions(trackingEnabled: Boolean): Set<ReconcileAction> = buildSet {
        if (trackingEnabled) add(ReconcileAction.TRACKING)
        add(ReconcileAction.SCHEDULE)
    }
}

interface ScheduleOwner { fun reconcileBackgroundWork() }

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        (context.applicationContext as? ScheduleOwner)?.reconcileBackgroundWork()
    }
}
