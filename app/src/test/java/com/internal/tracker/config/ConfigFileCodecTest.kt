package com.internal.tracker.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun versionTwoAcceptsOnlyAValidOptionalPilotToken() {
        val token = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        val valid = codec.decode("""{"version":2,"name":"Pilot","host":"a.trycloudflare.com","port":443,"scheme":"https","intervalSeconds":60,"tlsMode":"system","ingestToken":"$token"}""")
        val empty = codec.decode("""{"version":2,"name":"Pilot","host":"a.com","port":443,"scheme":"https","intervalSeconds":60,"tlsMode":"system","ingestToken":""}""")
        val invalid = codec.decode("""{"version":2,"name":"Pilot","host":"a.com","port":443,"scheme":"https","intervalSeconds":60,"tlsMode":"system","ingestToken":"not/a/token"}""")

        assertEquals(token, valid.getOrThrow().ingestToken)
        assertTrue(empty.isFailure)
        assertTrue(invalid.isFailure)
    }

    @Test
    fun legacyVersionOneStillLoadsWithoutAToken() {
        val result = codec.decode("""{"version":1,"name":"LAN","host":"192.168.1.61","port":5055,"scheme":"http","intervalSeconds":60,"tlsMode":"system"}""").getOrThrow()

        assertEquals(null, result.ingestToken)
    }

    @Test
    fun exportedTemplateIsVersionTwoWithoutASecret() {
        val template = codec.encodeTemplate()

        assertEquals(2, org.json.JSONObject(template).getInt("version"))
        assertFalse(org.json.JSONObject(template).has("ingestToken"))
    }

    @Test
    fun rejectsUnknownFieldsAndVersions() {
        val unknown = codec.decode("""{"version":1,"name":"P","host":"a.com","port":443,"scheme":"https","intervalSeconds":60,"tlsMode":"system","extra":true}""")
        val future = codec.decode("""{"version":3,"name":"P","host":"a.com","port":443,"scheme":"https","intervalSeconds":60,"tlsMode":"system"}""")

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
