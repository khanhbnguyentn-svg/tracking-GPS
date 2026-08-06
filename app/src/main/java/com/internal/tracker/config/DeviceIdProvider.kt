package com.internal.tracker.config

import java.util.UUID

class DeviceIdProvider(
    private val readAndroidId: () -> String?,
    private val readFallback: () -> String?,
    private val writeFallback: (String) -> Unit,
) {
    fun get(): String {
        val androidId = readAndroidId()?.lowercase()
        val value = if (androidId != null && androidId.matches(VALID_ID) && androidId != KNOWN_BAD_ID) {
            androidId
        } else {
            readFallback()?.takeIf { it.matches(VALID_ID) } ?: newFallback().also(writeFallback)
        }
        return "AND-$value"
    }

    private fun newFallback() = UUID.randomUUID().toString().replace("-", "").take(16)

    private companion object {
        val VALID_ID = Regex("[0-9a-f]{16}")
        const val KNOWN_BAD_ID = "9774d56d682e549c"
    }
}
