package app.timeline.islamic.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DatabaseKeyUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Creates/retrieves the SQLCipher passphrase. Never replaces a missing key for an existing DB. */
class DatabaseKeyManager(
    context: Context,
    databaseName: String = DATABASE_NAME,
    envelopeName: String = ENVELOPE_NAME,
    private val keyAlias: String = KEY_ALIAS
) {
    private val appContext = context.applicationContext
    private val envelopeFile = File(appContext.noBackupFilesDir, envelopeName)
    private val databaseFile = appContext.getDatabasePath(databaseName)

    @Synchronized
    @Throws(DatabaseKeyUnavailableException::class)
    fun getOrCreatePassphrase(): ByteArray {
        if (envelopeFile.exists()) {
            if (envelopeFile.length() !in MIN_ENVELOPE_BYTES.toLong()..MAX_ENVELOPE_BYTES.toLong()) {
                throw DatabaseKeyUnavailableException("Database key envelope has an invalid size. The database was not reset.")
            }
            return decryptEnvelope(envelopeFile.readBytes())
        }
        if (databaseFile.exists()) {
            throw DatabaseKeyUnavailableException("Database exists but its non-backed-up key envelope is missing. Recovery is required; no replacement key was generated.")
        }
        return try {
            val passphrase = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
            persistEnvelope(encryptEnvelope(passphrase))
            passphrase
        } catch (e: Exception) {
            throw DatabaseKeyUnavailableException("Unable to create the encrypted database-key envelope.", e)
        }
    }

    private fun persistEnvelope(bytes: ByteArray) {
        val parent = envelopeFile.parentFile ?: error("Missing key-envelope parent directory")
        check(parent.exists() || parent.mkdirs()) { "Cannot create key-envelope directory" }
        val temporary = File(parent, "${envelopeFile.name}.tmp")
        FileOutputStream(temporary).use { stream -> stream.write(bytes); stream.fd.sync() }
        if (envelopeFile.exists() && !envelopeFile.delete()) error("Cannot replace old key envelope")
        if (!temporary.renameTo(envelopeFile)) {
            temporary.delete()
            error("Cannot atomically activate database-key envelope")
        }
    }

    private fun encryptEnvelope(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return MAGIC + byteArrayOf(iv.size.toByte()) + iv + ciphertext
    }

    private fun decryptEnvelope(encoded: ByteArray): ByteArray {
        try {
            require(encoded.size > MAGIC.size + 1 + GCM_IV_BYTES)
            require(encoded.copyOfRange(0, MAGIC.size).contentEquals(MAGIC))
            val ivSize = encoded[MAGIC.size].toInt() and 0xff
            require(ivSize == GCM_IV_BYTES)
            val ivStart = MAGIC.size + 1
            val iv = encoded.copyOfRange(ivStart, ivStart + ivSize)
            val ciphertext = encoded.copyOfRange(ivStart + ivSize, encoded.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getExistingWrappingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            return cipher.doFinal(ciphertext).also { require(it.size == PASSPHRASE_BYTES) }
        } catch (e: Exception) {
            throw DatabaseKeyUnavailableException("Database key envelope cannot be authenticated or decrypted. The database was not reset.", e)
        }
    }

    private fun getExistingWrappingKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return store.getKey(keyAlias, null) as? SecretKey
            ?: throw DatabaseKeyUnavailableException("Android Keystore wrapping key is missing or was invalidated.")
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val DATABASE_NAME = "timeline-user.db"
        private const val ENVELOPE_NAME = "database-key.envelope"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "app.timeline.islamic.database.wrap.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PASSPHRASE_BYTES = 32
        private const val MIN_ENVELOPE_BYTES = 33
        private const val MAX_ENVELOPE_BYTES = 128
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private val MAGIC = byteArrayOf(0x54, 0x4c, 0x4b, 0x31) // TLK1
    }
}
