package com.internal.tracker.core.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StableDeviceIdProviderTest {
    @Test
    fun `provider persists first derived identity`() {
        val cache = FakeDeviceIdCache()
        val provider = StableDeviceIdProvider(
            androidId = { "android-123" },
            applicationId = "com.internal.tracker",
            hasher = DeviceIdHasher(),
            cache = cache,
        )

        val result = provider.get()

        assertTrue(result is DeviceIdResult.Ready)
        assertEquals((result as DeviceIdResult.Ready).value, cache.value)
    }

    @Test
    fun `provider reports recovery instead of replacing a mismatched cached identity`() {
        val cache = FakeDeviceIdCache("A".repeat(64))
        val provider = StableDeviceIdProvider(
            androidId = { "different-android-id" },
            applicationId = "com.internal.tracker",
            hasher = DeviceIdHasher(),
            cache = cache,
        )

        assertTrue(provider.get() is DeviceIdResult.IdentityMismatch)
        assertEquals("A".repeat(64), cache.value)
    }

    @Test
    fun `provider returns source unavailable without writing when Android ID is unavailable`() {
        val cache = FakeDeviceIdCache()
        val provider = StableDeviceIdProvider(
            androidId = { null },
            applicationId = "com.internal.tracker",
            hasher = DeviceIdHasher(),
            cache = cache,
        )

        assertTrue(provider.get() is DeviceIdResult.SourceUnavailable)
        assertEquals(null, cache.value)
    }

    @Test
    fun `provider returns recovery without deriving when cache read fails`() {
        val provider = StableDeviceIdProvider(
            androidId = { throw AssertionError("Android ID must not be read after cache failure") },
            applicationId = "com.internal.tracker",
            hasher = DeviceIdHasher(),
            cache = ThrowingReadCache(),
        )

        assertTrue(provider.get() is DeviceIdResult.StorageRecoveryRequired)
    }

    @Test
    fun `provider returns recovery when first cache write fails`() {
        val provider = StableDeviceIdProvider(
            androidId = { "android-123" },
            applicationId = "com.internal.tracker",
            hasher = DeviceIdHasher(),
            cache = ThrowingWriteCache(),
        )

        assertTrue(provider.get() is DeviceIdResult.StorageRecoveryRequired)
    }

    private class FakeDeviceIdCache(var value: String? = null) : DeviceIdCache {
        override fun read(): String? = value

        override fun write(deviceId: String) {
            value = deviceId
        }
    }

    private class ThrowingReadCache : DeviceIdCache {
        override fun read(): String? = throw DeviceIdentityStorageException()

        override fun write(deviceId: String) = Unit
    }

    private class ThrowingWriteCache : DeviceIdCache {
        override fun read(): String? = null

        override fun write(deviceId: String) {
            throw DeviceIdentityStorageException()
        }
    }
}
