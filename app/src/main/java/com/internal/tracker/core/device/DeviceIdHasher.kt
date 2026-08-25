package com.internal.tracker.core.device

import java.security.MessageDigest

class DeviceIdHasher {
    fun derive(androidId: String, applicationId: String): String {
        val canonical = "$DOMAIN\u0000$applicationId\u0000$androidId"
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02X".format(byte) }
    }

    private companion object {
        const val DOMAIN = "android-set-device-id:v1"
    }
}
