package com.internal.tracker.network

import com.internal.tracker.config.Scheme
import com.internal.tracker.config.TlsMode
import com.internal.tracker.profile.Profile
import com.internal.tracker.queue.PendingLocation
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OsmAndTransportTest {
    @Test
    fun requestContainsEncodedOsmAndParameters() {
        val request = OsmAndRequestFactory().create(profile(), "AND-ab cd", location())
        val url = request.url

        assertEquals("AND-ab cd", url.queryParameter("id"))
        assertEquals("10.5", url.queryParameter("lat"))
        assertEquals("20.25", url.queryParameter("lon"))
        assertEquals("123000", url.queryParameter("timestamp"))
        assertEquals(1.943844, url.queryParameter("speed")!!.toDouble(), 0.000001)
        assertEquals("4.0", url.queryParameter("accuracy"))
    }

    @Test
    fun bearerTokenIsAHeaderAndNeverAQueryParameter() {
        val token = "pilot-token"
        val request = OsmAndRequestFactory().create(profile(ingestToken = token), "AND-0123456789abcdef", location())

        assertEquals("Bearer $token", request.header("Authorization"))
        assertEquals(null, request.url.queryParameter("ingestToken"))
    }

    @Test
    fun requestWithoutTokenHasNoAuthorizationHeader() {
        val request = OsmAndRequestFactory().create(profile(), "AND-0123456789abcdef", location())

        assertEquals(null, request.header("Authorization"))
    }

    @Test
    fun clientClassifiesSuccessAndHttpFailure() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200))
            server.enqueue(MockResponse().setResponseCode(400))
            val client = OsmAndClient(OkHttpClient(), OsmAndRequestFactory())
            val local = profile(host = server.hostName, port = server.port, scheme = Scheme.HTTP)

            assertEquals(SendResult.Success, client.send(local, "AND-0123456789abcdef", location()))
            assertEquals(SendResult.HttpFailure(400), client.send(local, "AND-0123456789abcdef", location()))
        }
    }

    @Test
    fun unauthorizedResponseHasASpecificResult() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401))

            val result = OsmAndClient(OkHttpClient(), OsmAndRequestFactory()).send(
                profile(server.hostName, server.port, Scheme.HTTP), "AND-0123456789abcdef", location(),
            )

            assertEquals(SendResult.AuthenticationFailure, result)
        }
    }

    @Test
    fun invalidCustomCertificateIsRejected() {
        val result = runCatching { TlsClientFactory().customCa("not a certificate".toByteArray()) }

        assertTrue(result.isFailure)
    }

    private fun profile(
        host: String = "example.com",
        port: Int = 443,
        scheme: Scheme = Scheme.HTTPS,
        ingestToken: String? = null,
    ) = Profile(1, "P", host, port, scheme, 60, TlsMode.SYSTEM, null, null, true, ingestToken)

    private fun location() = PendingLocation(1, 10.5, 20.25, 123_000, 1.0, 4.0)
}
