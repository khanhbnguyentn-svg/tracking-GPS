package com.internal.tracker.mail

data class DeliveryTelemetry(
    val lastAttemptAt: Long,
    val lastSuccessAt: Long,
    val consecutiveFailures: Int,
    val lastFailure: String?,
)

interface DeliveryTelemetryStore {
    fun snapshot(): DeliveryTelemetry
    fun accepted(at: Long)
    fun failed(at: Long, category: String)
}
