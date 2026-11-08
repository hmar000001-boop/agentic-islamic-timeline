package app.timeline.islamic.core.corpus

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Imports a two-file root-signed keyring package without extracting caller-controlled paths. */
class SignedPublisherKeyringArchiveImporter(private val manager: PublisherKeyringManager) {
    suspend fun import(input: InputStream): Long {
        val archive = readArchive(input)
        return manager.install(archive.first, archive.second)
    }

    private fun readArchive(input: InputStream): Pair<ByteArray, ByteArray> {
        var archiveBytes = 0L
        val counted = object : java.io.FilterInputStream(input) {
            override fun read(): Int {
                val value = super.read()
                if (value >= 0 && ++archiveBytes > MAX_ARCHIVE_BYTES) throw IllegalArgumentException("Keyring archive exceeds size limit")
                return value
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val count = super.read(buffer, offset, length)
                if (count > 0 && (archiveBytes + count).also { archiveBytes = it } > MAX_ARCHIVE_BYTES) {
                    throw IllegalArgumentException("Keyring archive exceeds size limit")
                }
                return count
            }
        }
        var payload: ByteArray? = null
        var signature: ByteArray? = null
        val names = mutableSetOf<String>()
        var expandedTotal = 0L
        ZipInputStream(counted).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory) { "Directory entries are not permitted in keyring archives" }
                require(entry.name in setOf("keyring.txt", "keyring.sig")) { "Unexpected keyring archive entry" }
                require(names.add(entry.name)) { "Duplicate keyring archive entry" }
                val limit = if (entry.name == "keyring.txt") MAX_PAYLOAD_BYTES else SIGNATURE_BYTES
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    require(out.size() + count <= limit) { "Keyring entry exceeds size limit" }
                    expandedTotal += count
                    require(expandedTotal <= MAX_EXPANDED_BYTES) { "Keyring archive expands beyond limit" }
                    out.write(buffer, 0, count)
                }
                val bytes = out.toByteArray()
                if (entry.name == "keyring.txt") payload = bytes else signature = bytes
                zip.closeEntry()
            }
        }
        require(names == setOf("keyring.txt", "keyring.sig")) { "Archive must contain exactly keyring.txt and keyring.sig" }
        require(signature?.size == SIGNATURE_BYTES) { "Root Ed25519 signature must be exactly 64 bytes" }
        return payload!! to signature!!
    }

    private companion object {
        const val MAX_ARCHIVE_BYTES = 512 * 1024L
        const val MAX_EXPANDED_BYTES = 300 * 1024L
        const val MAX_PAYLOAD_BYTES = 256 * 1024
        const val SIGNATURE_BYTES = 64
    }
}
