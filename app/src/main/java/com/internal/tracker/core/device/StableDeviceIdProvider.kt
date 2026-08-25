package com.internal.tracker.core.device

class StableDeviceIdProvider(
    private val androidId: () -> String?,
    private val applicationId: String,
    private val hasher: DeviceIdHasher,
    private val cache: DeviceIdCache,
) {
    fun get(): DeviceIdResult {
        val cached = try {
            cache.read()
        } catch (_: DeviceIdentityStorageException) {
            return DeviceIdResult.StorageRecoveryRequired
        }
        val source = try {
            androidId()
        } catch (_: SecurityException) {
            return DeviceIdResult.SourceUnavailable
        } ?: return DeviceIdResult.SourceUnavailable
        val derived = hasher.derive(source, applicationId)

        if (cached != null) {
            return if (cached == derived) {
                DeviceIdResult.Ready(cached)
            } else {
                DeviceIdResult.IdentityMismatch(cached, derived)
            }
        }

        return try {
            cache.write(derived)
            DeviceIdResult.Ready(derived)
        } catch (_: DeviceIdentityStorageException) {
            DeviceIdResult.StorageRecoveryRequired
        }
    }
}

sealed interface DeviceIdResult {
    data class Ready(val value: String) : DeviceIdResult
    data class IdentityMismatch(val cached: String, val derived: String) : DeviceIdResult
    data object StorageRecoveryRequired : DeviceIdResult
    data object SourceUnavailable : DeviceIdResult
}
