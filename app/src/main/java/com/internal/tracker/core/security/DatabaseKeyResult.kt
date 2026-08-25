package com.internal.tracker.core.security

sealed interface DatabaseKeyResult {
    data class Ready(val passphrase: ByteArray) : DatabaseKeyResult
    data class RecoveryRequired(val reason: Reason) : DatabaseKeyResult

    enum class Reason { KEY_MISSING, ENVELOPE_INVALID, AUTHENTICATION_FAILED, STORAGE_ERROR }
}
