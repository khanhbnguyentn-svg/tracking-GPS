package com.internal.tracker.network

sealed interface DiagnosticResult {
    data object ServerReachable : DiagnosticResult
    data object DataAccepted : DiagnosticResult
    data object RealLocationRequired : DiagnosticResult
    data object DnsError : DiagnosticResult
    data object ConnectionRefused : DiagnosticResult
    data object Timeout : DiagnosticResult
    data object TlsError : DiagnosticResult
    data object AuthenticationError : DiagnosticResult
    data class HttpError(val code: Int) : DiagnosticResult
    data class NetworkError(val detail: String) : DiagnosticResult
}
