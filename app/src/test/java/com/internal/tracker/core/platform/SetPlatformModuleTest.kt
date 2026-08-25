package com.internal.tracker.core.platform

import com.internal.tracker.core.database.SqlCipherFactoryProvider
import com.internal.tracker.core.device.DeviceIdCache
import com.internal.tracker.core.device.DeviceIdHasher
import com.internal.tracker.core.device.StableDeviceIdProvider
import com.internal.tracker.core.id.UuidSource
import com.internal.tracker.core.security.DatabaseKeyResult
import com.internal.tracker.core.security.DatabasePassphraseStore
import com.internal.tracker.core.time.BusinessClock
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertSame
import org.junit.Test

class SetPlatformModuleTest {
    @Test
    fun `module exposes the exact foundation service instances`() {
        val clock = object : BusinessClock {
            override fun now(): Instant = Instant.EPOCH

            override fun elapsedRealtimeNanos(): Long = 0
        }
        val uuidSource = UuidSource { UUID(0, 1) }
        val deviceIdProvider = StableDeviceIdProvider(
            androidId = { "android-id" },
            applicationId = "com.internal.tracker.test",
            hasher = DeviceIdHasher(),
            cache = object : DeviceIdCache {
                override fun read(): String? = null

                override fun write(deviceId: String) = Unit
            },
        )
        val databasePassphraseStore = DatabasePassphraseStore {
            DatabaseKeyResult.Ready(ByteArray(32))
        }
        val sqlCipherFactoryProvider = SqlCipherFactoryProvider()

        val module = SetPlatformModule(
            clock = clock,
            uuidSource = uuidSource,
            deviceIdProvider = deviceIdProvider,
            databasePassphraseStore = databasePassphraseStore,
            sqlCipherFactoryProvider = sqlCipherFactoryProvider,
        )

        assertSame(clock, module.clock)
        assertSame(uuidSource, module.uuidSource)
        assertSame(deviceIdProvider, module.deviceIdProvider)
        assertSame(databasePassphraseStore, module.databasePassphraseStore)
        assertSame(sqlCipherFactoryProvider, module.sqlCipherFactoryProvider)
    }
}
