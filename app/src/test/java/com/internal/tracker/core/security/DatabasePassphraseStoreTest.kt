package com.internal.tracker.core.security

import java.security.InvalidKeyException
import java.security.UnrecoverableKeyException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
        val encoded = encodedEnvelope()
        val slot = MemoryEnvelopeSlot(encoded)
        val wrapper = FakePassphraseWrapper(unwrapFailure = AEADBadTagException("tampered"))
        val byteSource = RecordingByteSource(ByteArray(32))

        val result = DatabasePassphraseManager(slot, wrapper, byteSource, codec).getOrCreate()

        assertRecovery(DatabaseKeyResult.Reason.AUTHENTICATION_FAILED, result)
        assertNoReplacement(encoded, slot, wrapper, byteSource)
    }

    @Test
    fun `invalid envelope requests recovery and never creates replacement`() {
        val encoded = "not-a-passphrase-envelope"
        val slot = MemoryEnvelopeSlot(encoded)
        val wrapper = FakePassphraseWrapper()
        val byteSource = RecordingByteSource(ByteArray(32))

        val result = DatabasePassphraseManager(slot, wrapper, byteSource, codec).getOrCreate()

        assertRecovery(DatabaseKeyResult.Reason.ENVELOPE_INVALID, result)
        assertEquals(0, wrapper.unwrapCalls)
        assertNoReplacement(encoded, slot, wrapper, byteSource)
    }

    @Test
    fun `missing wrapping key requests recovery and never creates replacement`() {
        val encoded = encodedEnvelope()
        val slot = MemoryEnvelopeSlot(encoded)
        val wrapper = FakePassphraseWrapper(unwrapFailure = UnrecoverableKeyException("missing"))
        val byteSource = RecordingByteSource(ByteArray(32))

        val result = DatabasePassphraseManager(slot, wrapper, byteSource, codec).getOrCreate()

        assertRecovery(DatabaseKeyResult.Reason.KEY_MISSING, result)
        assertNoReplacement(encoded, slot, wrapper, byteSource)
    }

    @Test
    fun `invalid key failure requests recovery and never creates replacement`() {
        val encoded = encodedEnvelope()
        val slot = MemoryEnvelopeSlot(encoded)
        val wrapper = FakePassphraseWrapper(unwrapFailure = InvalidKeyException("invalidated"))
        val byteSource = RecordingByteSource(ByteArray(32))

        val result = DatabasePassphraseManager(slot, wrapper, byteSource, codec).getOrCreate()

        assertRecovery(DatabaseKeyResult.Reason.KEY_MISSING, result)
        assertNoReplacement(encoded, slot, wrapper, byteSource)
    }

    @Test
    fun `existing envelope read failure requests storage recovery without replacement`() {
        val encoded = encodedEnvelope()
        val slot = MemoryEnvelopeSlot(encoded, readFailure = IllegalStateException("read failed"))
        val wrapper = FakePassphraseWrapper()
        val byteSource = RecordingByteSource(ByteArray(32))

        val result = DatabasePassphraseManager(slot, wrapper, byteSource, codec).getOrCreate()

        assertRecovery(DatabaseKeyResult.Reason.STORAGE_ERROR, result)
        assertEquals(0, wrapper.unwrapCalls)
        assertNoReplacement(encoded, slot, wrapper, byteSource)
    }

    @Test
    fun `unexpected unwrap failure requests storage recovery without replacement`() {
        val encoded = encodedEnvelope()
        val slot = MemoryEnvelopeSlot(encoded)
        val wrapper = FakePassphraseWrapper(unwrapFailure = IllegalStateException("keystore unavailable"))
        val byteSource = RecordingByteSource(ByteArray(32))

        val result = DatabasePassphraseManager(slot, wrapper, byteSource, codec).getOrCreate()

        assertRecovery(DatabaseKeyResult.Reason.STORAGE_ERROR, result)
        assertNoReplacement(encoded, slot, wrapper, byteSource)
    }

    @Test
    fun `invalid unwrapped passphrase length requests recovery without replacement`() {
        val encoded = encodedEnvelope()
        val plaintext = ByteArray(31) { 6 }
        val slot = MemoryEnvelopeSlot(encoded)
        val wrapper = FakePassphraseWrapper(unwrappedPlaintext = plaintext)
        val byteSource = RecordingByteSource(ByteArray(32))

        val result = DatabasePassphraseManager(slot, wrapper, byteSource, codec).getOrCreate()

        assertRecovery(DatabaseKeyResult.Reason.ENVELOPE_INVALID, result)
        assertNoReplacement(encoded, slot, wrapper, byteSource)
        assertArrayEquals(ByteArray(31), plaintext)
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

    @Test
    fun `separate Android stores serialize simultaneous first access`() {
        val slot = RacingEnvelopeSlot()
        val wrapper = CopyingPassphraseWrapper()
        val firstSource = AtomicRecordingByteSource(ByteArray(32) { 1 })
        val secondSource = AtomicRecordingByteSource(ByteArray(32) { 2 })
        val firstStore = AndroidDatabasePassphraseStore(
            DatabasePassphraseManager(slot, wrapper, firstSource, codec),
        )
        val secondStore = AndroidDatabasePassphraseStore(
            DatabasePassphraseManager(slot, wrapper, secondSource, codec),
        )
        val executor = Executors.newFixedThreadPool(2)

        try {
            val firstResult = executor.submit<DatabaseKeyResult> { firstStore.getOrCreate() }
            assertTrue(slot.firstReadCaptured.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val secondWorkerStarted = CountDownLatch(1)
            val secondResult = executor.submit<DatabaseKeyResult> {
                secondWorkerStarted.countDown()
                secondStore.getOrCreate()
            }
            assertTrue(secondWorkerStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

            val secondEnteredBeforeFirstCompleted =
                slot.secondReadCaptured.await(CONCURRENT_ENTRY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            slot.releaseFirstRead.countDown()

            val firstPassphrase = readyPassphrase(firstResult.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val secondPassphrase = readyPassphrase(secondResult.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertFalse(secondEnteredBeforeFirstCompleted)
            assertArrayEquals(ByteArray(32) { 1 }, firstPassphrase)
            assertArrayEquals(firstPassphrase, secondPassphrase)
            assertEquals(1, firstSource.callCount.get() + secondSource.callCount.get())
            assertEquals(1, firstSource.callCount.get())
            assertEquals(0, secondSource.callCount.get())
            assertEquals(1, slot.writeCount.get())
        } finally {
            slot.releaseFirstRead.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }
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

    private fun readyPassphrase(result: DatabaseKeyResult): ByteArray {
        assertTrue(result is DatabaseKeyResult.Ready)
        return (result as DatabaseKeyResult.Ready).passphrase
    }

    private fun assertNoReplacement(
        expectedEncoded: String,
        slot: MemoryEnvelopeSlot,
        wrapper: FakePassphraseWrapper,
        byteSource: RecordingByteSource,
    ) {
        assertEquals(0, slot.writeCount)
        assertEquals(0, wrapper.wrapCalls)
        assertTrue(byteSource.requestedSizes.isEmpty())
        assertEquals(expectedEncoded, slot.value)
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

    private class AtomicRecordingByteSource(private val bytes: ByteArray) : SecureByteSource {
        val callCount = AtomicInteger()

        override fun next(size: Int): ByteArray {
            assertEquals(32, size)
            callCount.incrementAndGet()
            return bytes.copyOf()
        }
    }

    private class RacingEnvelopeSlot : PassphraseEnvelopeSlot {
        private val readCount = AtomicInteger()
        val writeCount = AtomicInteger()
        val firstReadCaptured = CountDownLatch(1)
        val secondReadCaptured = CountDownLatch(1)
        val releaseFirstRead = CountDownLatch(1)
        @Volatile
        private var value: String? = null

        override fun read(): String? {
            val snapshot = value
            when (readCount.incrementAndGet()) {
                1 -> {
                    firstReadCaptured.countDown()
                    check(releaseFirstRead.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        "Timed out waiting to release the first slot read"
                    }
                }
                2 -> secondReadCaptured.countDown()
            }
            return snapshot
        }

        override fun write(encoded: String) {
            writeCount.incrementAndGet()
            value = encoded
        }
    }

    private class CopyingPassphraseWrapper : PassphraseWrapper {
        override fun wrap(plaintext: ByteArray): PassphraseEnvelope = PassphraseEnvelope(
            version = 1,
            iv = ByteArray(12) { 3 },
            ciphertext = plaintext.copyOf() + ByteArray(16) { 4 },
        )

        override fun unwrap(envelope: PassphraseEnvelope): ByteArray =
            envelope.ciphertext.copyOfRange(0, 32)
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

    private companion object {
        const val TIMEOUT_SECONDS = 5L
        const val CONCURRENT_ENTRY_TIMEOUT_MILLIS = 1_000L
    }
}
