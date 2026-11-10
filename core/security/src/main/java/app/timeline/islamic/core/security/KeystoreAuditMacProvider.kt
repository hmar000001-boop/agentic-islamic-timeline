package app.timeline.islamic.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

class AuditKeyUnavailableException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/** HMAC key is hardware/Android-Keystore managed where supported; it is never exported. */
class KeystoreAuditMacProvider(
    context: Context,
    private val keyAlias: String = DEFAULT_ALIAS
) : app.timeline.islamic.core.agent.AuditMacProvider {
    private val appContext = context.applicationContext

    override fun computeMac(message: ByteArray, allowCreate: Boolean): ByteArray {
        return try {
            val key = getKey(allowCreate)
            Mac.getInstance(HMAC_ALGORITHM).run {
                init(key)
                doFinal(message)
            }
        } catch (error: AuditKeyUnavailableException) {
            throw error
        } catch (error: Exception) {
            throw AuditKeyUnavailableException("Audit integrity key is unavailable; audit writes are blocked.", error)
        }
    }

    private fun getKey(allowCreate: Boolean): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        if (!allowCreate) {
            throw AuditKeyUnavailableException("Audit Keystore key is missing or invalidated; refusing to reset the audit chain.")
        }
        val generator = KeyGenerator.getInstance(HMAC_ALGORITHM, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val HMAC_ALGORITHM = "HmacSHA256"
        const val DEFAULT_ALIAS = "app.timeline.islamic.audit.hmac.v1"
    }
}
