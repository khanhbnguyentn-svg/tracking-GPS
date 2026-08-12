package com.internal.tracker

import android.app.Application
import com.internal.tracker.profile.Profile
import com.internal.tracker.queue.LocationSample
import com.internal.tracker.queue.QueueStore
import com.internal.tracker.tracking.TrackingDependenciesOwner
import com.internal.tracker.worker.QueueUploader
import com.internal.tracker.worker.QueueUploaderOwner
import com.internal.tracker.report.ReportRun
import com.internal.tracker.report.ReportRunOwner
import com.internal.tracker.schedule.ScheduleOwner

class TrackerApplication : Application(), QueueUploaderOwner, TrackingDependenciesOwner, ReportRunOwner, ScheduleOwner {
    val container by lazy { AppContainer(this) }
    override val queueUploader: QueueUploader get() = container.queueUploader
    override val trackingQueue: QueueStore get() = container.queue
    override val trackingUploader: QueueUploader get() = container.queueUploader
    override suspend fun activeTrackingProfile(): Profile? = container.profiles.active()
    override fun rememberLocation(sample: LocationSample) { container.latestLocation = sample }
    override val reportRun: ReportRun get() = container.reportRun
    override fun reconcileSchedule() = container.reconcileSchedule()
}
