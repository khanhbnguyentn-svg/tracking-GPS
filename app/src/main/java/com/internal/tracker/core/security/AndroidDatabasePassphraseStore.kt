package com.internal.tracker.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidDatabasePassphraseStore internal constructor(
    private val delegate: DatabasePassphraseStore,
    private val beforeLockAcquisition: () -> Unit = {},
) : DatabasePassphraseStore {
    constructor(context: Context) : this(createDelegate(context.applicationContext))

    override fun getOrCreate(): DatabaseKeyResult {
        beforeLockAcquisition()
        return synchronized(PROCESS_LOCK) {
            delegate.getOrCreate()
        }
    }

    private companion object {
        val PROCESS_LOCK = Any()

        fun createDelegate(context: Context): DatabasePassphraseStore {
            val secureRandom = SecureRandom()
            return DatabasePassphraseManager(
                slot = SharedPreferencesPassphraseEnvelopeSlot(context),
                wrapper = AndroidKeystorePassphraseWrapper(secureRandom),
                byteSource = SecureByteSource { size -> ByteArray(size).also(secureRandom::nextBytes) },
                codec = PassphraseEnvelopeCodec(),
            )
        }
    }
}

private class SharedPreferencesPassphraseEnvelopeSlot(context: Context) : PassphraseEnvelopeSlot {
    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
    }

    override fun read(): String? = preferences.getString(ENVELOPE_KEY, null)

    override fun write(encoded: String) {
        check(preferences.edit().putString(ENVELOPE_KEY, encoded).commit()) {
            "Unable to persist wrapped database passphrase"
        }
    }

    private companion object {
        const val PREFERENCES_FILE = "android-set-database-passphrase"
        const val ENVELOPE_KEY = "wrapped-passphrase-v1"
    }
}

private class AndroidKeystorePassphraseWrapper(
    private val secureRandom: SecureRandom,
) : PassphraseWrapper {
    override fun wrap(plaintext: ByteArray): PassphraseEnvelope {
        val keyStore = loadKeyStore()
        val key = if (keyStore.containsAlias(KEY_ALIAS)) {
            requireExistingKey(keyStore)
        } else {
            generateKey()
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, secureRandom)
        val iv = cipher.iv
        try {
            check(iv.size == IV_SIZE) { "Android Keystore returned an invalid GCM IV" }
            val ciphertext = cipher.doFinal(plaintext)
            try {
                return PassphraseEnvelope(ENVELOPE_VERSION, iv, ciphertext)
            } finally {
                ciphertext.fill(0)
            }
        } finally {
            iv.fill(0)
        }
    }

    override fun unwrap(envelope: PassphraseEnvelope): ByteArray {
        val key = requireExistingKey(loadKeyStore())
        val iv = envelope.iv
        val ciphertext = envelope.ciphertext
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(AUTHENTICATION_TAG_BITS, iv))
            return cipher.doFinal(ciphertext)
        } finally {
            iv.fill(0)
            ciphertext.fill(0)
        }
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    private fun requireExistingKey(keyStore: KeyStore): SecretKey {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            throw UnrecoverableKeyException("Database wrapping key is missing")
        }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw UnrecoverableKeyException("Database wrapping key is missing")
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "android-set-db-wrap-v1"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val IV_SIZE = 12
        const val AUTHENTICATION_TAG_BITS = 128
        const val ENVELOPE_VERSION = 1
    }
}
