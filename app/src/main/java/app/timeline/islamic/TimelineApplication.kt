package app.timeline.islamic

import android.app.Application
import android.os.PowerManager
import android.net.Uri
import android.util.Base64
import app.timeline.islamic.core.agent.AuditVerification
import app.timeline.islamic.core.agent.TaskLease
import app.timeline.islamic.core.agent.ExecutionClaimResult
import app.timeline.islamic.core.agent.UserConfirmation
import app.timeline.islamic.core.agent.runtime.AgentStepProcessor
import app.timeline.islamic.core.agent.runtime.AgentRunResult
import app.timeline.islamic.core.agent.runtime.AgentRecoveryReport
import app.timeline.islamic.core.agent.runtime.AgentOrchestrator
import app.timeline.islamic.core.agent.runtime.AgentRecoveryCoordinator
import app.timeline.islamic.core.authorization.AuthorizationEngine
import app.timeline.islamic.core.authorization.ApprovedToolHandler
import app.timeline.islamic.core.authorization.ToolExecutionGateway
import app.timeline.islamic.core.persistence.EncryptedDatabaseFactory
import app.timeline.islamic.core.persistence.RoomAgentTaskStore
import app.timeline.islamic.core.persistence.RoomAuditLog
import app.timeline.islamic.core.persistence.RoomAuthorizationStore
import app.timeline.islamic.core.security.KeystoreAuditMacProvider
import app.timeline.islamic.core.corpus.BouncyCastleEd25519Verifier
import app.timeline.islamic.core.corpus.CorpusPackInstaller
import app.timeline.islamic.core.corpus.CorpusPackLifecycleManager
import app.timeline.islamic.core.corpus.CorpusPackRuntime
import app.timeline.islamic.core.corpus.InstalledCorpusPackRevalidator
import app.timeline.islamic.core.corpus.PinnedPublisherRoot
import app.timeline.islamic.core.corpus.PublisherKeyringManager
import app.timeline.islamic.core.corpus.PublisherKeyring
import app.timeline.islamic.core.corpus.SafCorpusPackImporter
import app.timeline.islamic.core.corpus.SafPublisherKeyringImporter
import app.timeline.islamic.core.corpus.SignedPublisherKeyringArchiveImporter
import app.timeline.islamic.core.persistence.RoomCorpusPackRegistry
import app.timeline.islamic.core.persistence.RoomPublisherKeyringStore
import app.timeline.islamic.core.persistence.RoomDenseEmbeddingIndexStore
import app.timeline.islamic.core.retrieval.BundledSqliteCorpusIndex
import app.timeline.islamic.core.retrieval.LocalEmbeddingIndexBuilder
import app.timeline.islamic.core.retrieval.LocalCorpusRetriever
import app.timeline.islamic.core.retrieval.LocalDenseRetriever
import app.timeline.islamic.core.retrieval.StoredDenseIndexRetriever
import app.timeline.islamic.core.retrieval.RetrievalResponse
import app.timeline.islamic.core.models.AndroidDeviceResourceProbe
import app.timeline.islamic.core.models.AndroidModelFileStore
import app.timeline.islamic.core.models.ModelCatalog
import app.timeline.islamic.core.models.ModelManager
import app.timeline.islamic.core.models.ModelPreflightDecision
import app.timeline.islamic.core.models.RoomModelRegistry
import app.timeline.islamic.core.models.SafModelArtifactImporter
import app.timeline.islamic.core.inference.LiteRtCpuGenerationRuntime
import app.timeline.islamic.core.inference.LiteRtEmbeddingTextModel
import app.timeline.islamic.core.inference.GenerationRuntimeSnapshot
import app.timeline.islamic.core.inference.GenerationRuntimeState
import app.timeline.islamic.feature.models.ModelManagementUiState
import app.timeline.islamic.feature.models.ModelUiItem
import app.timeline.islamic.core.knowledge.InstalledCorpusPackRecord
import app.timeline.islamic.core.knowledge.LocalTextEmbeddingModel
import app.timeline.islamic.core.knowledge.RetrievalRequest
import java.io.File
import app.timeline.islamic.feature.corpus.CorpusManagementUiState
import app.timeline.islamic.feature.corpus.CorpusPackUiItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TimelineApplication : Application() {
    @Volatile var agentRuntimeReady: Boolean = false
        private set
    @Volatile var agentRuntimeFailureCode: String? = null
        private set

    // Activity recreation must not run recovery a second time while a live process is working.
    private val agentInitializationMutex = Mutex()
    @Volatile private var completedRecoveryReport: AgentRecoveryReport? = null

    // Lazy access keeps UI startup independent from a model; storage errors remain explicit.
    val database by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { EncryptedDatabaseFactory.get(this) }
    private val taskStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomAgentTaskStore(database) }
    private val authorizationStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomAuthorizationStore(database, KeystoreAuditMacProvider(this)) }
    private val authorizationEngine by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AuthorizationEngine(authorizationStore) }
    private val auditLog by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomAuditLog(database, KeystoreAuditMacProvider(this))
    }
    private val agentOrchestrator by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AgentOrchestrator(taskStore, authorizationEngine)
    }
    private val agentRecovery by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AgentRecoveryCoordinator(taskStore, authorizationStore)
    }

    private data class PublisherRootConfiguration(val root: PinnedPublisherRoot?, val invalid: Boolean)

    /** No default publisher key exists; an absent public root disables importing without quarantining packs. */
    private val publisherRootConfiguration by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            val keyId = BuildConfig.CORPUS_PUBLISHER_ROOT_KEY_ID.trim()
            val encoded = BuildConfig.CORPUS_PUBLISHER_ROOT_KEY_B64.trim()
            if (keyId.isEmpty() && encoded.isEmpty()) PublisherRootConfiguration(null, false)
            else {
                require(keyId.isNotEmpty() && encoded.isNotEmpty()) { "Publisher root key configuration is incomplete" }
                val rawKey = Base64.decode(encoded, Base64.DEFAULT)
                PublisherRootConfiguration(PinnedPublisherRoot(keyId, rawKey), false)
            }
        }.getOrElse { PublisherRootConfiguration(null, true) }
    }
    private val corpusSignatureVerifier by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { BouncyCastleEd25519Verifier() }
    private val corpusRegistry by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { RoomCorpusPackRegistry(database) }
    private val corpusLifecycle by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { CorpusPackLifecycleManager(this, corpusRegistry) }
    private val publisherKeyringManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        publisherRootConfiguration.root?.let { root ->
            PublisherKeyringManager(root, RoomPublisherKeyringStore(database), corpusSignatureVerifier)
        }
    }
    private val corpusKeyring: PublisherKeyring by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        publisherKeyringManager ?: PublisherKeyring { null }
    }
    private val corpusIndex by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { BundledSqliteCorpusIndex() }
    /** Embeddings are derived text data and therefore stay inside the SQLCipher Room database. */
    private val denseEmbeddingIndexStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomDenseEmbeddingIndexStore(database)
    }
    // Model artifacts are stored only under noBackupFilesDir and their metadata remains in SQLCipher Room.
    private val modelManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ModelManager(RoomModelRegistry(database), AndroidModelFileStore(noBackupFilesDir))
    }
    private val safModelImporter by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SafModelArtifactImporter(this, modelManager)
    }
    private val modelResourceProbe by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidDeviceResourceProbe(this)
    }
    /** Serializes generation and embedding native engines: never keep both large weights resident. */
    private val inferenceLifecycleMutex = Mutex()
    private val generationRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LiteRtCpuGenerationRuntime(modelManager, File(cacheDir, "litertlm"))
    }
    private val corpusInstaller by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CorpusPackInstaller(this, corpusKeyring, corpusSignatureVerifier, corpusIndex, corpusRegistry)
    }
    private val corpusRevalidator by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        publisherKeyringManager?.let { manager ->
            InstalledCorpusPackRevalidator(this, corpusRegistry, manager, corpusSignatureVerifier, corpusIndex)
        }
    }
    private val corpusRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val manager = publisherKeyringManager
        val revalidator = corpusRevalidator
        if (manager != null && revalidator != null) {
            CorpusPackRuntime(manager, corpusInstaller, corpusLifecycle, revalidator)
        } else null
    }

    /** Must complete before any agent work is scheduled or an action executor is exposed. */
    suspend fun initializeAgentRuntime(): AgentRecoveryReport = agentInitializationMutex.withLock {
        completedRecoveryReport?.let {
            agentRuntimeReady = true
            return@withLock it
        }
        agentRuntimeReady = false
        agentRuntimeFailureCode = null
        try {
            when (val verification = auditLog.verify()) {
                is AuditVerification.Valid -> Unit
                is AuditVerification.Invalid -> error("Audit integrity verification failed: ${verification.reason}")
            }
            agentRecovery.reconcile().also {
                completedRecoveryReport = it
                agentRuntimeReady = true
            }
        } catch (failure: Exception) {
            // Do not log private task/proposal content or silently reset the audit root.
            // A failed attempt is not cached, so a later explicit retry can re-verify/reconcile.
            agentRuntimeFailureCode = failure::class.simpleName ?: "AGENT_RECOVERY_FAILED"
            throw failure
        }
    }

    /** The only app-level entry point for advancing an agent task. Recovery must pass first. */
    suspend fun runAgentStep(taskId: String, workerId: String, processor: AgentStepProcessor): AgentRunResult {
        check(agentRuntimeReady) { "Agent runtime is not initialized; recovery/integrity gate has not passed" }
        return agentOrchestrator.runOneStep(taskId, workerId, processor)
    }

    suspend fun approveAgentProposal(confirmation: UserConfirmation): Boolean {
        check(agentRuntimeReady) { "Agent runtime is not initialized" }
        return authorizationEngine.approve(confirmation)
    }

    suspend fun rejectAgentProposal(proposalId: String, proposalSha256: String): Boolean {
        check(agentRuntimeReady) { "Agent runtime is not initialized" }
        return authorizationEngine.reject(proposalId, proposalSha256)
    }

    suspend fun claimAgentAction(
        proposalId: String, proposalSha256: String, idempotencyKey: String, lease: TaskLease
    ): ExecutionClaimResult {
        check(agentRuntimeReady) { "Agent runtime is not initialized" }
        return authorizationEngine.claimExecution(proposalId, proposalSha256, idempotencyKey, lease)
    }

    fun createToolExecutionGateway(handlers: Map<String, ApprovedToolHandler>): ToolExecutionGateway {
        check(agentRuntimeReady) { "Agent runtime is not initialized" }
        return ToolExecutionGateway(authorizationEngine, handlers)
    }

    /** Initializes trusted corpus lifecycle only when a root key was actually pinned in the build. */
    suspend fun loadCorpusManagementState(): CorpusManagementUiState {
        return try {
            val root = publisherRootConfiguration
            var summary: String? = null
            denseEmbeddingIndexStore.recoverAbandonedGenerations()
            if (root.root != null) {
                val report = corpusRuntime?.initialize()
                if (report != null) {
                    summary = "Checked ${report.revalidation.checked}; healthy ${report.revalidation.healthy}; quarantined ${report.revalidation.quarantined}; restored ${report.revalidation.restored}."
                }
            } else {
                // Without a pinned publisher root, do recovery that does not require trust decisions,
                // but never run the revalidator and accidentally quarantine every existing pack.
                corpusLifecycle.recoverPendingRemovals()
                corpusInstaller.removeUnregisteredPackDirectories()
            }
            cleanupDenseIndexesForInactivePacks()
            val manager = publisherKeyringManager
            val embeddingSpec = ModelCatalog.require(LiteRtEmbeddingTextModel.EMBEDDING_MODEL_ID)
            val embeddingModelInstalled = modelManager.installedModels().any {
                it.modelId == embeddingSpec.id && it.revision == embeddingSpec.revision &&
                    it.status == app.timeline.islamic.core.models.ModelLifecycleStatus.INSTALLED
            }
            val embeddingDescriptor = LiteRtEmbeddingTextModel.descriptorForCatalog()
            val packs = corpusRegistry.listAll().map { record ->
                val generation = if (manager?.isReady() == true && embeddingModelInstalled &&
                    record.status == InstalledCorpusPackRecord.STATUS_INSTALLED
                ) denseEmbeddingIndexStore.activeGeneration(record, embeddingDescriptor) else null
                CorpusPackUiItem(
                    record.packId, record.version, record.status, record.licenseProfile, record.manifestSha256,
                    denseIndexReady = generation != null,
                    denseIndexVectorCount = generation?.vectorCount ?: 0L
                )
            }.sortedWith(compareBy(CorpusPackUiItem::packId, CorpusPackUiItem::version))
            CorpusManagementUiState(
                loading = false,
                publisherRootConfigured = root.root != null,
                publisherRootConfigurationInvalid = root.invalid,
                keyringReady = manager?.isReady() == true,
                keyringVersion = manager?.version() ?: 0L,
                packCount = packs.size,
                packs = packs,
                lastRevalidationSummary = summary,
                embeddingModelInstalled = embeddingModelInstalled
            )
        } catch (failure: Exception) {
            CorpusManagementUiState(
                loading = false,
                publisherRootConfigured = publisherRootConfiguration.root != null,
                publisherRootConfigurationInvalid = publisherRootConfiguration.invalid,
                keyringReady = publisherKeyringManager?.isReady() == true,
                keyringVersion = publisherKeyringManager?.version() ?: 0L,
                packs = runCatching { corpusRegistry.listAll().map { CorpusPackUiItem(it.packId, it.version, it.status, it.licenseProfile, it.manifestSha256) } }.getOrDefault(emptyList()),
                embeddingModelInstalled = runCatching {
                    val spec = ModelCatalog.require(LiteRtEmbeddingTextModel.EMBEDDING_MODEL_ID)
                    modelManager.installedModels().any {
                        it.modelId == spec.id && it.revision == spec.revision &&
                            it.status == app.timeline.islamic.core.models.ModelLifecycleStatus.INSTALLED
                    }
                }.getOrDefault(false),
                message = failure::class.simpleName ?: "CORPUS_INITIALIZATION_FAILED"
            )
        }
    }

    suspend fun importPublisherKeyring(uri: Uri): Long {
        val manager = publisherKeyringManager ?: error("A valid pinned publisher root is not configured")
        val importer = SignedPublisherKeyringArchiveImporter(manager)
        val version = SafPublisherKeyringImporter(this, importer).import(uri)
        corpusRuntime?.revalidateAfterTrustUpdate()
        cleanupDenseIndexesForInactivePacks()
        return version
    }

    suspend fun importCorpusPack(uri: Uri) {
        check(publisherKeyringManager?.isReady() == true) { "A verified publisher keyring is required before pack import" }
        SafCorpusPackImporter(this, corpusInstaller).import(uri)
        cleanupDenseIndexesForInactivePacks()
    }

    suspend fun updateCorpusPack(uri: Uri, packId: String, expectedCurrentVersion: String) {
        check(publisherKeyringManager?.isReady() == true) { "A verified publisher keyring is required before pack update" }
        val activeBefore = corpusRegistry.listInstalled().singleOrNull { it.packId == packId && it.version == expectedCurrentVersion }
            ?: error("Expected active corpus pack version is no longer installed")
        SafCorpusPackImporter(this, corpusInstaller).update(uri, expectedCurrentVersion)
        denseEmbeddingIndexStore.removePackVersion(activeBefore.packId, activeBefore.version)
        cleanupDenseIndexesForInactivePacks()
    }

    suspend fun removeCorpusPack(packId: String, version: String): Boolean {
        val removed = corpusLifecycle.remove(packId, version)
        if (removed) denseEmbeddingIndexStore.removePackVersion(packId, version)
        return removed
    }


    /** Dense vectors are derived from corpus text and may only remain for active trusted packs. */
    private suspend fun cleanupDenseIndexesForInactivePacks() {
        corpusRegistry.listAll()
            .filter { it.status != InstalledCorpusPackRecord.STATUS_INSTALLED }
            .forEach { record -> denseEmbeddingIndexStore.removePackVersion(record.packId, record.version) }
    }

    suspend fun buildLocalEmbeddingIndex(packId: String, version: String, model: LocalTextEmbeddingModel): LocalEmbeddingIndexBuilder.Result {
        require(publisherKeyringManager?.isReady() == true) { "A verified publisher keyring is required before indexing corpus passages" }
        corpusRuntime?.initialize()
        val record = corpusRegistry.listInstalled().singleOrNull { it.packId == packId && it.version == version }
            ?: error("The requested corpus pack version is not installed and active")
        val indexFile = File(File(filesDir, "corpus-packs"), record.relativeIndexPath)
        return LocalEmbeddingIndexBuilder(corpusIndex, denseEmbeddingIndexStore).build(record, indexFile, model)
    }

    /**
     * Evidence-first local retrieval. FTS5 remains available without an embedding artifact; when
     * the catalog-pinned EmbeddingGemma artifact is installed, the query is embedded locally and
     * fused with lexical candidates by RRF. No network provider or model-generated evidence is used.
     */
    suspend fun retrieveCorpus(request: RetrievalRequest): RetrievalResponse = inferenceLifecycleMutex.withLock {
        check(publisherKeyringManager?.isReady() == true) {
            "A verified publisher keyring is required before corpus retrieval"
        }
        corpusRuntime?.initialize()
        val installedPacks = corpusRegistry.listInstalled()
        val packsRoot = File(filesDir, "corpus-packs")
        val lexicalOnly = { dense: LocalDenseRetriever? ->
            LocalCorpusRetriever(packsRoot, corpusIndex, denseRetriever = dense)
        }
        val spec = ModelCatalog.require(LiteRtEmbeddingTextModel.EMBEDDING_MODEL_ID)
        val installedModel = modelManager.installedModels().singleOrNull {
            it.modelId == spec.id && it.revision == spec.revision &&
                it.status == app.timeline.islamic.core.models.ModelLifecycleStatus.INSTALLED
        }
        if (installedModel == null) {
            return@withLock lexicalOnly(null).search(request, installedPacks)
        }

        val runtimeSnapshot = generationRuntime.snapshot.value
        if (runtimeSnapshot.state != GenerationRuntimeState.UNLOADED) {
            val unavailableDenseProvider = LocalDenseRetriever { _, _, _, _ ->
                error("Dense retrieval is unavailable while the generation runtime is active")
            }
            return@withLock lexicalOnly(unavailableDenseProvider).search(request, installedPacks)
        }
        val resourceBlock = runtimeLoadBlockReason(spec.minimumDeviceRamBytes, modelResourceProbe.snapshot())
        if (resourceBlock != null) {
            val unavailableDenseProvider = LocalDenseRetriever { _, _, _, _ -> error(resourceBlock) }
            return@withLock lexicalOnly(unavailableDenseProvider).search(request, installedPacks)
        }
        val embeddingModel = try {
            LiteRtEmbeddingTextModel.open(
                modelManager.resolveInstalledFile(spec.id), File(cacheDir, "litertlm")
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Let the orchestrator retain lexical results and record a dense-provider failure.
            val failedProvider = LocalDenseRetriever { _, _, _, _ ->
                error("The installed local embedding runtime could not be initialized")
            }
            return@withLock lexicalOnly(failedProvider).search(request, installedPacks)
        }
        try {
            val denseRetriever = StoredDenseIndexRetriever(
                store = denseEmbeddingIndexStore,
                model = embeddingModel,
                evidenceLookup = corpusIndex
            )
            lexicalOnly(denseRetriever).search(request, installedPacks)
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
                embeddingModel.close()
            }
        }
    }

    /** Load a catalog-pinned, installed Gemma artifact on LiteRT-LM CPU only. */
    suspend fun loadGenerationModel(modelId: String) = inferenceLifecycleMutex.withLock {
        val spec = ModelCatalog.require(modelId)
        check(spec.task == app.timeline.islamic.core.models.ModelTask.GENERATION) {
            "Selected artifact is not a generation model"
        }
        val installed = modelManager.installedModels().singleOrNull {
            it.modelId == spec.id && it.revision == spec.revision
        }
        check(installed?.status == app.timeline.islamic.core.models.ModelLifecycleStatus.INSTALLED) {
            "A verified installed model is required before runtime load"
        }
        val snapshot = modelResourceProbe.snapshot()
        val runtimeBlock = runtimeLoadBlockReason(spec.minimumDeviceRamBytes, snapshot)
        check(runtimeBlock == null) { runtimeBlock ?: "Runtime preflight blocked" }
        generationRuntime.load(modelId)
    }

    suspend fun runGenerationSmokeTest(): String = inferenceLifecycleMutex.withLock {
        check(generationRuntime.snapshot.value.state == GenerationRuntimeState.READY) {
            "Load a generation model before running the smoke test"
        }
        generationRuntime.runSmokeTest()
    }

    suspend fun unloadGenerationModel() = inferenceLifecycleMutex.withLock {
        generationRuntime.unload()
    }

    /**
     * Build one dense generation from the verified installed EmbeddingGemma artifact. Existing
     * lexical FTS5 and RRF search remain the fallback; this method never silently changes retrieval.
     */
    suspend fun buildLocalEmbeddingIndexWithInstalledModel(
        packId: String,
        version: String
    ): LocalEmbeddingIndexBuilder.Result = inferenceLifecycleMutex.withLock {
        val spec = ModelCatalog.require(LiteRtEmbeddingTextModel.EMBEDDING_MODEL_ID)
        check(spec.task == app.timeline.islamic.core.models.ModelTask.EMBEDDING)
        val installed = modelManager.installedModels().singleOrNull {
            it.modelId == spec.id && it.revision == spec.revision
        }
        check(installed?.status == app.timeline.islamic.core.models.ModelLifecycleStatus.INSTALLED) {
            "A verified installed EmbeddingGemma model is required before indexing"
        }
        val snapshot = modelResourceProbe.snapshot()
        val runtimeBlock = runtimeLoadBlockReason(spec.minimumDeviceRamBytes, snapshot)
        check(runtimeBlock == null) { runtimeBlock ?: "Embedding runtime preflight blocked" }
        check(generationRuntime.snapshot.value.state == GenerationRuntimeState.UNLOADED) {
            "Unload the generation model explicitly before building the dense index"
        }
        val modelFile = modelManager.resolveInstalledFile(spec.id)
        val embeddingModel = LiteRtEmbeddingTextModel.open(modelFile, File(cacheDir, "litertlm"))
        try {
            buildLocalEmbeddingIndex(packId, version, embeddingModel)
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
                embeddingModel.close()
            }
        }
    }

    /** Returns a one-off query vector with the pinned query prefix; it does not switch retrieval. */
    suspend fun embedSearchQueryWithInstalledModel(query: String): FloatArray = inferenceLifecycleMutex.withLock {
        val spec = ModelCatalog.require(LiteRtEmbeddingTextModel.EMBEDDING_MODEL_ID)
        val installed = modelManager.installedModels().singleOrNull {
            it.modelId == spec.id && it.revision == spec.revision
        }
        check(installed?.status == app.timeline.islamic.core.models.ModelLifecycleStatus.INSTALLED) {
            "A verified installed EmbeddingGemma model is required before query embedding"
        }
        val snapshot = modelResourceProbe.snapshot()
        val runtimeBlock = runtimeLoadBlockReason(spec.minimumDeviceRamBytes, snapshot)
        check(runtimeBlock == null) { runtimeBlock ?: "Embedding runtime preflight blocked" }
        check(generationRuntime.snapshot.value.state == GenerationRuntimeState.UNLOADED) {
            "Unload the generation model explicitly before query embedding"
        }
        val embeddingModel = LiteRtEmbeddingTextModel.open(
            modelManager.resolveInstalledFile(spec.id), File(cacheDir, "litertlm")
        )
        try {
            embeddingModel.embedSearchQuery(query)
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
                embeddingModel.close()
            }
        }
    }

    private fun runtimeLoadBlockReason(minimumRamBytes: Long?, snapshot: app.timeline.islamic.core.models.ModelResourceSnapshot): String? = when {
        snapshot.lowMemory -> "Android currently reports low memory; close other apps and retry"
        minimumRamBytes != null && snapshot.totalRamBytes < minimumRamBytes -> "Device RAM is below this model's conservative resource floor"
        snapshot.availableStorageBytes < MINIMUM_RUNTIME_CACHE_RESERVE_BYTES -> "Insufficient private storage for runtime cache reserve"
        snapshot.thermalStatus != null && snapshot.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> "Device is thermally constrained; retry after cooling"
        else -> null
    }

    private companion object {
        const val MINIMUM_RUNTIME_CACHE_RESERVE_BYTES = 512L * 1024L * 1024L
    }


    /** Model inventory failure is distinct from an empty inventory; UI must fail closed. */
    suspend fun loadModelManagementState(): ModelManagementUiState {
        return try {
            modelManager.recover()
            val installed = modelManager.installedModels().associateBy { it.modelId to it.revision }
            val items = ModelCatalog.all().map { spec ->
                val row = installed[spec.id to spec.revision]
                ModelUiItem(
                    id = spec.id,
                    displayName = spec.displayName,
                    description = spec.description,
                    descriptionArabic = spec.descriptionArabic,
                    task = spec.task,
                    expectedBytes = spec.expectedBytes,
                    licenseId = spec.licenseId,
                    minimumRamBytes = spec.minimumDeviceRamBytes,
                    status = row?.status
                )
            }
            ModelManagementUiState(
                loading = false,
                busy = false,
                inventoryReadable = true,
                items = items,
                runtimeSnapshot = generationRuntime.snapshot.value
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            ModelManagementUiState(
                loading = false,
                busy = false,
                inventoryReadable = false,
                items = emptyList(),
                message = failure::class.simpleName ?: "MODEL_INVENTORY_UNAVAILABLE"
            )
        }
    }

    suspend fun importModelArtifact(uri: Uri, modelId: String) {
        val preflight = modelManager.preflight(modelId, modelResourceProbe.snapshot())
        check(preflight.decision != ModelPreflightDecision.BLOCKED_STORAGE) {
            "Insufficient private storage for the selected model and required reserve"
        }
        // RAM and thermal results describe runtime suitability, not whether file import is allowed.
        safModelImporter.import(uri, modelId)
    }

    suspend fun removeModelArtifact(modelId: String) = inferenceLifecycleMutex.withLock {
        if (generationRuntime.snapshot.value.modelId == modelId) generationRuntime.unload()
        check(modelManager.remove(modelId)) { "Model removal did not complete; its recovery state was retained" }
    }

    suspend fun preflightModel(modelId: String): ModelPreflightDecision =
        modelManager.preflight(modelId, modelResourceProbe.snapshot()).decision

}
