package app.timeline.islamic.core.corpus

import android.content.Context
import app.timeline.islamic.core.knowledge.CorpusPackRegistry
import app.timeline.islamic.core.knowledge.InstalledCorpusPackRecord
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock

/** Coordinates quarantine/removal states with app-private filesystem cleanup and crash recovery. */
class CorpusPackLifecycleManager(context: Context, private val registry: CorpusPackRegistry) {
    private val appContext = context.applicationContext

    suspend fun remove(packId: String, version: String): Boolean = CorpusPackMutationLock.mutex.withLock {
        withContext(Dispatchers.IO) {
            val record = registry.listAll().firstOrNull { it.packId == packId && it.version == version } ?: return@withContext false
            val pending = InstalledCorpusPackRecord.STATUS_REMOVAL_PENDING
            if (record.status != pending && !registry.transitionStatus(record.stableId, record.status, pending)) return@withContext false
            finishRemoval(record)
        }
    }

    /** Completes deletes that were marked REMOVAL_PENDING before a process died. */
    suspend fun recoverPendingRemovals(): Int = CorpusPackMutationLock.mutex.withLock {
        withContext(Dispatchers.IO) {
            val pending = registry.listAll().filter { it.status == InstalledCorpusPackRecord.STATUS_REMOVAL_PENDING }
            var completed = 0
            pending.forEach { if (finishRemoval(it)) completed++ }
            completed
        }
    }

    private suspend fun finishRemoval(record: InstalledCorpusPackRecord): Boolean {
        val root = File(appContext.filesDir, "corpus-packs")
        if (!root.exists()) return registry.removeRecord(record.stableId)
        if (Files.isSymbolicLink(root.toPath()) || !root.isDirectory) return false
        val rootCanonical = root.canonicalFile
        val filesRoot = appContext.filesDir.canonicalFile
        if (!rootCanonical.path.startsWith(filesRoot.path + File.separator)) return false
        val packDirectory = File(root, record.packId)
        val versionDirectory = File(packDirectory, record.version)
        if (Files.isSymbolicLink(packDirectory.toPath()) || Files.isSymbolicLink(versionDirectory.toPath())) return false
        val canonical = versionDirectory.canonicalFile
        if (!canonical.path.startsWith(rootCanonical.path + File.separator)) return false
        if (canonical.exists() && !deleteTreeWithoutFollowingLinks(canonical)) return false
        if (packDirectory.isDirectory && packDirectory.list()?.isEmpty() == true) packDirectory.delete()
        return registry.removeRecord(record.stableId)
    }

    private fun deleteTreeWithoutFollowingLinks(target: File): Boolean {
        return try {
            Files.walkFileTree(target.toPath(), object : SimpleFileVisitor<java.nio.file.Path>() {
                override fun visitFile(file: java.nio.file.Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (Files.isSymbolicLink(file)) throw IOException("Symbolic link found in corpus pack directory")
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }
                override fun postVisitDirectory(dir: java.nio.file.Path, exc: IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    if (Files.isSymbolicLink(dir)) throw IOException("Symbolic link found in corpus pack directory")
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            })
            true
        } catch (_: Exception) { false }
    }
}
