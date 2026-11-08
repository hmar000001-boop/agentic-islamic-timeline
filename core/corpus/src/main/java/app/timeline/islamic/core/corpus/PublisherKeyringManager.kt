package app.timeline.islamic.core.corpus

import app.timeline.islamic.core.knowledge.PublisherKeyringEnvelope
import app.timeline.islamic.core.knowledge.PublisherKeyringStore
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Root trust is injected by application build configuration. There is intentionally no default root key:
 * production imports remain disabled until a real publisher root has been reviewed and pinned.
 */
class PinnedPublisherRoot(rootKeyId: String, rawPublicKey: ByteArray) {
    val keyId: String = rootKeyId.also { require(it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))) }
    private val keyBytes = rawPublicKey.copyOf()
    val rawPublicKey: ByteArray get() = keyBytes.copyOf()
    init { require(keyBytes.size == 32) { "Pinned Ed25519 root key must be 32 bytes" } }
}

class PublisherKeyringException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Loads only root-verified snapshots and enforces monotonic keyring versions across app restarts. */
class PublisherKeyringManager(
    private val root: PinnedPublisherRoot,
    private val store: PublisherKeyringStore,
    private val verifier: PackSignatureVerifier,
    private val clock: () -> Long = System::currentTimeMillis
) : PublisherKeyring {
    private val current = AtomicReference<Map<String, TrustedPublisherKey>>(emptyMap())
    /** Serializes refresh/install so an older disk snapshot cannot overwrite a newer in-memory trust map. */
    private val updateMutex = Mutex()
    @Volatile private var currentVersion: Long = 0

    override fun find(keyId: String): TrustedPublisherKey? = current.get()[keyId]?.let { key ->
        if (key.isActiveAt(clock())) key else null
    }

    fun version(): Long = currentVersion
    fun isReady(): Boolean = current.get().values.any { it.isActiveAt(clock()) }

    suspend fun refresh(): Long = updateMutex.withLock {
        // Fail closed while the persisted envelope is being revalidated.
        current.set(emptyMap())
        currentVersion = 0
        val envelope = store.read() ?: run {
            current.set(emptyMap())
            currentVersion = 0
            return@withLock 0L
        }
        val document = verifyEnvelope(envelope.payload, envelope.signature, expectedVersion = envelope.version)
        publish(document)
        document.version
    }

    suspend fun install(payload: ByteArray, signature: ByteArray): Long = updateMutex.withLock {
        if (payload.size !in 1..MAX_KEYRING_BYTES || signature.size != SIGNATURE_BYTES) {
            throw PublisherKeyringException("Publisher keyring archive has invalid payload/signature size")
        }
        val document = try { verifyEnvelope(payload, signature, expectedVersion = null) }
        catch (e: Exception) { throw PublisherKeyringException("Publisher keyring verification failed", e) }
        val now = clock()
        if (document.generatedAtEpochMillis > now) throw PublisherKeyringException("Publisher keyring is dated in the future")
        val previousEnvelope = store.read()
        val previousDocument = previousEnvelope?.let {
            try { verifyEnvelope(it.payload, it.signature, expectedVersion = it.version) }
            catch (e: Exception) { throw PublisherKeyringException("Existing publisher keyring is invalid; refusing trust update", e) }
        }
        val expectedVersion = previousEnvelope?.version ?: 0L
        if (document.version <= expectedVersion) throw PublisherKeyringException("Publisher keyring rollback rejected")
        if (previousDocument != null) validateTrustTransition(previousDocument, document)
        val envelope = PublisherKeyringEnvelope(document.version, payload, signature, now)
        val written = try { store.writeIfCurrentVersion(expectedVersion, envelope) } catch (e: Exception) {
            throw PublisherKeyringException("Unable to persist publisher keyring", e)
        }
        if (!written) throw PublisherKeyringException("Concurrent publisher keyring update rejected; reload trust state and retry")
        publish(document)
        document.version
    }

    private fun verifyEnvelope(payload: ByteArray, signature: ByteArray, expectedVersion: Long?): PublisherKeyringDocument {
        val document = PublisherKeyringCodec.parse(payload)
        if (expectedVersion != null && document.version != expectedVersion) {
            throw PublisherKeyringException("Stored publisher keyring version does not match signed payload")
        }
        if (document.generatedAtEpochMillis > clock()) throw PublisherKeyringException("Publisher keyring is dated in the future")
        if (!verifier.verify(payload, signature, root.rawPublicKey)) {
            throw PublisherKeyringException("Publisher keyring does not verify under pinned root '${root.keyId}'")
        }
        return document
    }

    private fun validateTrustTransition(previous: PublisherKeyringDocument, incoming: PublisherKeyringDocument) {
        if (incoming.generatedAtEpochMillis < previous.generatedAtEpochMillis) {
            throw PublisherKeyringException("Publisher keyring generation time cannot move backwards")
        }
        val nextById = incoming.publishers.associateBy { it.keyId }
        previous.publishers.forEach { old ->
            val next = nextById[old.keyId] ?: throw PublisherKeyringException("Publisher key '${old.keyId}' cannot be removed from key history")
            if (!old.rawPublicKey.contentEquals(next.rawPublicKey) || old.notBeforeEpochMillis != next.notBeforeEpochMillis) {
                throw PublisherKeyringException("Publisher key id '${old.keyId}' cannot be rebound")
            }
            if (old.revokedAtEpochMillis != null && next.revokedAtEpochMillis != old.revokedAtEpochMillis) {
                throw PublisherKeyringException("Publisher key revocation for '${old.keyId}' is immutable")
            }
        }
    }

    private fun publish(document: PublisherKeyringDocument) {
        if (document.version < currentVersion) return
        current.set(document.publishers.associate { record ->
            record.keyId to TrustedPublisherKey(
                record.keyId, record.rawPublicKey, record.notBeforeEpochMillis, record.revokedAtEpochMillis
            )
        })
        currentVersion = document.version
    }

    private companion object { const val MAX_KEYRING_BYTES = 256 * 1024; const val SIGNATURE_BYTES = 64 }
}
