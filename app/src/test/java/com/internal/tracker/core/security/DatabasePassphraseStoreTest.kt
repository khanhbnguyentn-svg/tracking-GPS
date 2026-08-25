package com.internal.tracker.core.security

import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabasePassphraseStoreTest {
    private val codec = PassphraseEnvelopeCodec()

    @Test
    fun `first access creates 32 bytes wraps stores and returns a copy`() {
        val generated = ByteArray(32) { (it + 1).toByte() }
        val expected = generated.copyOf()
        val slot = MemoryEnvelopeSlot()
        val wrapper = FakePassphraseWrapper()
        val byteSource = RecordingByteSource(generated)
        val manager = DatabasePassphraseManager(slot, wrapper, byteSource, codec)

        val result = manager.getOrCreate()

        assertTrue(result is DatabaseKeyResult.Ready)
        val ready = result as DatabaseKeyResult.Ready
        assertArrayEquals(expected, ready.passphrase)
        assertNotSame(generated, ready.passphrase)
        assertEquals(listOf(32), byteSource.requestedSizes)
        assertEquals(1, wrapper.wrapCalls)
        assertEquals(0, wrapper.unwrapCalls)
        assertEquals(1, slot.writeCount)
        assertEnvelopeEquals(wrapper.wrappedEnvelope, codec.decode(requireNotNull(slot.value)))
        assertArrayEquals(ByteArray(32), generated)
        assertArrayEquals(ByteArray(32), requireNotNull(wrapper.wrappedPlaintext))
    }

    @Test
    fun `existing envelope unwraps without generating a new passphrase`() {
        val encoded = encodedEnvelope()
        val plaintext = ByteArray(32) { (it + 40).toByte() }
        val expected = plaintext.copyOf()
        val slot = MemoryEnvelopeSlot(encoded)
        val wrapper = FakePassphraseWrapper(unwrappedPlaintext = plaintext)
        val byteSource = RecordingByteSource(ByteArray(32))
        val manager = DatabasePassphraseManager(slot, wrapper, byteSource, codec)

        val result = manager.getOrCreate()

        assertTrue(result is DatabaseKeyResult.Ready)
        val ready = result as DatabaseKeyResult.Ready
        assertArrayEquals(expected, ready.passphrase)
        assertNotSame(plaintext, ready.passphrase)
        assertEquals(emptyList<Int>(), byteSource.requestedSizes)
        assertEquals(0, wrapper.wrapCalls)
        assertEquals(1, wrapper.unwrapCalls)
        assertEquals(0, slot.writeCount)
        assertEquals(encoded, slot.value)
        assertArrayEquals(ByteArray(32), plaintext)
    }

    @Test
    fun `unwrap authentication failure requests recovery and never overwrites envelope`() {
        val slot = MemoryEnvelopeSlot(encodedEnvelope())
        val wrapper = FakePassphraseWrapper(unwrapFailure = AEADBadTagException("tampered"))
        val byteSource = RecordingByteSource(ByteArray(32))

        val result = DatabasePassphraseManager(slot, wrapper, byteSource, codec).getOrCreate()

        assertRecovery(DatabaseKeyResult.Reason.AUTHENTICATION_FAILED, result)
        assertNoReplacement(slot, byteSource)
    }

    @Test
    fun `invalid envelope requests recovery and never creates replacement`() {
        val slot = MemoryEnvelopeSlot("not-a-passphrase-envelope")
        val wrapper = FakePassphraseWrapper()
        val byteSource = RecordingByteSource(ByteArray(32))

        val result = DatabasePassphraseManager(slot, wrapper, byteSource, codec).getOrCreate()

        assertRecovery(DatabaseKeyResult.Reason.ENVELOPE_INVALID, result)
        assertEquals(0, wrapper.unwrapCalls)
        assertNoReplacement(slot, byteSource)
    }

    @Test
    fun `missing wrapping key requests recovery and never creates replacement`() {
        val slot = MemoryEnvelopeSlot(encodedEnvelope())
        val wrapper = FakePassphraseWrapper(unwrapFailure = UnrecoverableKeyException("missing"))
        val byteSource = RecordingByteSource(ByteArray(32))

        val result = DatabasePassphraseManager(slot, wrapper, byteSource, codec).getOrCreate()

        assertRecovery(DatabaseKeyResult.Reason.KEY_MISSING, result)
        assertNoReplacement(slot, byteSource)
    }

    @Test
    fun `existing envelope read failure requests storage recovery without replacement`() {
        val slot = MemoryEnvelopeSlot(encodedEnvelope(), readFailure = IllegalStateException("read failed"))
        val wrapper = FakePassphraseWrapper()
        val byteSource = RecordingByteSource(ByteArray(32))

        val result = DatabasePassphraseManager(slot, wrapper, byteSource, codec).getOrCreate()

        assertRecovery(DatabaseKeyResult.Reason.STORAGE_ERROR, result)
        assertEquals(0, wrapper.wrapCalls)
        assertEquals(0, wrapper.unwrapCalls)
        assertNoReplacement(slot, byteSource)
    }

    @Test
    fun `unexpected unwrap failure requests storage recovery without replacement`() {
        val slot = MemoryEnvelopeSlot(encodedEnvelope())
        val wrapper = FakePassphraseWrapper(unwrapFailure = IllegalStateException("keystore unavailable"))
        val byteSource = RecordingByteSource(ByteArray(32))

        val result = DatabasePassphraseManager(slot, wrapper, byteSource, codec).getOrCreate()

        assertRecovery(DatabaseKeyResult.Reason.STORAGE_ERROR, result)
        assertNoReplacement(slot, byteSource)
    }

    @Test
    fun `first access write failure requests storage recovery and zeroes generated passphrase`() {
        val generated = ByteArray(32) { 9 }
        val slot = MemoryEnvelopeSlot(writeFailure = IllegalStateException("write failed"))
        val wrapper = FakePassphraseWrapper()
        val byteSource = RecordingByteSource(generated)

        val result = DatabasePassphraseManager(slot, wrapper, byteSource, codec).getOrCreate()

        assertRecovery(DatabaseKeyResult.Reason.STORAGE_ERROR, result)
        assertEquals(listOf(32), byteSource.requestedSizes)
        assertEquals(1, wrapper.wrapCalls)
        assertEquals(1, slot.writeCount)
        assertEquals(null, slot.value)
        assertArrayEquals(ByteArray(32), generated)
        assertArrayEquals(ByteArray(32), requireNotNull(wrapper.wrappedPlaintext))
    }

    private fun encodedEnvelope(): String = codec.encode(
        PassphraseEnvelope(
            version = 1,
            iv = ByteArray(12) { (it + 1).toByte() },
            ciphertext = ByteArray(48) { (it + 20).toByte() },
        ),
    )

    private fun assertRecovery(expected: DatabaseKeyResult.Reason, actual: DatabaseKeyResult) {
        assertTrue(actual is DatabaseKeyResult.RecoveryRequired)
        assertEquals(expected, (actual as DatabaseKeyResult.RecoveryRequired).reason)
    }

    private fun assertNoReplacement(slot: MemoryEnvelopeSlot, byteSource: RecordingByteSource) {
        assertEquals(0, slot.writeCount)
        assertTrue(byteSource.requestedSizes.isEmpty())
        assertFalse(slot.value.isNullOrEmpty())
    }

    private fun assertEnvelopeEquals(expected: PassphraseEnvelope, actual: PassphraseEnvelope) {
        assertEquals(expected.version, actual.version)
        assertArrayEquals(expected.iv, actual.iv)
        assertArrayEquals(expected.ciphertext, actual.ciphertext)
    }

    private class MemoryEnvelopeSlot(
        var value: String? = null,
        private val readFailure: Exception? = null,
        private val writeFailure: Exception? = null,
    ) : PassphraseEnvelopeSlot {
        var writeCount = 0

        override fun read(): String? {
            readFailure?.let { throw it }
            return value
        }

        override fun write(encoded: String) {
            writeCount += 1
            writeFailure?.let { throw it }
            value = encoded
        }
    }

    private class RecordingByteSource(private val bytes: ByteArray) : SecureByteSource {
        val requestedSizes = mutableListOf<Int>()

        override fun next(size: Int): ByteArray {
            requestedSizes += size
            return bytes
        }
    }

    private class FakePassphraseWrapper(
        private val unwrappedPlaintext: ByteArray = ByteArray(32) { (it + 70).toByte() },
        private val unwrapFailure: Exception? = null,
    ) : PassphraseWrapper {
        val wrappedEnvelope = PassphraseEnvelope(
            version = 1,
            iv = ByteArray(12) { (it + 5).toByte() },
            ciphertext = ByteArray(48) { (it + 90).toByte() },
        )
        var wrappedPlaintext: ByteArray? = null
        var wrapCalls = 0
        var unwrapCalls = 0

        override fun wrap(plaintext: ByteArray): PassphraseEnvelope {
            wrapCalls += 1
            wrappedPlaintext = plaintext
            return wrappedEnvelope
        }

        override fun unwrap(envelope: PassphraseEnvelope): ByteArray {
            unwrapCalls += 1
            unwrapFailure?.let { throw it }
            return unwrappedPlaintext
        }
    }
}
