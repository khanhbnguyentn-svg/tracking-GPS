package com.internal.tracker.core.platform

import com.internal.tracker.core.database.SqlCipherFactoryProvider
import com.internal.tracker.core.device.StableDeviceIdProvider
import com.internal.tracker.core.id.UuidSource
import com.internal.tracker.core.security.DatabasePassphraseStore
import com.internal.tracker.core.time.BusinessClock

data class SetPlatformModule(
    val clock: BusinessClock,
    val uuidSource: UuidSource,
    val deviceIdProvider: StableDeviceIdProvider,
    val databasePassphraseStore: DatabasePassphraseStore,
    val sqlCipherFactoryProvider: SqlCipherFactoryProvider,
)
