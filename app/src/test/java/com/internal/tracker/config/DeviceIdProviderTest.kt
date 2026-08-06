package com.internal.tracker.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdProviderTest {
    @Test
    fun validAndroidIdIsNormalized() {
        val provider = DeviceIdProvider({ "0123456789ABCDEF" }, { null }, {})

        assertEquals("AND-0123456789abcdef", provider.get())
    }

    @Test
    fun invalidAndroidIdUsesAndPersistsFallback() {
        var stored: String? = null
        val provider = DeviceIdProvider({ "9774d56d682e549c" }, { stored }, { stored = it })

        val first = provider.get()
        val second = provider.get()

        assertTrue(first.matches(Regex("AND-[0-9a-f]{16}")))
        assertEquals(first, second)
        assertEquals(first.removePrefix("AND-"), stored)
    }
}
