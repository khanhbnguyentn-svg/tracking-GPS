package com.internal.tracker.core.device

interface DeviceIdCache {
    @Throws(DeviceIdentityStorageException::class)
    fun read(): String?

    @Throws(DeviceIdentityStorageException::class)
    fun write(deviceId: String)
}

class DeviceIdentityStorageException(
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
