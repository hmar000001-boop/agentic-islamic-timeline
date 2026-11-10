package app.timeline.islamic.core.corpus

import android.content.Context
import android.net.Uri

/** Opens a keyring archive only from a user-selected SAF content URI. */
class SafPublisherKeyringImporter(context: Context, private val importer: SignedPublisherKeyringArchiveImporter) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun import(uri: Uri): Long {
        require(uri.scheme == "content") { "Publisher keyrings must be selected through the Storage Access Framework" }
        val stream = resolver.openInputStream(uri) ?: throw PublisherKeyringException("Selected publisher keyring cannot be opened")
        return importer.import(stream)
    }
}
