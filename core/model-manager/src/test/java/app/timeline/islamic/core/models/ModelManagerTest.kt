package app.timeline.islamic.core.models

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModelManagerTest {
    private lateinit var root: File
    private lateinit var registry: FakeRegistry
    private lateinit var files: FakeFileStore
    private lateinit var manager: ModelManager
    private val bytes = "tiny-model-fixture".toByteArray()
    private lateinit var spec: ModelArtifactSpec

    @Before
    fun setUp() {
        root = Files.createTempDirectory("model-manager-test").toFile()
        registry = FakeRegistry()
        files = FakeFileStore(root)
        spec = ModelArtifactSpec(
            id = "test-model",
            displayName = "Test model",
            description = "Deterministic fixture",
            descriptionArabic = "بيانات اختبار ثابتة",
            task = ModelTask.EMBEDDING,
            repository = "test/pinned-model",
            revision = "0123456789abcdef0123456789abcdef01234567",
            artifactFileName = "test-model.litertlm",
            expectedBytes = bytes.size.toLong(),
            sha256 = sha256(bytes),
            licenseId = "Apache-2.0",
            licenseUrl = "https://example.invalid/license",
            minimumDeviceRamBytes = 2L * GIB
        )
        manager = ModelManager(
            registry = registry,
            files = files,
            catalog = listOf(spec),
            nowEpochMillis = { 1234L },
            minimumStorageReserveBytes = 512L * 1024 * 1024
        )
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun catalogContainsThreeImmutablePinnedArtifacts() {
        val catalog = ModelCatalog.all()
        assertEquals(setOf("gemma-4-e2b-it", "gemma-4-e4b-it", "embeddinggemma-2-text-270m"), catalog.map { it.id }.toSet())
        catalog.forEach { model ->
            assertEquals(40, model.revision.length)
            assertEquals(64, model.sha256.length)
            assertTrue(model.artifactFileName.endsWith(".litertlm"))
            assertEquals("Apache-2.0", model.licenseId)
        }
    }

    @Test
    fun unknownModelIsRejectedBeforeOpeningSourceOrCreatingRegistryRow() = runBlocking {
        var opened = false
        assertThrows(IllegalStateException::class.java) {
            runBlocking { manager.import("not-in-catalog") { opened = true; ByteArrayInputStream(bytes) } }
        }
        assertFalse(opened)
        assertTrue(registry.rows.isEmpty())
    }

    @Test
    fun importVerifiesExactLengthAndDigestBeforeMarkingInstalled() = runBlocking {
        val result = manager.import(spec.id) { ByteArrayInputStream(bytes) }
        assertEquals(ModelLifecycleStatus.INSTALLED, result.status)
        assertEquals(ModelLifecycleStatus.INSTALLED, registry.rows[result.recordId]?.status)
        assertTrue(files.finalFile(spec).isFile)
        assertFalse(files.stagingFile(spec).exists())
    }

    @Test
    fun wrongDigestRemovesOnlyAttemptOwnedBytesAndRegistryRow() = runBlocking {
        val altered = bytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { manager.import(spec.id) { ByteArrayInputStream(altered) } }
        }
        assertTrue(registry.rows.isEmpty())
        assertFalse(files.stagingFile(spec).exists())
        assertFalse(files.finalFile(spec).exists())
    }

    @Test
    fun truncatedStreamIsRejectedAndRolledBack() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { manager.import(spec.id) { ByteArrayInputStream(bytes.copyOf(bytes.size - 1)) } }
        }
        assertTrue(registry.rows.isEmpty())
        assertFalse(files.stagingFile(spec).exists())
    }

    @Test
    fun oversizedStreamIsRejectedAndRolledBack() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { manager.import(spec.id) { ByteArrayInputStream(bytes + byteArrayOf(1)) } }
        }
        assertTrue(registry.rows.isEmpty())
        assertFalse(files.stagingFile(spec).exists())
    }

    @Test
    fun cancellationDuringImportRollsBackStagingAndRegistry() {
        // Use a block body so Kotlin emits a JVM `void` method. A nullable final
        // expression such as `scope.coroutineContext[Job]?.cancel()` otherwise
        // makes this expression-bodied test return kotlin.Unit, which JUnit 4
        // rejects while validating the entire test class.
        runBlocking {
            val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
            lateinit var importJob: Job
            var emitted = false
            val source = object : InputStream() {
                override fun read(): Int = -1
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (emitted) return -1
                    emitted = true
                    bytes.copyInto(buffer, offset)
                    importJob.cancel()
                    return bytes.size
                }
            }
            importJob = scope.launch(start = CoroutineStart.LAZY) {
                manager.import(spec.id) { source }
            }
            importJob.start()
            importJob.join()
            assertTrue(importJob.isCancelled)
            assertTrue(registry.rows.isEmpty())
            assertFalse(files.stagingFile(spec).exists())
            scope.coroutineContext[Job]?.cancel()
        }
    }

    @Test
    fun failedImportCleanupRetainsQuarantinedRegistryRecord() = runBlocking {
        files.allowDelete = false
        val altered = bytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { manager.import(spec.id) { ByteArrayInputStream(altered) } }
        }
        assertEquals(ModelLifecycleStatus.QUARANTINED, registry.rows.values.single().status)
        assertTrue(files.stagingFile(spec).exists())
    }

    @Test
    fun duplicateRevisionCannotBeImportedTwice() = runBlocking {
        manager.import(spec.id) { ByteArrayInputStream(bytes) }
        var opened = false
        assertThrows(IllegalStateException::class.java) {
            runBlocking { manager.import(spec.id) { opened = true; ByteArrayInputStream(bytes) } }
        }
        assertFalse(opened)
        assertEquals(1, registry.rows.size)
    }

    @Test
    fun preexistingUnregisteredFinalArtifactIsNeverDeletedByFailedImport() = runBlocking {
        files.prepare(spec)
        files.finalFile(spec).writeBytes(byteArrayOf(9, 8, 7))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { manager.import(spec.id) { ByteArrayInputStream(bytes) } }
        }
        assertTrue(files.finalFile(spec).isFile)
        assertEquals(listOf(9.toByte(), 8.toByte(), 7.toByte()), files.finalFile(spec).readBytes().toList())
        assertTrue(registry.rows.isEmpty())
    }

    @Test
    fun destinationAppearingImmediatelyBeforeCommitIsNeverDeletedByRollback() = runBlocking {
        val unrelated = byteArrayOf(7, 6, 5, 4)
        files.beforeCommit = { files.finalFile(spec).writeBytes(unrelated) }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { manager.import(spec.id) { ByteArrayInputStream(bytes) } }
        }
        assertEquals(unrelated.toList(), files.finalFile(spec).readBytes().toList())
        assertFalse(files.stagingFile(spec).exists())
        assertTrue(registry.rows.isEmpty())
    }

    @Test
    fun renameCompletedButDirectorySyncFailedIsRecoveredWithoutDeletingCommittedBytes() = runBlocking {
        files.failAfterAtomicRename = true
        assertThrows(ModelCommitRecoveryRequired::class.java) {
            runBlocking { manager.import(spec.id) { ByteArrayInputStream(bytes) } }
        }
        val record = registry.rows.values.single()
        assertEquals(ModelLifecycleStatus.INSTALLING, record.status)
        assertEquals(bytes.toList(), files.finalFile(spec).readBytes().toList())
        assertFalse(files.stagingFile(spec).exists())

        files.failAfterAtomicRename = false
        val report = manager.recover()
        assertEquals(1, report.recoveredInstalls)
        assertEquals(ModelLifecycleStatus.INSTALLED, registry.rows[record.recordId]?.status)
    }

    @Test
    fun interruptedInstallWithVerifiedFinalArtifactIsRecovered() = runBlocking {
        val row = newRecord(ModelLifecycleStatus.INSTALLING)
        registry.rows[row.recordId] = row
        files.prepare(spec)
        files.finalFile(spec).writeBytes(bytes)
        val report = manager.recover()
        assertEquals(1, report.recoveredInstalls)
        assertEquals(ModelLifecycleStatus.INSTALLED, registry.rows[row.recordId]?.status)
    }

    @Test
    fun interruptedInstallWithoutValidFinalArtifactIsRemoved() = runBlocking {
        val row = newRecord(ModelLifecycleStatus.INSTALLING)
        registry.rows[row.recordId] = row
        files.prepare(spec)
        files.createInstallOwnershipMarker(spec, row.recordId)
        files.stagingFile(spec).writeBytes(byteArrayOf(1, 2))
        val report = manager.recover()
        assertEquals(1, report.removed)
        assertNull(registry.rows[row.recordId])
        assertFalse(files.stagingFile(spec).exists())
    }

    @Test
    fun interruptedInstallNeverDeletesAnUnregisteredInvalidFinalArtifact() = runBlocking {
        val row = newRecord(ModelLifecycleStatus.INSTALLING)
        registry.rows[row.recordId] = row
        files.prepare(spec)
        val unrelated = byteArrayOf(4, 3, 2, 1)
        files.finalFile(spec).writeBytes(unrelated)

        val report = manager.recover()

        assertEquals(1, report.quarantined)
        assertEquals(ModelLifecycleStatus.QUARANTINED, registry.rows[row.recordId]?.status)
        assertEquals(unrelated.toList(), files.finalFile(spec).readBytes().toList())
    }

    @Test
    fun interruptedInstallNeverDeletesOrphanPartialWithoutMatchingMarker() = runBlocking {
        val row = newRecord(ModelLifecycleStatus.INSTALLING)
        registry.rows[row.recordId] = row
        files.prepare(spec)
        val orphan = byteArrayOf(8, 8, 8)
        files.stagingFile(spec).writeBytes(orphan)

        val report = manager.recover()

        assertEquals(1, report.quarantined)
        assertEquals(orphan.toList(), files.stagingFile(spec).readBytes().toList())
        assertEquals(ModelLifecycleStatus.QUARANTINED, registry.rows[row.recordId]?.status)
    }

    @Test
    fun mismatchedInstallOwnershipMarkerQuarantinesWithoutDeletingPartial() = runBlocking {
        val row = newRecord(ModelLifecycleStatus.INSTALLING)
        registry.rows[row.recordId] = row
        files.prepare(spec)
        files.createInstallOwnershipMarker(spec, "f".repeat(64))
        val orphan = byteArrayOf(6, 5, 4)
        files.stagingFile(spec).writeBytes(orphan)

        val report = manager.recover()

        assertEquals(1, report.quarantined)
        assertEquals(orphan.toList(), files.stagingFile(spec).readBytes().toList())
    }

    @Test
    fun installedArtifactTamperingIsQuarantinedDuringRecovery() = runBlocking {
        manager.import(spec.id) { ByteArrayInputStream(bytes) }
        val row = registry.rows.values.single()
        files.finalFile(spec).writeBytes(bytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })
        val report = manager.recover()
        assertEquals(1, report.quarantined)
        assertEquals(ModelLifecycleStatus.QUARANTINED, registry.rows[row.recordId]?.status)
    }

    @Test
    fun resolveRehashesImmediatelyAndQuarantinesTamperedFile() = runBlocking {
        manager.import(spec.id) { ByteArrayInputStream(bytes) }
        files.finalFile(spec).writeBytes(byteArrayOf(0))
        assertThrows(IllegalStateException::class.java) { runBlocking { manager.resolveInstalledFile(spec.id) } }
        assertEquals(ModelLifecycleStatus.QUARANTINED, registry.rows.values.single().status)
    }

    @Test
    fun removalDeletesFilesBeforeDeletingRegistryRecord() = runBlocking {
        manager.import(spec.id) { ByteArrayInputStream(bytes) }
        val record = registry.rows.values.single()
        assertTrue(manager.remove(spec.id))
        assertNull(registry.rows[record.recordId])
        assertFalse(files.finalFile(spec).exists())
    }

    @Test
    fun explicitRemovalPreservesArtifactWithoutMatchingOwnershipMarker() = runBlocking {
        val row = newRecord(ModelLifecycleStatus.QUARANTINED)
        registry.rows[row.recordId] = row
        files.prepare(spec)
        val unrelated = byteArrayOf(2, 4, 6, 8)
        files.finalFile(spec).writeBytes(unrelated)

        assertFalse(manager.remove(spec.id))
        assertEquals(ModelLifecycleStatus.REMOVAL_PENDING, registry.rows[row.recordId]?.status)
        assertEquals(unrelated.toList(), files.finalFile(spec).readBytes().toList())
    }

    @Test
    fun failedRemovalRetainsRemovalPendingRegistryRecord() = runBlocking {
        manager.import(spec.id) { ByteArrayInputStream(bytes) }
        val record = registry.rows.values.single()
        files.allowDelete = false
        assertFalse(manager.remove(spec.id))
        assertEquals(ModelLifecycleStatus.REMOVAL_PENDING, registry.rows[record.recordId]?.status)
        assertTrue(files.finalFile(spec).exists())
    }

    @Test
    fun pendingRemovalCanBeRecoveredAndDeletedLater() = runBlocking {
        manager.import(spec.id) { ByteArrayInputStream(bytes) }
        val record = registry.rows.values.single()
        files.allowDelete = false
        assertFalse(manager.remove(spec.id))
        files.allowDelete = true
        val report = manager.recover()
        assertEquals(1, report.removed)
        assertNull(registry.rows[record.recordId])
        assertFalse(files.finalFile(spec).exists())
    }

    @Test
    fun preflightBlocksLowMemoryBeforeOtherChecks() {
        val result = manager.preflight(spec.id, snapshot(lowMemory = true, totalRamBytes = 1))
        assertEquals(ModelPreflightDecision.BLOCKED_LOW_MEMORY, result.decision)
        assertFalse(result.runtimeReady)
    }

    @Test
    fun preflightBlocksBelowPublisherRamFloor() {
        val result = manager.preflight(spec.id, snapshot(totalRamBytes = GIB))
        assertEquals(ModelPreflightDecision.BLOCKED_DEVICE_RAM, result.decision)
    }

    @Test
    fun preflightBlocksInsufficientStorage() {
        val result = manager.preflight(spec.id, snapshot(availableStorageBytes = spec.expectedBytes + 1))
        assertEquals(ModelPreflightDecision.BLOCKED_STORAGE, result.decision)
    }

    @Test
    fun preflightBlocksSevereThermalState() {
        val result = manager.preflight(spec.id, snapshot(thermalStatus = ModelManager.THERMAL_STATUS_SEVERE))
        assertEquals(ModelPreflightDecision.BLOCKED_THERMAL, result.decision)
    }

    @Test
    fun preflightKeepsUnknownThermalStateAsRuntimeProbeRequired() {
        val result = manager.preflight(spec.id, snapshot(thermalStatus = null))
        assertEquals(ModelPreflightDecision.RUNTIME_PROBE_REQUIRED, result.decision)
        assertNull(result.thermalStatus)
        assertFalse(result.runtimeReady)
    }

    @Test
    fun passingResourceEstimateStillRequiresARealRuntimeProbe() {
        val result = manager.preflight(spec.id, snapshot())
        assertEquals(ModelPreflightDecision.RUNTIME_PROBE_REQUIRED, result.decision)
        assertFalse(result.runtimeReady)
    }

    private fun newRecord(status: ModelLifecycleStatus) = InstalledModelRecord(
        recordId = stableModelRecordId(spec),
        modelId = spec.id,
        revision = spec.revision,
        sha256 = spec.sha256,
        runtimeFormat = ModelManager.RUNTIME_FORMAT_LITERT_LM,
        status = status,
        installedAtEpochMillis = 1L
    )

    private fun snapshot(
        totalRamBytes: Long = 8L * GIB,
        availableStorageBytes: Long = 2L * GIB,
        lowMemory: Boolean = false,
        processPssBytes: Long = 100L * 1024 * 1024,
        memoryClassMb: Int = 256,
        thermalStatus: Int? = 0
    ) = ModelResourceSnapshot(totalRamBytes, availableStorageBytes, lowMemory, processPssBytes, memoryClassMb, thermalStatus)

    private fun sha256(value: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }

    private class FakeRegistry : ModelRegistry {
        val rows = linkedMapOf<String, InstalledModelRecord>()
        override suspend fun find(modelId: String, revision: String): InstalledModelRecord? =
            rows.values.singleOrNull { it.modelId == modelId && it.revision == revision }
        override suspend fun list(): List<InstalledModelRecord> = rows.values.toList()
        override suspend fun insertInstalling(record: InstalledModelRecord) {
            check(record.status == ModelLifecycleStatus.INSTALLING)
            check(find(record.modelId, record.revision) == null)
            rows[record.recordId] = record
        }
        override suspend fun transition(recordId: String, expected: ModelLifecycleStatus, next: ModelLifecycleStatus): Boolean {
            val current = rows[recordId] ?: return false
            if (current.status != expected) return false
            rows[recordId] = current.copy(status = next)
            return true
        }
        override suspend fun deleteAfterFilesRemoved(recordId: String): Boolean {
            if (rows[recordId]?.status != ModelLifecycleStatus.REMOVAL_PENDING) return false
            rows.remove(recordId)
            return true
        }
    }

    private class FakeFileStore(private val root: File) : ModelFileStore {
        var allowDelete = true
        var beforeCommit: (() -> Unit)? = null
        var failAfterAtomicRename = false
        override fun prepare(spec: ModelArtifactSpec) {
            check(versionDirectory(spec).mkdirs() || versionDirectory(spec).isDirectory)
        }
        override fun stagingFile(spec: ModelArtifactSpec): File = File(versionDirectory(spec), "${spec.artifactFileName}.partial")
        override fun finalFile(spec: ModelArtifactSpec): File = File(versionDirectory(spec), spec.artifactFileName)
        override fun installOwnershipMarkerFile(spec: ModelArtifactSpec): File = File(versionDirectory(spec), ".${spec.artifactFileName}.install-owner")
        override fun createInstallOwnershipMarker(spec: ModelArtifactSpec, recordId: String) {
            val marker = installOwnershipMarkerFile(spec)
            check(marker.createNewFile())
            marker.writeText(recordId, Charsets.US_ASCII)
        }
        override fun ownsInstallAttempt(spec: ModelArtifactSpec, recordId: String): Boolean =
            installOwnershipMarkerFile(spec).takeIf { it.isFile && it.length() == 64L }?.readText(Charsets.US_ASCII) == recordId
        override fun deleteInstallOwnershipMarker(spec: ModelArtifactSpec, recordId: String): Boolean {
            val marker = installOwnershipMarkerFile(spec)
            if (!marker.exists()) return true
            if (!ownsInstallAttempt(spec, recordId)) return false
            return deleteExact(marker)
        }
        override fun deleteInterruptedInstallFiles(spec: ModelArtifactSpec, recordId: String): Boolean {
            if (finalFile(spec).exists() || !ownsInstallAttempt(spec, recordId)) return false
            return deleteStagingFile(spec) && deleteInstallOwnershipMarker(spec, recordId)
        }
        override fun createStagingFile(spec: ModelArtifactSpec): File {
            val file = stagingFile(spec)
            check(file.createNewFile())
            return file
        }
        override fun commit(staging: File, destination: File) {
            beforeCommit?.invoke()
            check(destination.createNewFile()) { "Model destination already exists and cannot be reserved" }
            try {
                Files.move(staging.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (failure: Throwable) {
                if (staging.exists() && destination.isFile && destination.length() == 0L) destination.delete()
                throw failure
            }
            if (failAfterAtomicRename) throw ModelCommitRecoveryRequired("injected directory sync failure")
        }
        override fun deleteStagingFile(spec: ModelArtifactSpec): Boolean = deleteExact(stagingFile(spec))
        override fun deleteCommittedFile(spec: ModelArtifactSpec): Boolean = deleteExact(finalFile(spec))
        override fun deleteOwnedFiles(spec: ModelArtifactSpec, recordId: String): Boolean {
            if (!allowDelete) return false
            val marker = installOwnershipMarkerFile(spec)
            val staging = stagingFile(spec)
            val artifact = finalFile(spec)
            if ((staging.exists() || artifact.exists()) && !ownsInstallAttempt(spec, recordId)) return false
            if (marker.exists() && !ownsInstallAttempt(spec, recordId)) return false
            if (!deleteStagingFile(spec) || !deleteCommittedFile(spec)) return false
            if (marker.exists() && !deleteInstallOwnershipMarker(spec, recordId)) return false
            val directory = versionDirectory(spec)
            if (directory.exists() && directory.listFiles()?.isNotEmpty() == true) return false
            directory.delete()
            val modelDirectory = directory.parentFile
            if (modelDirectory?.listFiles()?.isEmpty() == true) modelDirectory.delete()
            return true
        }
        override fun isInsideManagedRoot(file: File): Boolean =
            file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) && file.canonicalFile != root.canonicalFile
        private fun deleteExact(file: File): Boolean = if (!allowDelete) false else !file.exists() || file.delete()
        private fun versionDirectory(spec: ModelArtifactSpec) = File(File(root, spec.id), spec.revision)
    }

    private companion object { const val GIB = 1024L * 1024 * 1024 }
}
