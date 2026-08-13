package com.internal.tracker

import android.app.Application
import com.internal.tracker.report.ReportRun
import com.internal.tracker.report.ReportRunOwner
import com.internal.tracker.schedule.ScheduleOwner

class TrackerApplication : Application(), ReportRunOwner, ScheduleOwner {
    val container by lazy { AppContainer(this) }
    override val reportRun: ReportRun get() = container.reportRun
    override fun reconcileBackgroundWork() = container.reconcileBackgroundWork()
}
