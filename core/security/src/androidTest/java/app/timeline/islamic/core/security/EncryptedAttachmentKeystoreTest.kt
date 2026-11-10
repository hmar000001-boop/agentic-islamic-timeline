package app.timeline.islamic.core.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedAttachmentKeystoreTest {
    private lateinit var context: Context
    private lateinit var alias: String
    private lateinit var directory: File
    private lateinit var store: EncryptedAttachmentStore

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alias = "app.timeline.test.attachment.${UUID.randomUUID()}"
        directory = File(context.cacheDir, "attachment-keystore-test-${UUID.randomUUID()}").apply {
            check(mkdirs())
        }
        store = EncryptedAttachmentStore(directory, KeystoreAttachmentEncryptionKeyProvider(alias))
    }

    @After fun tearDown() {
        if (::alias.isInitialized) {
            runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) } }
        }
        if (::directory.isInitialized) directory.deleteRecursively()
    }

    @Test fun keystoreKeySurvivesStoreRecreationAndAuthenticatesContent(): Unit {
        val content = "Keystore-backed attachment / ملف خاص".toByteArray(Charsets.UTF_8)
        val metadata = store.encrypt("keystore-roundtrip", ByteArrayInputStream(content))
        val reopened = EncryptedAttachmentStore(directory, KeystoreAttachmentEncryptionKeyProvider(alias))
        val restored = reopened.withDecryptedAttachment("keystore-roundtrip", metadata.sizeBytes, metadata.sha256) {
            it.readBytes()
        }
        assertArrayEquals(content, restored)
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue(keyStore.containsAlias(alias))
    }

    @Test fun missingKeystoreKeyIsNotRecreatedWhileCiphertextExists(): Unit {
        val metadata = store.encrypt("keystore-key-loss", ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)))
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue(keyStore.containsAlias(alias))
        keyStore.deleteEntry(alias)

        val reopened = EncryptedAttachmentStore(directory, KeystoreAttachmentEncryptionKeyProvider(alias))
        assertTrue(runCatching {
            reopened.withDecryptedAttachment("keystore-key-loss", metadata.sizeBytes, metadata.sha256) { it.readBytes() }
        }.exceptionOrNull() is EncryptedAttachmentException)
        assertTrue(runCatching {
            reopened.encrypt("second-file", ByteArrayInputStream(byteArrayOf(8, 9)))
        }.exceptionOrNull() is EncryptedAttachmentException)

        val afterFailure = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertFalse("A replacement key must not be generated for existing ciphertext", afterFailure.containsAlias(alias))
        assertTrue(File(directory, "keystore-key-loss.tla").exists())
    }
}
