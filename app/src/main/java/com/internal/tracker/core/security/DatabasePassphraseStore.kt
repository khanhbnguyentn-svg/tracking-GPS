package com.internal.tracker.core.security

fun interface DatabasePassphraseStore {
    fun getOrCreate(): DatabaseKeyResult
}
