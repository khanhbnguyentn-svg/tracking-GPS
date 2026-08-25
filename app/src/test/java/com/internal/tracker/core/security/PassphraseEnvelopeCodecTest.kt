package com.internal.tracker.core.security

import java.nio.ByteBuffer
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PassphraseEnvelopeCodecTest {
    private val codec = PassphraseEnvelopeCodec()

    @Test
    fun `round trips versioned iv and ciphertext`() {
        val source = PassphraseEnvelope(1, ByteArray(12) { it.toByte() }, ByteArray(48) { (it + 20).toByte() })

        val decoded = codec.decode(codec.encode(source))

        assertEquals(1, decoded.version)
        assertArrayEquals(source.iv, decoded.iv)
        assertArrayEquals(source.ciphertext, decoded.ciphertext)
    }

    @Test
    fun `encodes the specified big endian binary layout`() {
        val encoded = codec.encode(PassphraseEnvelope(1, ByteArray(12) { (it + 1).toByte() }, ByteArray(17) { (it + 20).toByte() }))

        assertArrayEquals(
            byteArrayOf(1, 12, 0, 0, 0, 17) +
                ByteArray(12) { (it + 1).toByte() } +
                ByteArray(17) { (it + 20).toByte() },
            Base64.getDecoder().decode(encoded),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unknown envelope version`() {
        codec.encode(PassphraseEnvelope(2, ByteArray(12), ByteArray(48)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non twelve byte iv`() {
        codec.encode(PassphraseEnvelope(1, ByteArray(11), ByteArray(48)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects ciphertext shorter than authentication tag`() {
        codec.encode(PassphraseEnvelope(1, ByteArray(12), ByteArray(16)))
    }

    @Test
    fun `accepts minimum and maximum ciphertext lengths`() {
        assertEquals(17, codec.decode(codec.encode(PassphraseEnvelope(1, ByteArray(12), ByteArray(17)))).ciphertext.size)
        assertEquals(1024, codec.decode(codec.encode(PassphraseEnvelope(1, ByteArray(12), ByteArray(1024)))).ciphertext.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects ciphertext longer than maximum`() {
        codec.encode(PassphraseEnvelope(1, ByteArray(12), ByteArray(1025)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects truncated envelope`() {
        codec.decode("AQ==")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unknown decoded envelope version`() {
        codec.decode(envelopeBytes(version = 2, ivLength = 12, ciphertextLength = 17))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects decoded iv length other than twelve`() {
        codec.decode(envelopeBytes(version = 1, ivLength = 11, ciphertextLength = 17))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects decoded ciphertext below minimum`() {
        codec.decode(envelopeBytes(version = 1, ivLength = 12, ciphertextLength = 16))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects decoded ciphertext above maximum`() {
        codec.decode(envelopeBytes(version = 1, ivLength = 12, ciphertextLength = 1025))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects trailing bytes`() {
        codec.decode(envelopeBytes(version = 1, ivLength = 12, ciphertextLength = 17, trailing = byteArrayOf(99)))
    }

    @Test
    fun `copies arrays at model boundaries`() {
        val iv = ByteArray(12) { 7 }
        val ciphertext = ByteArray(17) { 8 }
        val envelope = PassphraseEnvelope(1, iv, ciphertext)
        iv[0] = 1
        ciphertext[0] = 2
        val exposedIv = envelope.iv
        val exposedCiphertext = envelope.ciphertext
        exposedIv[1] = 3
        exposedCiphertext[1] = 4

        assertArrayEquals(ByteArray(12) { 7 }, envelope.iv)
        assertArrayEquals(ByteArray(17) { 8 }, envelope.ciphertext)
    }

    private fun envelopeBytes(
        version: Int,
        ivLength: Int,
        ciphertextLength: Int,
        trailing: ByteArray = byteArrayOf(),
    ): String {
        val bytes = ByteBuffer.allocate(6 + ivLength + ciphertextLength + trailing.size)
            .put(version.toByte())
            .put(ivLength.toByte())
            .putInt(ciphertextLength)
            .put(ByteArray(ivLength))
            .put(ByteArray(ciphertextLength))
            .put(trailing)
            .array()
        return Base64.getEncoder().encodeToString(bytes)
    }
}
