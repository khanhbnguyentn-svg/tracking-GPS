package com.internal.tracker.network

sealed interface SendResult {
    data object Success : SendResult
    data object DnsFailure : SendResult
    data object Refused : SendResult
    data object Timeout : SendResult
    data object TlsFailure : SendResult
    data object AuthenticationFailure : SendResult
    data class HttpFailure(val code: Int) : SendResult
    data class NetworkFailure(val message: String) : SendResult
}
