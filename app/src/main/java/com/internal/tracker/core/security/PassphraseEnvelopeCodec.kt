package com.internal.tracker.core.security

import java.util.Base64

class PassphraseEnvelopeCodec {
    fun encode(envelope: PassphraseEnvelope): String {
        val iv = envelope.iv
        val ciphertext = envelope.ciphertext
        validate(envelope.version, iv.size, ciphertext.size)
        val bytes = ByteArray(HEADER_SIZE + iv.size + ciphertext.size)
        bytes[0] = envelope.version.toByte()
        bytes[1] = iv.size.toByte()
        writeCiphertextLength(bytes, ciphertext.size)
        iv.copyInto(bytes, destinationOffset = HEADER_SIZE)
        ciphertext.copyInto(bytes, destinationOffset = HEADER_SIZE + iv.size)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun decode(encoded: String): PassphraseEnvelope {
        require(encoded.length <= MAX_ENCODED_LENGTH) { "Passphrase envelope exceeds maximum encoded length" }
        val bytes = try {
            Base64.getDecoder().decode(encoded)
        } catch (exception: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid passphrase envelope", exception)
        }
        require(bytes.size >= HEADER_SIZE) { "Truncated passphrase envelope" }

        val version = bytes[0].toInt() and BYTE_MASK
        val ivLength = bytes[1].toInt() and BYTE_MASK
        val ciphertextLength = readCiphertextLength(bytes)
        validate(version, ivLength, ciphertextLength)

        val expectedLength = HEADER_SIZE + ivLength + ciphertextLength
        require(bytes.size == expectedLength) { "Invalid passphrase envelope length" }
        return PassphraseEnvelope(
            version = version,
            iv = bytes.copyOfRange(HEADER_SIZE, HEADER_SIZE + ivLength),
            ciphertext = bytes.copyOfRange(HEADER_SIZE + ivLength, expectedLength),
        )
    }

    private fun validate(version: Int, ivLength: Int, ciphertextLength: Int) {
        require(version == VERSION) { "Unsupported passphrase envelope version" }
        require(ivLength == IV_LENGTH) { "Invalid passphrase envelope IV length" }
        require(ciphertextLength in MIN_CIPHERTEXT_LENGTH..MAX_CIPHERTEXT_LENGTH) {
            "Invalid passphrase envelope ciphertext length"
        }
    }

    private fun writeCiphertextLength(bytes: ByteArray, ciphertextLength: Int) {
        bytes[2] = (ciphertextLength ushr 24).toByte()
        bytes[3] = (ciphertextLength ushr 16).toByte()
        bytes[4] = (ciphertextLength ushr 8).toByte()
        bytes[5] = ciphertextLength.toByte()
    }

    private fun readCiphertextLength(bytes: ByteArray): Int {
        return ((bytes[2].toInt() and BYTE_MASK) shl 24) or
            ((bytes[3].toInt() and BYTE_MASK) shl 16) or
            ((bytes[4].toInt() and BYTE_MASK) shl 8) or
            (bytes[5].toInt() and BYTE_MASK)
    }

    private companion object {
        const val VERSION = 1
        const val IV_LENGTH = 12
        const val MIN_CIPHERTEXT_LENGTH = 17
        const val MAX_CIPHERTEXT_LENGTH = 1024
        const val HEADER_SIZE = 6
        const val BYTE_MASK = 0xff
        const val MAX_ENVELOPE_LENGTH = HEADER_SIZE + IV_LENGTH + MAX_CIPHERTEXT_LENGTH
        const val MAX_ENCODED_LENGTH = ((MAX_ENVELOPE_LENGTH + 2) / 3) * 4
    }
}
