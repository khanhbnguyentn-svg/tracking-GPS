package com.internal.tracker.ui

import com.internal.tracker.config.ConfigFileCodec
import com.internal.tracker.config.Scheme
import com.internal.tracker.config.TlsMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileConfigJsonTest {
    @Test
    fun importedPilotTokenSurvivesFormSave() {
        val token = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

        val json = buildConfigJson("Pilot", "a.trycloudflare.com", "443", Scheme.HTTPS, "60", TlsMode.SYSTEM, "", token)
        val profile = ConfigFileCodec().decode(json).getOrThrow()

        assertEquals(token, profile.ingestToken)
    }
}
