package com.internal.tracker.network

import com.internal.tracker.profile.Profile
import com.internal.tracker.queue.LocationSample
import com.internal.tracker.queue.PendingLocation
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

fun interface NetworkProbe {
    fun test(profile: Profile): SendResult
}

class ConnectionTester(
    private val probe: NetworkProbe,
    private val sender: LocationSender,
) {
    fun testNetwork(profile: Profile): DiagnosticResult = probe.test(profile).toDiagnostic(success = DiagnosticResult.ServerReachable)

    fun sendLatest(profile: Profile, deviceId: String, location: LocationSample?): DiagnosticResult {
        location ?: return DiagnosticResult.RealLocationRequired
        val pending = PendingLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = location.timestamp,
            speed = location.speed,
            accuracy = location.accuracy,
        )
        return sender.send(profile, deviceId, pending).toDiagnostic(success = DiagnosticResult.DataAccepted)
    }

    private fun SendResult.toDiagnostic(success: DiagnosticResult): DiagnosticResult = when (this) {
        SendResult.Success -> success
        SendResult.DnsFailure -> DiagnosticResult.DnsError
        SendResult.Refused -> DiagnosticResult.ConnectionRefused
        SendResult.Timeout -> DiagnosticResult.Timeout
        SendResult.TlsFailure -> DiagnosticResult.TlsError
        is SendResult.HttpFailure -> DiagnosticResult.HttpError(code)
        is SendResult.NetworkFailure -> DiagnosticResult.NetworkError(message)
    }
}

class OkHttpNetworkProbe(private val clientFor: (Profile) -> OkHttpClient) : NetworkProbe {
    override fun test(profile: Profile): SendResult = try {
        val url = HttpUrl.Builder()
            .scheme(profile.scheme.name.lowercase())
            .host(profile.host)
            .port(profile.port)
            .addPathSegment("")
            .build()
        val client = clientFor(profile).newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (response.code in 200..499) SendResult.Success else SendResult.HttpFailure(response.code)
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
