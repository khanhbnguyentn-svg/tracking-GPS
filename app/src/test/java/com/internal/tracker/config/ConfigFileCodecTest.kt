package com.internal.tracker.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigFileCodecTest {
    private val codec = ConfigFileCodec()

    @Test
    fun templateRoundTrips() {
        val decoded = codec.decode(codec.encodeTemplate()).getOrThrow()

        assertEquals("Production", decoded.name)
        assertEquals("traccar.internal.company.com", decoded.host)
        assertEquals(443, decoded.port)
        assertEquals(Scheme.HTTPS, decoded.scheme)
        assertEquals(60, decoded.intervalSeconds)
        assertEquals(TlsMode.SYSTEM, decoded.tlsMode)
    }

    @Test
    fun rejectsUnknownFieldsAndVersions() {
        val unknown = codec.decode("""{"version":1,"name":"P","host":"a.com","port":443,"scheme":"https","intervalSeconds":60,"tlsMode":"system","extra":true}""")
        val future = codec.decode("""{"version":2,"name":"P","host":"a.com","port":443,"scheme":"https","intervalSeconds":60,"tlsMode":"system"}""")

        assertTrue(unknown.isFailure)
        assertTrue(future.isFailure)
    }

    @Test
    fun pinningRequiresValidSha256Pin() {
        val missing = codec.decode("""{"version":1,"name":"P","host":"a.com","port":443,"scheme":"https","intervalSeconds":60,"tlsMode":"pinning"}""")
        val valid = codec.decode("""{"version":1,"name":"P","host":"a.com","port":443,"scheme":"https","intervalSeconds":60,"tlsMode":"pinning","certificatePin":"sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="}""")

        assertTrue(missing.isFailure)
        assertTrue(valid.isSuccess)
    }

    @Test
    fun validatesConnectionRanges() {
        val badPort = codec.decode("""{"version":1,"name":"P","host":"a.com","port":70000,"scheme":"https","intervalSeconds":60,"tlsMode":"system"}""")
        val fast = codec.decode("""{"version":1,"name":"P","host":"a.com","port":443,"scheme":"https","intervalSeconds":5,"tlsMode":"system"}""")

        assertTrue(badPort.isFailure)
        assertTrue(fast.isFailure)
    }
}
