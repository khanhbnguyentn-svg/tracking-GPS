package com.internal.tracker.network

import com.internal.tracker.profile.Profile
import com.internal.tracker.queue.PendingLocation
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import okhttp3.OkHttpClient

fun interface LocationSender {
    fun send(profile: Profile, deviceId: String, location: PendingLocation): SendResult
}

class OsmAndClient(
    private val clientFor: (Profile) -> OkHttpClient,
    private val requests: OsmAndRequestFactory,
) : LocationSender {
    constructor(client: OkHttpClient, requests: OsmAndRequestFactory) : this({ client }, requests)

    override fun send(profile: Profile, deviceId: String, location: PendingLocation): SendResult = try {
        clientFor(profile).newCall(requests.create(profile, deviceId, location)).execute().use { response ->
            when {
                response.isSuccessful -> SendResult.Success
                response.code == 401 -> SendResult.AuthenticationFailure
                else -> SendResult.HttpFailure(response.code)
            }
        }
    } catch (_: UnknownHostException) {
        SendResult.DnsFailure
    } catch (_: ConnectException) {
        SendResult.Refused
    } catch (_: SocketTimeoutException) {
        SendResult.Timeout
    } catch (_: SSLException) {
        SendResult.TlsFailure
    } catch (error: Exception) {
        SendResult.NetworkFailure(error.javaClass.simpleName)
    }
}
