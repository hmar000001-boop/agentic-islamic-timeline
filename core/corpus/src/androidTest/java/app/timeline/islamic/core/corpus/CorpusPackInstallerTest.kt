package app.timeline.islamic.core.corpus

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.timeline.islamic.core.knowledge.CorpusIndexValidationReport
import app.timeline.islamic.core.knowledge.CorpusIndexValidator
import app.timeline.islamic.core.knowledge.CorpusLanguage
import app.timeline.islamic.core.knowledge.CorpusPackManifest
import app.timeline.islamic.core.knowledge.CorpusPackRegistry
import app.timeline.islamic.core.knowledge.InstalledCorpusPackRecord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises filesystem/registry behavior on Android; cryptographic vectors are tested separately. */
@RunWith(AndroidJUnit4::class)
class CorpusPackInstallerTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun installsValidatedPackAndRetainsSignedManifestAndSignature(): Unit = runBlocking {
        val packId = uniquePackId("install")
        val fixture = fixture(packId)
        val registry = MemoryRegistry()
        try {
            val result = fixture.installer(registry).install(ByteArrayInputStream(fixture.archive))
            val versionDirectory = File(context.filesDir, "corpus-packs/$packId/1.0.0")
            assertEquals("$packId/1.0.0/corpus.sqlite", result.installedRelativePath)
            assertTrue(File(versionDirectory, "corpus.sqlite").isFile)
            assertTrue(File(versionDirectory, "manifest.txt").isFile)
            assertTrue(File(versionDirectory, "manifest.sig").isFile)
            assertTrue(File(versionDirectory, "manifest.txt").readBytes().contentEquals(CorpusPackManifestCodec.encode(fixture.manifest)))
            assertTrue(File(versionDirectory, "manifest.sig").readBytes().contentEquals(ByteArray(64) { 5 }))
            assertEquals(1, registry.records.size)
            assertEquals(result.manifestSha256, registry.records.single().manifestSha256)
            assertEquals(packId, registry.records.single().packId)
        } finally {
            File(context.filesDir, "corpus-packs/$packId").deleteRecursively()
        }
    }

    @Test fun rejectsUnexpectedZipEntryWithoutRegisteringOrLeavingInstalledFiles() {
        val packId = uniquePackId("traversal")
        val fixture = fixture(packId, extraEntries = mapOf("../$packId-escape.txt" to "escape".toByteArray()))
        val registry = MemoryRegistry()
        try {
            expectInstallFailure { runBlocking { fixture.installer(registry).install(ByteArrayInputStream(fixture.archive)) } }
            assertTrue(registry.records.isEmpty())
            assertFalse(File(context.filesDir, "corpus-packs/$packId").exists())
            assertFalse(File(context.filesDir, "$packId-escape.txt").exists())
        } finally {
            File(context.filesDir, "corpus-packs/$packId").deleteRecursively()
            File(context.filesDir, "$packId-escape.txt").delete()
        }
    }

    @Test fun rejectsInvalidSignatureBeforeIndexValidationOrRegistryCommit() {
        val packId = uniquePackId("bad-signature")
        val fixture = fixture(packId)
        val registry = MemoryRegistry()
        var validatorCalled = false
        val validator = object : CorpusIndexValidator {
            override fun validate(
                indexFile: File,
                manifest: CorpusPackManifest,
                asOfEpochMillis: Long
            ): CorpusIndexValidationReport {
                validatorCalled = true
                return CorpusIndexValidationReport(1, 1, 1, setOf(CorpusLanguage.ARABIC))
            }
        }
        try {
            expectInstallFailure {
                runBlocking {
                    fixture.installer(registry, validator, signatureValid = false)
                        .install(ByteArrayInputStream(fixture.archive))
                }
            }
            assertFalse(validatorCalled)
            assertTrue(registry.records.isEmpty())
            assertFalse(File(context.filesDir, "corpus-packs/$packId").exists())
        } finally {
            File(context.filesDir, "corpus-packs/$packId").deleteRecursively()
        }
    }

    @Test fun rollsBackInstalledDirectoryWhenRegistryCommitFails() {
        val packId = uniquePackId("registry-fail")
        val fixture = fixture(packId)
        val registry = MemoryRegistry(failRegistration = true)
        try {
            expectInstallFailure { runBlocking { fixture.installer(registry).install(ByteArrayInputStream(fixture.archive)) } }
            assertTrue(registry.records.isEmpty())
            assertFalse(File(context.filesDir, "corpus-packs/$packId/1.0.0").exists())
        } finally {
            File(context.filesDir, "corpus-packs/$packId").deleteRecursively()
        }
    }

    @Test fun refusesInstallWhenPublisherKeyHasBeenRevoked() {
        val packId = uniquePackId("revoked-key")
        val fixture = fixture(packId)
        val registry = MemoryRegistry()
        try {
            expectInstallFailure {
                runBlocking {
                    fixture.installer(registry, keyRevokedAt = 1_500L)
                        .install(ByteArrayInputStream(fixture.archive))
                }
            }
            assertTrue(registry.records.isEmpty())
        } finally {
            File(context.filesDir, "corpus-packs/$packId").deleteRecursively()
        }
    }

    @Test fun refusesInstallWhenPublisherKeyIsNotYetActive() {
        val packId = uniquePackId("inactive-key")
        val fixture = fixture(packId)
        val registry = MemoryRegistry()
        try {
            expectInstallFailure {
                runBlocking {
                    fixture.installer(registry, keyNotBefore = 3_000L)
                        .install(ByteArrayInputStream(fixture.archive))
                }
            }
            assertTrue(registry.records.isEmpty())
        } finally {
            File(context.filesDir, "corpus-packs/$packId").deleteRecursively()
        }
    }


    @Test fun updateActivatesOnlyStrictlyNewerVersionAndRetainsSupersededEvidence(): Unit = runBlocking {
        val packId = uniquePackId("update")
        val v1 = fixture(packId, version = "1.0.0")
        val v2 = fixture(packId, version = "1.1.0")
        val registry = MemoryRegistry()
        try {
            v1.installer(registry).install(ByteArrayInputStream(v1.archive))
            val updated = v2.installer(registry).update(ByteArrayInputStream(v2.archive), "1.0.0")
            assertEquals("1.1.0", updated.version)
            assertEquals(1, registry.records.count { it.status == InstalledCorpusPackRecord.STATUS_INSTALLED })
            assertEquals(1, registry.records.count { it.status == InstalledCorpusPackRecord.STATUS_SUPERSEDED })
            assertTrue(File(context.filesDir, "corpus-packs/$packId/1.0.0/corpus.sqlite").isFile)
            assertTrue(File(context.filesDir, "corpus-packs/$packId/1.1.0/corpus.sqlite").isFile)
        } finally {
            File(context.filesDir, "corpus-packs/$packId").deleteRecursively()
        }
    }

    @Test fun updateRejectsDowngradeAndPreservesCurrentActiveVersion(): Unit = runBlocking {
        val packId = uniquePackId("downgrade")
        val v1 = fixture(packId, version = "2.0.0")
        val downgrade = fixture(packId, version = "1.9.9")
        val registry = MemoryRegistry()
        try {
            v1.installer(registry).install(ByteArrayInputStream(v1.archive))
            expectInstallFailure {
                runBlocking { downgrade.installer(registry).update(ByteArrayInputStream(downgrade.archive), "2.0.0") }
            }
            assertEquals(listOf("2.0.0"), registry.listInstalled().map { it.version })
            assertFalse(File(context.filesDir, "corpus-packs/$packId/1.9.9").exists())
        } finally {
            File(context.filesDir, "corpus-packs/$packId").deleteRecursively()
        }
    }

    private data class Fixture(
        val packId: String,
        val archive: ByteArray,
        val manifest: CorpusPackManifest,
        val indexBytes: ByteArray
    ) {
        fun installer(
            registry: CorpusPackRegistry,
            validator: CorpusIndexValidator = fixtureValidator(indexBytes),
            signatureValid: Boolean = true,
            keyNotBefore: Long = 1L,
            keyRevokedAt: Long? = null
        ) = CorpusPackInstaller(
            context = ApplicationProvider.getApplicationContext(),
            keyring = PublisherKeyring { id ->
                if (id == "publisher-test") TrustedPublisherKey(id, ByteArray(32) { 9 }, keyNotBefore, keyRevokedAt) else null
            },
            signatureVerifier = PackSignatureVerifier { message, signature, publicKey ->
                signatureValid && message.contentEquals(CorpusPackManifestCodec.encode(manifest)) &&
                    signature.size == 64 && publicKey.size == 32
            },
            indexValidator = validator,
            registry = registry,
            clock = { 2_000L }
        )
    }

    private fun fixture(
        packId: String,
        extraEntries: Map<String, ByteArray> = emptyMap(),
        version: String = "1.0.0"
    ): Fixture {
        val index = "fixture-corpus-sqlite".toByteArray(Charsets.UTF_8)
        val manifest = CorpusPackManifest(
            schemaVersion = 1,
            packId = packId,
            packVersion = version,
            createdAtEpochMillis = 1_000L,
            keyId = "publisher-test",
            licenseProfile = "fixture-cleared",
            sourceCount = 1,
            passageCount = 1,
            indexFile = "corpus.sqlite",
            indexSha256 = sha256(index),
            languages = setOf(CorpusLanguage.ARABIC)
        )
        val manifestBytes = CorpusPackManifestCodec.encode(manifest)
        val signature = ByteArray(64) { 5 }
        val entries = linkedMapOf(
            "manifest.txt" to manifestBytes,
            "manifest.sig" to signature,
            "corpus.sqlite" to index
        ) + extraEntries
        return Fixture(packId, zip(entries), manifest, index)
    }

    private class MemoryRegistry(private val failRegistration: Boolean = false) : CorpusPackRegistry {
        val records = mutableListOf<InstalledCorpusPackRecord>()
        override suspend fun isInstalled(packId: String, version: String): Boolean =
            records.any { it.packId == packId && it.version == version }

        override suspend fun register(record: InstalledCorpusPackRecord) {
            if (failRegistration) error("Injected registry failure")
            records += record
        }

        override suspend fun listInstalled(): List<InstalledCorpusPackRecord> = records.filter { it.status == InstalledCorpusPackRecord.STATUS_INSTALLED }
        override suspend fun listAll(): List<InstalledCorpusPackRecord> = records.toList()
        override suspend fun activateReplacement(expectedCurrentVersion: String, replacement: InstalledCorpusPackRecord): Boolean {
            if (failRegistration) return false
            val current = records.singleOrNull { it.packId == replacement.packId && it.version == expectedCurrentVersion && it.status == InstalledCorpusPackRecord.STATUS_INSTALLED } ?: return false
            records[records.indexOf(current)] = current.copy(status = InstalledCorpusPackRecord.STATUS_SUPERSEDED)
            records += replacement
            return true
        }
    }

    private fun uniquePackId(prefix: String) = "$prefix-${java.util.UUID.randomUUID().toString().replace("-", "")}"

    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { output ->
            entries.forEach { (name, value) ->
                output.putNextEntry(ZipEntry(name))
                output.write(value)
                output.closeEntry()
            }
        }
        bytes.toByteArray()
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun expectInstallFailure(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected CorpusPackInstallException")
        } catch (_: CorpusPackInstallException) {
            // Expected rejection.
        }
    }

    companion object {
        private fun fixtureValidator(indexBytes: ByteArray) = object : CorpusIndexValidator {
            override fun validate(
                indexFile: File,
                manifest: CorpusPackManifest,
                asOfEpochMillis: Long
            ): CorpusIndexValidationReport {
                check(indexFile.readBytes().contentEquals(indexBytes)) { "Installer passed unexpected bytes to validator" }
                return CorpusIndexValidationReport(
                    manifest.schemaVersion, manifest.sourceCount, manifest.passageCount, manifest.languages
                )
            }
        }
    }
}
