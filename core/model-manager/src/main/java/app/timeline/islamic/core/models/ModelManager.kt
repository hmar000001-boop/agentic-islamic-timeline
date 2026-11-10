package app.timeline.islamic.core.models

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Atomic rename completed or its completion became uncertain; retain INSTALLING for recovery. */
class ModelCommitRecoveryRequired(message: String, cause: Throwable? = null) : IOException(message, cause)

interface ModelFileStore {
    fun prepare(spec: ModelArtifactSpec)
    fun stagingFile(spec: ModelArtifactSpec): File
    fun finalFile(spec: ModelArtifactSpec): File
    /** Creates a durable ownership marker before any bytes are written for this registry record. */
    fun createInstallOwnershipMarker(spec: ModelArtifactSpec, recordId: String)
    fun installOwnershipMarkerFile(spec: ModelArtifactSpec): File
    fun ownsInstallAttempt(spec: ModelArtifactSpec, recordId: String): Boolean
    fun deleteInstallOwnershipMarker(spec: ModelArtifactSpec, recordId: String): Boolean
    /** Removes only a marker-owned staging file, and only while no final artifact exists. */
    fun deleteInterruptedInstallFiles(spec: ModelArtifactSpec, recordId: String): Boolean
    /** Creates the deterministic staging path with exclusive-create semantics. */
    fun createStagingFile(spec: ModelArtifactSpec): File
    /** Must exclusively reserve the destination, then atomically rename on the same filesystem; never overwrite existing bytes. */
    fun commit(staging: File, destination: File)
    /** Deletes one exact catalog-derived file; never follows symlinks or recursively deletes. */
    fun deleteStagingFile(spec: ModelArtifactSpec): Boolean
    fun deleteCommittedFile(spec: ModelArtifactSpec): Boolean
    /** Deletes exact catalog-owned bytes only when any ownership marker matches this registry record. */
    fun deleteOwnedFiles(spec: ModelArtifactSpec, recordId: String): Boolean
    fun isInsideManagedRoot(file: File): Boolean
}

data class ModelResourceSnapshot(
    val totalRamBytes: Long,
    val availableStorageBytes: Long,
    val lowMemory: Boolean,
    val processPssBytes: Long,
    val memoryClassMb: Int,
    val thermalStatus: Int?
)

enum class ModelPreflightDecision {
    BLOCKED_LOW_MEMORY,
    BLOCKED_DEVICE_RAM,
    BLOCKED_STORAGE,
    BLOCKED_THERMAL,
    RUNTIME_PROBE_REQUIRED
}

data class ModelPreflightResult(
    val decision: ModelPreflightDecision,
    val measuredRamBytes: Long,
    val measuredAvailableStorageBytes: Long,
    val measuredProcessPssBytes: Long,
    val memoryClassMb: Int,
    val thermalStatus: Int?,
    val requiredFreeStorageBytes: Long
) {
    /** This result is never a claim that an engine loaded or that inference is supported. */
    val runtimeReady: Boolean get() = false
}

class ModelManager(
    private val registry: ModelRegistry,
    private val files: ModelFileStore,
    private val catalog: List<ModelArtifactSpec> = ModelCatalog.all(),
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val minimumStorageReserveBytes: Long = 512L * 1024 * 1024
) {
    private val operationMutex = Mutex()
    private val catalogById = catalog.associateBy(ModelArtifactSpec::id)

    init {
        require(catalogById.size == catalog.size) { "Model catalog ids must be unique" }
        require(minimumStorageReserveBytes >= 0)
    }

    fun availableCatalog(): List<ModelArtifactSpec> = catalog.toList()

    suspend fun installedModels(): List<InstalledModelRecord> = registry.list()

    /**
     * Installs only a catalog item. Bytes are streamed into an app-private staging file, bounded
     * by the pinned size, hashed, synced and atomically renamed before Room is marked INSTALLED.
     */
    suspend fun import(modelId: String, openSource: () -> InputStream): InstalledModelRecord = operationMutex.withLock {
        val spec = catalogById[modelId] ?: error("Model is not present in the built-in catalog")
        val recordId = stableModelRecordId(spec)
        check(registry.find(spec.id, spec.revision) == null) {
            "This exact model revision already has a registry record; remove or recover it first"
        }

        val installing = InstalledModelRecord(
            recordId = recordId,
            modelId = spec.id,
            revision = spec.revision,
            sha256 = spec.sha256,
            runtimeFormat = RUNTIME_FORMAT_LITERT_LM,
            status = ModelLifecycleStatus.INSTALLING,
            installedAtEpochMillis = nowEpochMillis()
        )
        registry.insertInstalling(installing)

        var ownershipMarkerCreatedByAttempt = false
        var stagingCreatedByAttempt = false
        var commitCompletedByAttempt = false
        try {
            files.prepare(spec)
            val staging = files.stagingFile(spec)
            val destination = files.finalFile(spec)
            require(files.isInsideManagedRoot(staging) && files.isInsideManagedRoot(destination)) {
                "Model path escaped the managed model directory"
            }
            require(!destination.exists()) { "An unregistered model artifact already exists at the catalog path" }
            files.createInstallOwnershipMarker(spec, recordId)
            ownershipMarkerCreatedByAttempt = true
            files.createStagingFile(spec).also { created ->
                require(created.canonicalFile == staging.canonicalFile) { "Model store returned an unexpected staging path" }
                stagingCreatedByAttempt = true
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            FileOutputStream(staging, false).use { output ->
                openSource().use { input ->
                    val buffer = ByteArray(STREAM_BUFFER_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        copied += read.toLong()
                        require(copied <= spec.expectedBytes) { "Selected file exceeds the catalog's exact byte limit" }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
                output.fd.sync()
            }

            require(copied == spec.expectedBytes) {
                "Selected file length does not match the catalog (expected ${spec.expectedBytes}, received $copied)"
            }
            val actualHash = digest.digest().toHex()
            require(actualHash == spec.sha256) { "Selected file SHA-256 does not match the pinned catalog artifact" }

            require(!destination.exists()) { "Model destination appeared before commit" }
            // The store reserves the destination with exclusive-create before atomic rename.
            // A durability failure after rename preserves INSTALLING for recovery.
            files.commit(staging, destination)
            commitCompletedByAttempt = true
            check(!staging.exists()) { "Model commit did not consume the staging file with an atomic rename" }
            stagingCreatedByAttempt = false
            require(verifyFile(spec, destination)) { "Committed model artifact failed post-rename verification" }
            check(registry.transition(recordId, ModelLifecycleStatus.INSTALLING, ModelLifecycleStatus.INSTALLED)) {
                "Model registry changed during installation; artifact will not be marked installed"
            }
            installing.copy(status = ModelLifecycleStatus.INSTALLED)
        } catch (failure: Throwable) {
            // If atomic rename happened but durability could not be confirmed (or its exact
            // outcome is uncertain), preserve INSTALLING and let recovery hash the destination.
            // Otherwise roll back only files this attempt is known to own. In particular, a
            // destination that appeared before a no-replace commit is never deleted here.
            if (failure is ModelCommitRecoveryRequired) throw failure
            val cleaned = runCatching {
                val stagingClean = !stagingCreatedByAttempt || files.deleteStagingFile(spec)
                val committedClean = !commitCompletedByAttempt || files.deleteCommittedFile(spec)
                val markerClean = !ownershipMarkerCreatedByAttempt || files.deleteInstallOwnershipMarker(spec, recordId)
                stagingClean && committedClean && markerClean
            }.getOrDefault(false)
            if (cleaned) {
                runCatching {
                    registry.transition(recordId, ModelLifecycleStatus.INSTALLING, ModelLifecycleStatus.REMOVAL_PENDING)
                    registry.deleteAfterFilesRemoved(recordId)
                }
            } else {
                runCatching {
                    registry.transition(recordId, ModelLifecycleStatus.INSTALLING, ModelLifecycleStatus.QUARANTINED)
                }
            }
            throw failure
        }
    }

    /** Reconciles interrupted work and re-hashes every row that claims to be installed. */
    suspend fun recover(): ModelRecoveryReport = operationMutex.withLock {
        var recoveredInstalls = 0
        var quarantined = 0
        var removed = 0
        var pendingRemovals = 0
        val rows = registry.list()

        for (row in rows) {
            val spec = catalogById[row.modelId]
            if (spec == null || spec.revision != row.revision || spec.sha256 != row.sha256 || row.runtimeFormat != RUNTIME_FORMAT_LITERT_LM) {
                if (row.status != ModelLifecycleStatus.QUARANTINED && row.status != ModelLifecycleStatus.REMOVAL_PENDING) {
                    if (registry.transition(row.recordId, row.status, ModelLifecycleStatus.QUARANTINED)) quarantined++
                }
                continue
            }

            when (row.status) {
                ModelLifecycleStatus.INSTALLING -> {
                    val artifact = files.finalFile(spec)
                    if (files.isInsideManagedRoot(artifact) && verifyFile(spec, artifact)) {
                        if (registry.transition(row.recordId, ModelLifecycleStatus.INSTALLING, ModelLifecycleStatus.INSTALLED)) recoveredInstalls++
                    } else if (artifact.exists()) {
                        // A destination with an invalid digest may have existed before this
                        // interrupted attempt. The Room INSTALLING row alone is not ownership
                        // proof, so never delete it during automatic recovery.
                        if (registry.transition(row.recordId, ModelLifecycleStatus.INSTALLING, ModelLifecycleStatus.QUARANTINED)) quarantined++
                    } else if (files.ownsInstallAttempt(spec, row.recordId)) {
                        val cleaned = runCatching { files.deleteInterruptedInstallFiles(spec, row.recordId) }.getOrDefault(false)
                        if (cleaned) {
                            registry.transition(row.recordId, ModelLifecycleStatus.INSTALLING, ModelLifecycleStatus.REMOVAL_PENDING)
                            if (registry.deleteAfterFilesRemoved(row.recordId)) removed++
                        } else if (registry.transition(row.recordId, ModelLifecycleStatus.INSTALLING, ModelLifecycleStatus.QUARANTINED)) {
                            quarantined++
                        }
                    } else if (!files.installOwnershipMarkerFile(spec).exists() && !files.stagingFile(spec).exists()) {
                        // The process died after the Room row was inserted but before it created
                        // an ownership marker or any file. Removing the empty row is safe.
                        registry.transition(row.recordId, ModelLifecycleStatus.INSTALLING, ModelLifecycleStatus.REMOVAL_PENDING)
                        if (registry.deleteAfterFilesRemoved(row.recordId)) removed++
                    } else if (registry.transition(row.recordId, ModelLifecycleStatus.INSTALLING, ModelLifecycleStatus.QUARANTINED)) {
                        quarantined++
                    }
                }
                ModelLifecycleStatus.INSTALLED -> {
                    val artifact = files.finalFile(spec)
                    if (!files.isInsideManagedRoot(artifact) || !verifyFile(spec, artifact)) {
                        if (registry.transition(row.recordId, ModelLifecycleStatus.INSTALLED, ModelLifecycleStatus.QUARANTINED)) quarantined++
                    }
                }
                ModelLifecycleStatus.QUARANTINED -> Unit
                ModelLifecycleStatus.REMOVAL_PENDING -> {
                    if (runCatching { files.deleteOwnedFiles(spec, row.recordId) }.getOrDefault(false)) {
                        if (registry.deleteAfterFilesRemoved(row.recordId)) removed++
                    } else pendingRemovals++
                }
            }
        }

        // Unregistered staging files are deliberately left untouched. Without a matching
        // per-attempt ownership marker, filename shape alone is not proof that the file belongs
        // to this interrupted import.
        ModelRecoveryReport(recoveredInstalls, quarantined, removed, pendingRemovals)
    }

    /** Removal records REMOVAL_PENDING before touching bytes and deletes Room only after success. */
    suspend fun remove(modelId: String): Boolean = operationMutex.withLock {
        val spec = catalogById[modelId] ?: error("Model is not present in the built-in catalog")
        val row = registry.find(spec.id, spec.revision) ?: return@withLock false
        if (row.status == ModelLifecycleStatus.REMOVAL_PENDING) {
            val deleted = files.deleteOwnedFiles(spec, row.recordId)
            return@withLock deleted && registry.deleteAfterFilesRemoved(row.recordId)
        }
        require(row.status == ModelLifecycleStatus.INSTALLED || row.status == ModelLifecycleStatus.QUARANTINED) {
            "Model cannot be removed while its lifecycle is ${row.status}"
        }
        if (!registry.transition(row.recordId, row.status, ModelLifecycleStatus.REMOVAL_PENDING)) return@withLock false
        val allBytesGone = runCatching { files.deleteOwnedFiles(spec, row.recordId) }.getOrDefault(false)
        if (!allBytesGone) return@withLock false
        registry.deleteAfterFilesRemoved(row.recordId)
    }

    /** Resolve is a second verification boundary immediately before a future runtime may open bytes. */
    suspend fun resolveInstalledFile(modelId: String): File = operationMutex.withLock {
        val spec = catalogById[modelId] ?: error("Model is not present in the built-in catalog")
        val row = registry.find(spec.id, spec.revision) ?: error("Model revision is not installed")
        check(row.status == ModelLifecycleStatus.INSTALLED) { "Model is not in INSTALLED state" }
        val file = files.finalFile(spec)
        if (!files.isInsideManagedRoot(file) || !verifyFile(spec, file)) {
            registry.transition(row.recordId, ModelLifecycleStatus.INSTALLED, ModelLifecycleStatus.QUARANTINED)
            error("Installed model failed size or SHA-256 revalidation and has been quarantined")
        }
        file
    }

    fun preflight(modelId: String, snapshot: ModelResourceSnapshot): ModelPreflightResult {
        val spec = catalogById[modelId] ?: error("Model is not present in the built-in catalog")
        val requiredStorage = Math.addExact(spec.expectedBytes, minimumStorageReserveBytes)
        // Keep nullable resource observations stable for all checks and the returned snapshot.
        val thermalStatus = snapshot.thermalStatus
        val decision = when {
            snapshot.lowMemory -> ModelPreflightDecision.BLOCKED_LOW_MEMORY
            spec.minimumDeviceRamBytes != null && snapshot.totalRamBytes < spec.minimumDeviceRamBytes -> ModelPreflightDecision.BLOCKED_DEVICE_RAM
            snapshot.availableStorageBytes < requiredStorage -> ModelPreflightDecision.BLOCKED_STORAGE
            thermalStatus != null && thermalStatus >= THERMAL_STATUS_SEVERE -> ModelPreflightDecision.BLOCKED_THERMAL
            else -> ModelPreflightDecision.RUNTIME_PROBE_REQUIRED
        }
        return ModelPreflightResult(
            decision = decision,
            measuredRamBytes = snapshot.totalRamBytes,
            measuredAvailableStorageBytes = snapshot.availableStorageBytes,
            measuredProcessPssBytes = snapshot.processPssBytes,
            memoryClassMb = snapshot.memoryClassMb,
            thermalStatus = thermalStatus,
            requiredFreeStorageBytes = requiredStorage
        )
    }

    private suspend fun verifyFile(spec: ModelArtifactSpec, file: File): Boolean {
        if (!file.isFile || file.length() != spec.expectedBytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        return try {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(STREAM_BUFFER_BYTES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    total += read.toLong()
                    if (total > spec.expectedBytes) return false
                    digest.update(buffer, 0, read)
                }
            }
            total == spec.expectedBytes && digest.digest().toHex() == spec.sha256
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            false
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        const val RUNTIME_FORMAT_LITERT_LM = "LITERT_LM"
        const val STREAM_BUFFER_BYTES = 64 * 1024
        const val THERMAL_STATUS_SEVERE = 4
    }
}

data class ModelRecoveryReport(
    val recoveredInstalls: Int,
    val quarantined: Int,
    val removed: Int,
    val pendingRemovals: Int
)
