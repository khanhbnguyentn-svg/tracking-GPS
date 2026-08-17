package com.internal.tracker

import android.app.Application
import com.internal.tracker.report.ReportRun
import com.internal.tracker.report.ReportRunOwner
import com.internal.tracker.diagnostics.DiagnosticAlertDelivery
import com.internal.tracker.diagnostics.DiagnosticAlertOwner
import com.internal.tracker.schedule.ScheduleOwner
import com.internal.tracker.schedule.RecoveryCause

class TrackerApplication : Application(), ReportRunOwner, ScheduleOwner, DiagnosticAlertOwner {
    val container by lazy { AppContainer(this) }
    override val reportRun: ReportRun get() = container.reportRun
    override val diagnosticAlertDelivery: DiagnosticAlertDelivery get() = container.diagnosticAlertDelivery
    override fun reconcileBackgroundWork(cause: RecoveryCause) = container.reconcileBackgroundWork(cause)
}
