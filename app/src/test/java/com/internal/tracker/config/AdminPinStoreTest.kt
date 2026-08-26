package com.internal.tracker.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminPinStoreTest {
    @Test
    fun acceptsOnlyPilotIntervalsAndDeviceRange() {
        assertTrue(PilotConfig("001", "pic@example.com", 6, "sender@gmail.com", "abcdefghijklmnop").isValid())
        assertFalse(PilotConfig("000", "pic@example.com", 6, "sender@gmail.com", "abcdefghijklmnop").isValid())
        assertFalse(PilotConfig("001", "pic@example.com", 8, "sender@gmail.com", "abcdefghijklmnop").isValid())
    }

    @Test
    fun normalizesDisplayedAppPasswordSpacing() {
        assertTrue(PilotConfig("100", "pic@example.com", 24, "sender@gmail.com", "abcd efgh ijkl mnop").isValid())
    }

    @Test
    fun defaultPinAndChangedPinAreVerified() {
        val store = AdminPinStore(InMemoryPinPreferences())

        assertTrue(store.verify("18758691"))
        store.change("18758691", "24681357").getOrThrow()

        assertFalse(store.verify("18758691"))
        assertTrue(store.verify("24681357"))
    }
}

private class InMemoryPinPreferences : PinPreferences {
    private var value: PinRecord? = null

    override fun read(): PinRecord? = value

    override fun write(record: PinRecord) {
        value = record
    }
}
