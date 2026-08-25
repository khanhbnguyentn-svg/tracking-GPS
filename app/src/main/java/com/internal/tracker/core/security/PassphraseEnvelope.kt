package com.internal.tracker.core.security

class PassphraseEnvelope(
    val version: Int,
    iv: ByteArray,
    ciphertext: ByteArray,
) {
    private val storedIv = iv.copyOf()
    private val storedCiphertext = ciphertext.copyOf()

    val iv: ByteArray
        get() = storedIv.copyOf()

    val ciphertext: ByteArray
        get() = storedCiphertext.copyOf()
}
