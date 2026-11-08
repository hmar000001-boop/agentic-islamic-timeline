package app.timeline.islamic.core.corpus

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Ordered startup lifecycle: keyring trust → pending removals → orphan cleanup → pack revalidation. */
class CorpusPackRuntime(
    private val keyring: PublisherKeyringManager,
    private val installer: CorpusPackInstaller,
    private val lifecycle: CorpusPackLifecycleManager,
    private val revalidator: InstalledCorpusPackRevalidator
) {
    private val mutex = Mutex()
    @Volatile private var completed: StartupReport? = null

    data class StartupReport(
        val keyringVersion: Long,
        val keyringReady: Boolean,
        val keyringFailureCode: String?,
        val removalsRecovered: Int,
        val orphanDirectoriesRemoved: Int,
        val revalidation: InstalledCorpusPackRevalidator.Report
    )

    suspend fun initialize(): StartupReport = mutex.withLock {
        completed?.let { return@withLock it }
        runIntegrityPass(refreshKeyring = true).also { completed = it }
    }

    /** Call after a user imports a newer root-signed keyring so quarantined packs can be revalidated. */
    suspend fun revalidateAfterTrustUpdate(): StartupReport = mutex.withLock {
        runIntegrityPass(refreshKeyring = false).also { completed = it }
    }

    private suspend fun runIntegrityPass(refreshKeyring: Boolean): StartupReport {
        var keyringFailure: String? = null
        val version = if (refreshKeyring) try { keyring.refresh() } catch (failure: Exception) {
            // Manager clears its in-memory snapshot before checking persisted signatures. Pack checks below
            // therefore quarantine any pack which no longer has an independently verified active publisher.
            keyringFailure = failure::class.simpleName ?: "KEYRING_INVALID"
            0L
        } else keyring.version()
        val removals = lifecycle.recoverPendingRemovals()
        val orphans = installer.removeUnregisteredPackDirectories()
        val integrity = revalidator.revalidateAll()
        return StartupReport(version, keyring.isReady(), keyringFailure, removals, orphans, integrity)
    }
}
