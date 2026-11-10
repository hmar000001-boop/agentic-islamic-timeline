package app.timeline.islamic.core.models

import android.content.Context
import android.net.Uri

/** Opens a user-selected content URI for one import only; no persisted URI permission is requested. */
class SafModelArtifactImporter(context: Context, private val manager: ModelManager) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun import(uri: Uri, modelId: String): InstalledModelRecord {
        require(uri.scheme == "content") { "Only user-selected content:// documents are accepted" }
        // Open lazily: unknown catalog ids or registry failures must not leak a provider stream.
        return manager.import(modelId) {
            resolver.openInputStream(uri) ?: error("The selected document provider could not open the file")
        }
    }
}
