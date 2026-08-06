package com.internal.tracker.worker

import com.internal.tracker.network.LocationSender
import com.internal.tracker.network.SendResult
import com.internal.tracker.profile.Profile
import com.internal.tracker.queue.QueueStore

data class UploadSummary(val sent: Int, val failed: Int)

class QueueUploader(
    private val queue: QueueStore,
    private val activeProfile: suspend () -> Profile?,
    private val deviceId: () -> String,
    private val sender: LocationSender,
) {
    suspend fun drain(maxItems: Int = 100): UploadSummary {
        val profile = activeProfile() ?: return UploadSummary(0, 1)
        var sent = 0
        for (location in queue.oldest(maxItems.coerceAtMost(100))) {
            if (sender.send(profile, deviceId(), location) == SendResult.Success) {
                queue.markSent(location.id)
                sent++
            } else {
                queue.incrementRetry(location.id)
                return UploadSummary(sent, 1)
            }
        }
        return UploadSummary(sent, 0)
    }
}
