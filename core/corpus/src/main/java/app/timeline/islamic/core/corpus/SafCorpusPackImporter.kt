package app.timeline.islamic.core.corpus

import android.content.Context
import android.net.Uri

/**
 * Small Storage Access Framework bridge. The caller must pass a user-selected content:// URI;
 * the file is streamed directly into the bounded installer and never copied to shared storage.
 */
class SafCorpusPackImporter(context: Context, private val installer: CorpusPackInstaller) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun import(uri: Uri): CorpusPackInstallResult = open(uri).use { installer.install(it) }

    suspend fun update(uri: Uri, expectedCurrentVersion: String): CorpusPackInstallResult =
        open(uri).use { installer.update(it, expectedCurrentVersion) }

    private fun open(uri: Uri) = run {
        require(uri.scheme == "content") { "Corpus packs must be selected through the Storage Access Framework" }
        resolver.openInputStream(uri) ?: throw CorpusPackInstallException("Selected corpus pack cannot be opened")
    }
}
