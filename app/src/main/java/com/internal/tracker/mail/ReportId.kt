package com.internal.tracker.mail

import java.security.MessageDigest

object ReportId {
    fun create(
        deviceId: String,
        scheduledFor: Long,
        recordIds: List<Long>,
        incidentIds: List<String>,
    ): String {
        val source = buildString {
            appendLine(deviceId)
            appendLine(scheduledFor)
            appendLine(recordIds.sorted().joinToString(","))
            append(incidentIds.sorted().joinToString(","))
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
