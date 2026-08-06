package com.internal.tracker

import android.app.Application
import com.internal.tracker.profile.Profile
import com.internal.tracker.queue.LocationSample
import com.internal.tracker.queue.QueueStore
import com.internal.tracker.tracking.TrackingDependenciesOwner
import com.internal.tracker.worker.QueueUploader
import com.internal.tracker.worker.QueueUploaderOwner

class TrackerApplication : Application(), QueueUploaderOwner, TrackingDependenciesOwner {
    val container by lazy { AppContainer(this) }
    override val queueUploader: QueueUploader get() = container.queueUploader
    override val trackingQueue: QueueStore get() = container.queue
    override val trackingUploader: QueueUploader get() = container.queueUploader
    override suspend fun activeTrackingProfile(): Profile? = container.profiles.active()
    override fun rememberLocation(sample: LocationSample) { container.latestLocation = sample }
}
