package com.internal.tracker.core.security

import java.security.InvalidKeyException
import java.security.UnrecoverableKeyException
import javax.crypto.BadPaddingException

internal interface PassphraseEnvelopeSlot {
    fun read(): String?
    fun write(encoded: String)
}

internal interface PassphraseWrapper {
    fun wrap(plaintext: ByteArray): PassphraseEnvelope
    fun unwrap(envelope: PassphraseEnvelope): ByteArray
}

internal fun interface SecureByteSource {
    fun next(size: Int): ByteArray
}

internal class DatabasePassphraseManager(
    private val slot: PassphraseEnvelopeSlot,
    private val wrapper: PassphraseWrapper,
    private val byteSource: SecureByteSource,
    private val codec: PassphraseEnvelopeCodec,
) : DatabasePassphraseStore {
    override fun getOrCreate(): DatabaseKeyResult {
        val encoded = try {
            slot.read()
        } catch (_: Exception) {
            return recovery(DatabaseKeyResult.Reason.STORAGE_ERROR)
        }
        return if (encoded == null) create() else unwrap(encoded)
    }

    private fun create(): DatabaseKeyResult {
        val plaintext = try {
            byteSource.next(PASSPHRASE_SIZE)
        } catch (_: Exception) {
            return recovery(DatabaseKeyResult.Reason.STORAGE_ERROR)
        }
        try {
            if (plaintext.size != PASSPHRASE_SIZE) {
                return recovery(DatabaseKeyResult.Reason.STORAGE_ERROR)
            }
            val encoded = try {
                codec.encode(wrapper.wrap(plaintext))
            } catch (exception: Exception) {
                return recovery(classifyCryptographicFailure(exception))
            }
            return try {
                slot.write(encoded)
                DatabaseKeyResult.Ready(plaintext.copyOf())
            } catch (_: Exception) {
                recovery(DatabaseKeyResult.Reason.STORAGE_ERROR)
            }
        } finally {
            plaintext.fill(0)
        }
    }

    private fun unwrap(encoded: String): DatabaseKeyResult {
        val envelope = try {
            codec.decode(encoded)
        } catch (_: IllegalArgumentException) {
            return recovery(DatabaseKeyResult.Reason.ENVELOPE_INVALID)
        } catch (_: Exception) {
            return recovery(DatabaseKeyResult.Reason.STORAGE_ERROR)
        }
        val plaintext = try {
            wrapper.unwrap(envelope)
        } catch (exception: Exception) {
            return recovery(classifyCryptographicFailure(exception))
        }
        try {
            if (plaintext.size != PASSPHRASE_SIZE) {
                return recovery(DatabaseKeyResult.Reason.ENVELOPE_INVALID)
            }
            return DatabaseKeyResult.Ready(plaintext.copyOf())
        } finally {
            plaintext.fill(0)
        }
    }

    private fun classifyCryptographicFailure(exception: Exception): DatabaseKeyResult.Reason = when (exception) {
        is UnrecoverableKeyException, is InvalidKeyException -> DatabaseKeyResult.Reason.KEY_MISSING
        is BadPaddingException -> DatabaseKeyResult.Reason.AUTHENTICATION_FAILED
        else -> DatabaseKeyResult.Reason.STORAGE_ERROR
    }

    private fun recovery(reason: DatabaseKeyResult.Reason): DatabaseKeyResult =
        DatabaseKeyResult.RecoveryRequired(reason)

    private companion object {
        const val PASSPHRASE_SIZE = 32
    }
}
