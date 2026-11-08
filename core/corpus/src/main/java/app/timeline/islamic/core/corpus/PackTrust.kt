package app.timeline.islamic.core.corpus

import org.bouncycastle.math.ec.rfc8032.Ed25519

/** Public key bytes are the 32-byte raw Ed25519 encoding, not an X.509 SubjectPublicKeyInfo blob. */
class TrustedPublisherKey(
    val keyId: String,
    rawPublicKey: ByteArray,
    val notBeforeEpochMillis: Long,
    val revokedAtEpochMillis: Long? = null
) {
    private val encodedPublicKey = rawPublicKey.copyOf()
    /** Defensive copy: keyring callers cannot mutate the trusted key held by the application. */
    val rawPublicKey: ByteArray get() = encodedPublicKey.copyOf()

    init {
        require(keyId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))) { "Invalid publisher key id" }
        require(encodedPublicKey.size == Ed25519.PUBLIC_KEY_SIZE) { "Ed25519 public keys must be 32 bytes" }
        require(notBeforeEpochMillis > 0)
        require(revokedAtEpochMillis == null || revokedAtEpochMillis >= notBeforeEpochMillis) {
            "Publisher key revocation time precedes activation"
        }
    }

    fun isActiveAt(epochMillis: Long): Boolean =
        epochMillis >= notBeforeEpochMillis && (revokedAtEpochMillis == null || epochMillis < revokedAtEpochMillis)
}

fun interface PublisherKeyring {
    fun find(keyId: String): TrustedPublisherKey?
}

fun interface PackSignatureVerifier {
    fun verify(message: ByteArray, signature: ByteArray, rawPublicKey: ByteArray): Boolean
}

/** Bouncy Castle lightweight Ed25519 avoids depending on Android provider availability. */
class BouncyCastleEd25519Verifier : PackSignatureVerifier {
    override fun verify(message: ByteArray, signature: ByteArray, rawPublicKey: ByteArray): Boolean {
        if (signature.size != Ed25519.SIGNATURE_SIZE || rawPublicKey.size != Ed25519.PUBLIC_KEY_SIZE) return false
        return try {
            Ed25519.validatePublicKeyFull(rawPublicKey, 0) &&
                Ed25519.verify(signature, 0, rawPublicKey, 0, message, 0, message.size)
        } catch (_: RuntimeException) {
            false
        }
    }
}
