package app.timeline.islamic.core.corpus

import android.content.Context
import app.timeline.islamic.core.knowledge.CorpusIndexValidator
import app.timeline.islamic.core.knowledge.CorpusPackManifest
import app.timeline.islamic.core.knowledge.CorpusPackRegistry
import app.timeline.islamic.core.knowledge.InstalledCorpusPackRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class CorpusPackInstallException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class CorpusPackInstallResult(
    val packId: String,
    val version: String,
    val sourceCount: Int,
    val passageCount: Int,
    val manifestSha256: String,
    val installedRelativePath: String
)

/**
 * Imports a signed ZIP from SAF or another user-selected local source. The archive is never extracted
 * into a caller-controlled path. A pack is visible only after signature, hashes and SQLite validation
 * pass and the staging directory is atomically renamed into the installed pack tree.
 */
class CorpusPackInstaller(
    context: Context,
    private val keyring: PublisherKeyring,
    private val signatureVerifier: PackSignatureVerifier,
    private val indexValidator: CorpusIndexValidator,
    private val registry: CorpusPackRegistry,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val appContext = context.applicationContext

    suspend fun install(archive: InputStream): CorpusPackInstallResult = PROCESS_INSTALL_MUTEX.withLock {
        withContext(Dispatchers.IO) {
        val root = File(appContext.filesDir, "corpus-packs")
        if (!root.exists() && !root.mkdirs()) throw CorpusPackInstallException("Unable to create private corpus directory")
        if (!root.isDirectory) throw CorpusPackInstallException("Corpus directory is not a directory")
        val staging = File(root, ".staging-${UUID.randomUUID()}")
        if (!staging.mkdir()) throw CorpusPackInstallException("Unable to create private staging directory")
        var committedDirectory: File? = null
        var registryCommitted = false
        try {
            val extracted = extractArchive(archive, staging)
            val manifestBytes = extracted.manifestBytes ?: fail("manifest.txt is missing")
            val signature = extracted.signatureBytes ?: fail("manifest.sig is missing")
            val manifest = CorpusPackManifestCodec.parse(manifestBytes)
            val key = keyring.find(manifest.keyId) ?: fail("Publisher key '${manifest.keyId}' is not trusted on this build")
            val now = clock()
            if (manifest.createdAtEpochMillis > now) fail("Corpus pack creation time is in the future")
            if (!key.isActiveAt(now)) fail("Publisher key is not active or has been revoked")
            if (key.keyId != manifest.keyId) fail("Publisher key identifier mismatch")
            if (!signatureVerifier.verify(manifestBytes, signature, key.rawPublicKey)) fail("Corpus manifest signature is invalid")

            val index = File(staging, manifest.indexFile)
            if (!index.isFile) fail("Corpus index is missing")
            val indexHash = sha256(index)
            if (!constantTimeHexEquals(indexHash, manifest.indexSha256)) fail("Corpus index SHA-256 does not match the signed manifest")
            val validation = try { indexValidator.validate(index, manifest, now) }
            catch (e: Exception) { throw CorpusPackInstallException("Corpus SQLite validation failed", e) }
            if (validation.schemaVersion != manifest.schemaVersion ||
                validation.sourceCount != manifest.sourceCount ||
                validation.passageCount != manifest.passageCount ||
                validation.languages != manifest.languages) {
                fail("Corpus index metadata does not match its signed manifest")
            }
            val installedRecords = registry.listInstalled()
            if (installedRecords.any { it.packId == manifest.packId }) {
                fail("A version of this pack is already installed; pack replacement requires the dedicated update workflow")
            }

            // Retain exact signed metadata beside the immutable index for later audit and trust revalidation.
            writeSynced(File(staging, "manifest.txt"), manifestBytes)
            writeSynced(File(staging, "manifest.sig"), signature)

            val packParent = File(root, manifest.packId)
            if (!packParent.exists() && !packParent.mkdir()) fail("Unable to create pack destination")
            if (!packParent.isDirectory) fail("Pack destination is not a directory")
            val destination = File(packParent, manifest.packVersion)
            if (destination.exists()) {
                // A previous process may have died after the directory rename but before the Room commit.
                if (!destination.isDirectory || !destination.deleteRecursively()) fail("Unregistered pack destination cannot be safely recovered")
            }
            // The staging directory is under the same app-private root; do not fall back to a non-atomic copy.
            try {
                Files.move(staging.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (e: Exception) {
                throw CorpusPackInstallException("Filesystem does not support atomic corpus installation", e)
            }
            committedDirectory = destination
            val manifestHash = sha256(manifestBytes)
            val record = InstalledCorpusPackRecord(
                packId = manifest.packId,
                version = manifest.packVersion,
                manifestSha256 = manifestHash,
                installedAtEpochMillis = now,
                licenseProfile = manifest.licenseProfile
            )
            try {
                withContext(NonCancellable) {
                    registry.register(record)
                    registryCommitted = true
                }
            } catch (e: Exception) {
                throw CorpusPackInstallException("Pack files were validated but registry commit failed", e)
            }
            CorpusPackInstallResult(
                manifest.packId, manifest.packVersion, validation.sourceCount, validation.passageCount,
                manifestHash, record.relativeIndexPath
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: CorpusPackInstallException) {
            throw e
        } catch (e: Exception) {
            throw CorpusPackInstallException("Corpus pack import failed", e)
        } finally {
            staging.deleteRecursively()
            committedDirectory?.let { directory ->
                // The registry and filesystem cannot share a transaction; a failed commit is rolled back.
                if (!registryCommitted) directory.deleteRecursively()
            }
        }
        }
    }

    private data class ExtractedArchive(val manifestBytes: ByteArray?, val signatureBytes: ByteArray?)

    private fun extractArchive(input: InputStream, staging: File): ExtractedArchive {
        val archiveFile = File(staging, "upload.zip")
        FileOutputStream(archiveFile).use { output ->
            input.use { source ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_ARCHIVE_BYTES) fail("Corpus ZIP exceeds compressed size limit")
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }

        val seen = mutableSetOf<String>()
        var manifest: ByteArray? = null
        var signature: ByteArray? = null
        var expandedBytes = 0L
        val names = setOf("manifest.txt", "manifest.sig", "corpus.sqlite")
        try {
            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry: ZipEntry = entries.nextElement()
                    if (entry.isDirectory || entry.name !in names || entry.name.contains('/') || entry.name.contains('\\')) {
                        fail("Unexpected ZIP entry '${entry.name}'")
                    }
                    if (!seen.add(entry.name)) fail("Duplicate ZIP entry '${entry.name}'")
                    if (entry.size < 0L || entry.compressedSize < 0L) fail("ZIP entry size metadata is invalid")
                    val maximum = when (entry.name) {
                        "manifest.txt" -> MAX_MANIFEST_BYTES.toLong()
                        "manifest.sig" -> SIGNATURE_BYTES.toLong()
                        else -> MAX_INDEX_BYTES
                    }
                    if (entry.size > maximum) fail("ZIP entry '${entry.name}' exceeds size limit")
                    zip.getInputStream(entry).use { source ->
                        when (entry.name) {
                            "manifest.txt" -> manifest = readBoundedBytes(source, MAX_MANIFEST_BYTES) { expandedBytes += it; if (expandedBytes > MAX_EXPANDED_BYTES) fail("Corpus archive expands beyond size limit") }
                            "manifest.sig" -> signature = readBoundedBytes(source, SIGNATURE_BYTES) { expandedBytes += it; if (expandedBytes > MAX_EXPANDED_BYTES) fail("Corpus archive expands beyond size limit") }
                            "corpus.sqlite" -> {
                                val output = File(staging, "corpus.sqlite")
                                FileOutputStream(output).use { sink ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        val count = source.read(buffer)
                                        if (count < 0) break
                                        expandedBytes += count
                                        if (expandedBytes > MAX_EXPANDED_BYTES || output.length() + count > MAX_INDEX_BYTES) fail("Corpus index exceeds the configured size limit")
                                        sink.write(buffer, 0, count)
                                    }
                                    sink.fd.sync()
                                }
                            }
                        }
                    }
                    val actualSize = when (entry.name) {
                        "manifest.txt" -> manifest?.size?.toLong() ?: -1L
                        "manifest.sig" -> signature?.size?.toLong() ?: -1L
                        else -> File(staging, "corpus.sqlite").length()
                    }
                    if (actualSize != entry.size) fail("ZIP entry '${entry.name}' was truncated or expanded inconsistently")
                }
            }
        } finally {
            archiveFile.delete()
        }
        if (seen != names) fail("Archive must contain exactly manifest.txt, manifest.sig and corpus.sqlite")
        if (signature?.size != SIGNATURE_BYTES) fail("Ed25519 signature must be exactly 64 bytes")
        return ExtractedArchive(manifest, signature)
    }

    private inline fun readBoundedBytes(input: InputStream, limit: Int, onBytes: (Int) -> Unit): ByteArray {
        val out = ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (out.size() + count > limit) fail("ZIP entry exceeds size limit")
            onBytes(count)
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    /** Removes only unregistered pack directories left behind by a process death during installation. */
    suspend fun removeUnregisteredPackDirectories(): Int = PROCESS_INSTALL_MUTEX.withLock {
        withContext(Dispatchers.IO) {
            val root = File(appContext.filesDir, "corpus-packs")
            if (!root.isDirectory) return@withContext 0
            val installedIds = registry.listInstalled().map { it.stableId }.toSet()
            var removed = 0
            root.listFiles()?.forEach { packDirectory ->
                if (!packDirectory.isDirectory) return@forEach
                if (packDirectory.name.startsWith(".staging-") || packDirectory.name.startsWith(".incoming-")) {
                    if (packDirectory.deleteRecursively()) removed++
                    return@forEach
                }
                if (!packDirectory.name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))) return@forEach
                packDirectory.listFiles()?.forEach { versionDirectory ->
                    if (versionDirectory.isDirectory && "${packDirectory.name}@${versionDirectory.name}" !in installedIds) {
                        if (versionDirectory.deleteRecursively()) removed++
                    }
                }
            }
            removed
        }
    }

    private fun writeSynced(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
    }

    private fun sha256(file: File): String = file.inputStream().use { stream ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun constantTimeHexEquals(actual: String, expected: String): Boolean {
        if (actual.length != expected.length) return false
        var different = 0
        for (index in actual.indices) different = different or (actual[index].code xor expected[index].code)
        return different == 0
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun fail(message: String): Nothing = throw CorpusPackInstallException(message)

    private companion object {
        val PROCESS_INSTALL_MUTEX = Mutex()
        const val MAX_ARCHIVE_BYTES = 2L * 1024 * 1024 * 1024
        const val MAX_EXPANDED_BYTES = 4L * 1024 * 1024 * 1024
        const val MAX_INDEX_BYTES = 4L * 1024 * 1024 * 1024
        const val MAX_MANIFEST_BYTES = 64 * 1024
        const val SIGNATURE_BYTES = 64
    }
}
