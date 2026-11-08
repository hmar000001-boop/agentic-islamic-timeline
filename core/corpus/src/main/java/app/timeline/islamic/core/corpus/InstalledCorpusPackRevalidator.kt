package app.timeline.islamic.core.corpus

import android.content.Context
import app.timeline.islamic.core.knowledge.CorpusIndexValidator
import app.timeline.islamic.core.knowledge.CorpusPackRegistry
import app.timeline.islamic.core.knowledge.InstalledCorpusPackRecord
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Startup integrity/trust sweep. Any inconsistency removes the pack from the active retrieval set. */
class InstalledCorpusPackRevalidator(
    context: Context,
    private val registry: CorpusPackRegistry,
    private val keyring: PublisherKeyring,
    private val signatureVerifier: PackSignatureVerifier,
    private val indexValidator: CorpusIndexValidator,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val appContext = context.applicationContext

    data class Report(val checked: Int, val healthy: Int, val quarantined: Int, val restored: Int, val skipped: Int)

    suspend fun revalidateAll(): Report = withContext(Dispatchers.IO) {
        var checked = 0; var healthy = 0; var quarantined = 0; var restored = 0; var skipped = 0
        val records = registry.listAll()
        for (record in records) {
            if (record.status !in setOf(InstalledCorpusPackRecord.STATUS_INSTALLED, InstalledCorpusPackRecord.STATUS_QUARANTINED)) {
                skipped++
                continue
            }
            checked++
            val valid = runCatching { validate(record) }.getOrDefault(false)
            if (valid) {
                if (record.status == InstalledCorpusPackRecord.STATUS_QUARANTINED && registry.transitionStatus(
                        record.stableId, InstalledCorpusPackRecord.STATUS_QUARANTINED, InstalledCorpusPackRecord.STATUS_INSTALLED
                    )) restored++ else healthy++
            } else if (record.status == InstalledCorpusPackRecord.STATUS_INSTALLED && registry.transitionStatus(
                    record.stableId, InstalledCorpusPackRecord.STATUS_INSTALLED, InstalledCorpusPackRecord.STATUS_QUARANTINED
                )) quarantined++
            else skipped++
        }
        Report(checked, healthy, quarantined, restored, skipped)
    }

    private fun validate(record: InstalledCorpusPackRecord): Boolean {
        val now = clock()
        val root = File(appContext.filesDir, "corpus-packs")
        if (!root.isDirectory || Files.isSymbolicLink(root.toPath())) return false
        val rootCanonical = root.canonicalFile
        val appFiles = appContext.filesDir.canonicalFile
        if (!rootCanonical.path.startsWith(appFiles.path + File.separator)) return false
        val packDirectory = File(root, record.packId)
        val versionDirectory = File(packDirectory, record.version)
        if (Files.isSymbolicLink(packDirectory.toPath()) || Files.isSymbolicLink(versionDirectory.toPath())) return false
        if (!packDirectory.isDirectory || !versionDirectory.isDirectory) return false
        val canonicalDirectory = versionDirectory.canonicalFile
        if (!canonicalDirectory.path.startsWith(rootCanonical.path + File.separator)) return false

        val manifestFile = File(canonicalDirectory, "manifest.txt")
        val signatureFile = File(canonicalDirectory, "manifest.sig")
        val indexFile = File(canonicalDirectory, "corpus.sqlite")
        if (listOf(manifestFile, signatureFile, indexFile).any { Files.isSymbolicLink(it.toPath()) || !it.isFile }) return false
        if (manifestFile.length() !in 1..MAX_MANIFEST_BYTES.toLong() || signatureFile.length() != SIGNATURE_BYTES.toLong()) return false
        val manifestBytes = manifestFile.readBytes()
        val signature = signatureFile.readBytes()
        val manifest = CorpusPackManifestCodec.parse(manifestBytes)
        if (manifest.packId != record.packId || manifest.packVersion != record.version || manifest.licenseProfile != record.licenseProfile) return false
        if (!constantTimeEquals(sha256(manifestBytes), record.manifestSha256)) return false
        if (manifest.createdAtEpochMillis > now) return false
        val publisher = keyring.find(manifest.keyId) ?: return false
        if (!publisher.isActiveAt(manifest.createdAtEpochMillis) || !publisher.isActiveAt(now)) return false
        if (!signatureVerifier.verify(manifestBytes, signature, publisher.rawPublicKey)) return false
        if (!constantTimeEquals(sha256(indexFile), manifest.indexSha256)) return false
        val validation = indexValidator.validate(indexFile, manifest, now)
        return validation.schemaVersion == manifest.schemaVersion &&
            validation.sourceCount == manifest.sourceCount &&
            validation.passageCount == manifest.passageCount &&
            validation.languages == manifest.languages
    }

    private fun sha256(file: File): String = file.inputStream().use { stream ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        digest.digest().toHex()
    }
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private companion object { const val MAX_MANIFEST_BYTES = 64 * 1024; const val SIGNATURE_BYTES = 64 }
}
