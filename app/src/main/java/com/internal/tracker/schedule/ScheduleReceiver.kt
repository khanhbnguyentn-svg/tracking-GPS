package com.internal.tracker.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

object ScheduleReceiverPolicy {
    fun shouldReconcile(trackingEnabled: Boolean) = trackingEnabled
}

interface ScheduleOwner { fun reconcileSchedule() }

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        (context.applicationContext as? ScheduleOwner)?.reconcileSchedule()
    }
}
