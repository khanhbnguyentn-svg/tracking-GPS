package com.internal.tracker.core.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdHasherTest {
    private val hasher = DeviceIdHasher()

    @Test
    fun `same Android and application IDs produce same uppercase hex ID`() {
        val first = hasher.derive("android-123", "com.internal.tracker")
        val second = hasher.derive("android-123", "com.internal.tracker")

        assertEquals(first, second)
        assertTrue(first.matches(Regex("[0-9A-F]{64}")))
    }

    @Test
    fun `derivation uses the versioned domain-separated canonical input`() {
        assertEquals(
            "74FF3D3B35C93EDDEB9157EEF15FF6E3FCBF4C7840463356094458DA143244E2",
            hasher.derive("android-123", "com.internal.tracker"),
        )
    }

    @Test
    fun `application identity is domain separated`() {
        assertNotEquals(
            hasher.derive("android-123", "com.internal.tracker"),
            hasher.derive("android-123", "another.package"),
        )
    }
}
