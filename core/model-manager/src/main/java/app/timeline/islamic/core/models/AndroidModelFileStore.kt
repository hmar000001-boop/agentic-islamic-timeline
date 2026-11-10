package app.timeline.islamic.core.models

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

/** App-private, no-backup artifact storage. No provider-supplied name ever participates in a path. */
class AndroidModelFileStore(noBackupFilesDir: File) : ModelFileStore {
    private val root = File(noBackupFilesDir, ROOT_DIRECTORY_NAME)
    private val canonicalRoot: File

    init {
        if (!root.exists() && !root.mkdirs()) error("Unable to create private model directory")
        require(root.isDirectory && !Files.isSymbolicLink(root.toPath())) { "Model root is not a safe directory" }
        canonicalRoot = root.canonicalFile
    }

    override fun prepare(spec: ModelArtifactSpec) {
        val modelDirectory = checkedChild(root, spec.id)
        if (!modelDirectory.exists() && !modelDirectory.mkdirs()) error("Unable to create model directory")
        require(modelDirectory.isDirectory && !Files.isSymbolicLink(modelDirectory.toPath()) && isInsideManagedRoot(modelDirectory)) {
            "Model directory is not a safe child of the managed root"
        }
        // Persist each directory entry before creating the version directory beneath it.
        syncDirectory(root)
        val directory = checkedChild(modelDirectory, spec.revision)
        if (!directory.exists() && !directory.mkdirs()) error("Unable to create model staging directory")
        require(directory.isDirectory && !Files.isSymbolicLink(directory.toPath()) && isInsideManagedRoot(directory)) {
            "Model version directory is not a safe child of the managed root"
        }
        syncDirectory(modelDirectory)
    }

    override fun stagingFile(spec: ModelArtifactSpec): File = checkedChild(versionDirectory(spec), "${spec.artifactFileName}.partial")

    override fun finalFile(spec: ModelArtifactSpec): File = checkedChild(versionDirectory(spec), spec.artifactFileName)

    override fun installOwnershipMarkerFile(spec: ModelArtifactSpec): File =
        checkedChild(versionDirectory(spec), ".${spec.artifactFileName}.install-owner")

    override fun createInstallOwnershipMarker(spec: ModelArtifactSpec, recordId: String) {
        require(recordId.matches(Regex("[a-f0-9]{64}"))) { "Invalid model install record identifier" }
        val marker = installOwnershipMarkerFile(spec)
        check(marker.createNewFile()) { "An install ownership marker already exists; recovery is required" }
        try {
            FileOutputStream(marker, false).use { output ->
                output.write(recordId.toByteArray(Charsets.US_ASCII))
                output.fd.sync()
            }
            syncDirectory(marker.parentFile ?: error("Missing ownership-marker directory"))
        } catch (failure: Throwable) {
            val cleaned = runCatching {
                val removed = safeDeleteExactFile(marker)
                if (removed) syncDirectory(marker.parentFile ?: error("Missing ownership-marker directory"))
                removed
            }.getOrDefault(false)
            if (!cleaned) {
                throw ModelCommitRecoveryRequired("Model install ownership marker could not be durably cleaned", failure)
            }
            throw failure
        }
    }

    override fun ownsInstallAttempt(spec: ModelArtifactSpec, recordId: String): Boolean {
        val marker = installOwnershipMarkerFile(spec)
        if (!marker.isFile || Files.isSymbolicLink(marker.toPath()) || marker.length() != 64L) return false
        return runCatching { marker.readText(Charsets.US_ASCII) == recordId }.getOrDefault(false)
    }

    override fun deleteInstallOwnershipMarker(spec: ModelArtifactSpec, recordId: String): Boolean {
        val marker = installOwnershipMarkerFile(spec)
        if (!marker.exists()) return true
        if (!ownsInstallAttempt(spec, recordId)) return false
        return safeDeleteExactFile(marker).also { if (it) syncDirectory(versionDirectory(spec)) }
    }

    override fun deleteInterruptedInstallFiles(spec: ModelArtifactSpec, recordId: String): Boolean {
        val staging = stagingFile(spec)
        val destination = finalFile(spec)
        if (destination.exists() || !ownsInstallAttempt(spec, recordId)) return false
        if (!safeDeleteExactFile(staging)) return false
        if (!deleteInstallOwnershipMarker(spec, recordId)) return false
        removeEmptyOwnedDirectories(spec)
        return !staging.exists() && !installOwnershipMarkerFile(spec).exists()
    }

    override fun createStagingFile(spec: ModelArtifactSpec): File {
        val staging = stagingFile(spec)
        require(isInsideManagedRoot(staging)) { "Staging path escaped managed root" }
        check(staging.createNewFile()) { "A staging artifact already exists; recovery is required before import" }
        try {
            syncDirectory(staging.parentFile ?: error("Missing staging directory"))
            return staging
        } catch (failure: Throwable) {
            runCatching { safeDeleteExactFile(staging) }
            throw failure
        }
    }

    override fun commit(staging: File, destination: File) {
        require(isInsideManagedRoot(staging) && isInsideManagedRoot(destination)) { "Model rename path escaped managed root" }
        require(staging.isFile && !Files.isSymbolicLink(staging.toPath())) { "Verified staging artifact is missing or unsafe" }
        require(!destination.exists() && !Files.isSymbolicLink(destination.toPath())) { "Destination already exists or is unsafe" }
        require(staging.parentFile?.canonicalFile == destination.parentFile?.canonicalFile) {
            "Model commit must remain within the same directory/filesystem"
        }
        // POSIX rename replaces an existing destination. Reserve the name with exclusive-create
        // first so the atomic rename can only replace our own empty placeholder, never another file.
        check(destination.createNewFile()) { "Model destination already exists and cannot be reserved" }
        try {
            Os.rename(staging.absolutePath, destination.absolutePath)
        } catch (failure: Throwable) {
            val placeholderRemoved = runCatching {
                val removed = if (staging.exists() && destination.isFile && destination.length() == 0L) {
                    destination.delete() || !destination.exists()
                } else !destination.exists()
                if (removed) syncDirectory(destination.parentFile ?: error("Missing model parent directory"))
                removed
            }.getOrDefault(false)
            if (!placeholderRemoved) {
                throw ModelCommitRecoveryRequired(
                    "Model destination reservation could not be cleaned; install recovery is required",
                    failure
                )
            }
            throw failure
        }
        try {
            syncDirectory(destination.parentFile ?: error("Missing model parent directory"))
        } catch (failure: Throwable) {
            // The rename has consumed staging and produced the destination. Keep the INSTALLING
            // row so the next recovery pass can verify exact size/hash before promoting it.
            throw ModelCommitRecoveryRequired(
                "Model rename completed but directory sync failed; install recovery is required",
                failure
            )
        }
    }

    override fun deleteStagingFile(spec: ModelArtifactSpec): Boolean =
        safeDeleteExactFile(stagingFile(spec)).also { if (it) syncDirectory(versionDirectory(spec)) }

    override fun deleteCommittedFile(spec: ModelArtifactSpec): Boolean =
        safeDeleteExactFile(finalFile(spec)).also { if (it) syncDirectory(versionDirectory(spec)) }

    override fun deleteOwnedFiles(spec: ModelArtifactSpec, recordId: String): Boolean {
        val marker = installOwnershipMarkerFile(spec)
        if (marker.exists() && !ownsInstallAttempt(spec, recordId)) return false
        val directory = versionDirectory(spec)
        if (!isInsideManagedRoot(directory)) return false
        if (Files.isSymbolicLink(directory.toPath())) return false
        val staging = stagingFile(spec)
        val artifact = finalFile(spec)
        if ((staging.exists() || artifact.exists()) && !ownsInstallAttempt(spec, recordId)) return false
        if (!safeDeleteExactFile(staging) || !safeDeleteExactFile(artifact)) return false
        if (marker.exists() && !deleteInstallOwnershipMarker(spec, recordId)) return false
        if (directory.exists()) {
            val remaining = directory.listFiles() ?: return false
            if (remaining.isNotEmpty()) return false
            if (!directory.delete()) return false
            val parent = directory.parentFile ?: return false
            syncDirectory(parent)
        }
        val modelDirectory = directory.parentFile
        if (modelDirectory != null && modelDirectory.exists()) {
            if (Files.isSymbolicLink(modelDirectory.toPath())) return false
            val remaining = modelDirectory.listFiles() ?: return false
            if (remaining.isEmpty() && !modelDirectory.delete()) return false
        }
        syncDirectory(root)
        return !staging.exists() && !artifact.exists()
    }

    override fun isInsideManagedRoot(file: File): Boolean = runCatching {
        val canonical = file.canonicalFile.toPath()
        canonical.startsWith(canonicalRoot.toPath()) && canonical != canonicalRoot.toPath()
    }.getOrDefault(false)

    private fun versionDirectory(spec: ModelArtifactSpec): File {
        require(spec.id.matches(Regex("[a-z0-9][a-z0-9-]{0,63}")))
        require(spec.revision.matches(Regex("[a-f0-9]{40}")))
        val modelDirectory = checkedChild(root, spec.id)
        return checkedChild(modelDirectory, spec.revision)
    }

    private fun checkedChild(parent: File, childName: String): File {
        require(childName.isNotBlank() && childName != "." && childName != ".." && !childName.contains('/') && !childName.contains('\\'))
        val child = File(parent, childName)
        require(isInsideManagedRoot(child)) { "Resolved model path escaped managed root" }
        if (child.exists()) require(!Files.isSymbolicLink(child.toPath())) { "Symlink is not allowed in model storage" }
        return child
    }

    private fun removeEmptyOwnedDirectories(spec: ModelArtifactSpec) {
        val directory = versionDirectory(spec)
        if (directory.exists() && directory.listFiles()?.isEmpty() == true) {
            if (directory.delete()) syncDirectory(directory.parentFile ?: error("Missing model directory parent"))
        }
        val modelDirectory = directory.parentFile
        if (modelDirectory != null && modelDirectory.exists() && modelDirectory.listFiles()?.isEmpty() == true) {
            if (modelDirectory.delete()) syncDirectory(root)
        }
    }

    private fun safeDeleteExactFile(file: File): Boolean {
        if (!isInsideManagedRoot(file)) return false
        if (!file.exists()) return true
        if (Files.isSymbolicLink(file.toPath()) || !file.isFile) return false
        return file.delete() || !file.exists()
    }

    private fun syncDirectory(directory: File) {
        // O_DIRECTORY exists in POSIX/Linux but is not part of Android's public
        // OsConstants SDK surface. Open with the public O_RDONLY flag, then verify
        // both the path and the opened descriptor are the same real directory before
        // fsync. Keep the unresolved absolute path so lstat (rather than stat) can
        // reject a symlink at the directory path instead of silently resolving it.
        require(directory.isDirectory && !Files.isSymbolicLink(directory.toPath())) {
            "Model storage sync target is not a safe directory: ${directory.absolutePath}"
        }
        val path = directory.absolutePath
        val beforeOpen = Os.lstat(path)
        check(OsConstants.S_ISDIR(beforeOpen.st_mode)) {
            "Model storage sync target is not a directory: $path"
        }

        val descriptor = Os.open(path, OsConstants.O_RDONLY, 0)
        try {
            val opened = Os.fstat(descriptor)
            check(OsConstants.S_ISDIR(opened.st_mode)) {
                "Opened model storage sync target is not a directory: $path"
            }
            val currentPath = Os.lstat(path)
            check(
                OsConstants.S_ISDIR(currentPath.st_mode) &&
                    currentPath.st_dev == opened.st_dev &&
                    currentPath.st_ino == opened.st_ino
            ) {
                "Model storage directory changed while opening it: $path"
            }
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }

    companion object { const val ROOT_DIRECTORY_NAME = "on-device-models" }
}
