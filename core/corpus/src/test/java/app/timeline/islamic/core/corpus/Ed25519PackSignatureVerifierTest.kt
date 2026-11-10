package app.timeline.islamic.core.corpus

import org.junit.Assert.*
import org.junit.Test

class Ed25519PackSignatureVerifierTest {
    @Test fun rfc8032EmptyMessageVectorVerifiesAndTamperingFails() {
        val publicKey = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        val signature = hex("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155" +
            "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b")
        val verifier = BouncyCastleEd25519Verifier()
        assertTrue(verifier.verify(byteArrayOf(), signature, publicKey))
        assertFalse(verifier.verify(byteArrayOf(), signature, ByteArray(32)))
        assertFalse(verifier.verify(byteArrayOf(), ByteArray(63), publicKey))
        signature[0] = (signature[0].toInt() xor 1).toByte()
        assertFalse(verifier.verify(byteArrayOf(), signature, publicKey))
    }

    @Test fun trustedKeyDefensivelyCopiesKeyBytesAndHonorsActivationAndRevocation() {
        val source = ByteArray(32) { 7 }
        val key = TrustedPublisherKey("test-key", source, 100, 200)
        source[0] = 9
        assertEquals(7, key.rawPublicKey[0].toInt())
        val returned = key.rawPublicKey
        returned[0] = 10
        assertEquals(7, key.rawPublicKey[0].toInt())
        assertFalse(key.isActiveAt(99))
        assertTrue(key.isActiveAt(100))
        assertTrue(key.isActiveAt(199))
        assertFalse(key.isActiveAt(200))
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
