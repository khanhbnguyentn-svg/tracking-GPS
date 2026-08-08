package com.internal.tracker.network

import com.internal.tracker.profile.Profile
import com.internal.tracker.queue.PendingLocation
import okhttp3.HttpUrl
import okhttp3.Request

class OsmAndRequestFactory {
    fun create(profile: Profile, deviceId: String, location: PendingLocation): Request {
        val url = HttpUrl.Builder()
            .scheme(profile.scheme.name.lowercase())
            .host(profile.host)
            .port(profile.port)
            .addPathSegment("")
            .addQueryParameter("id", deviceId)
            .addQueryParameter("lat", location.latitude.toString())
            .addQueryParameter("lon", location.longitude.toString())
            .addQueryParameter("timestamp", location.timestamp.toString())
            .addQueryParameter("speed", (location.speed * METERS_PER_SECOND_TO_KNOTS).toString())
            .addQueryParameter("accuracy", location.accuracy.toString())
            .build()
        return Request.Builder().url(url).get().apply {
            profile.ingestToken?.let { header("Authorization", "Bearer $it") }
        }.build()
    }

    private companion object {
        const val METERS_PER_SECOND_TO_KNOTS = 1.943844
    }
}
