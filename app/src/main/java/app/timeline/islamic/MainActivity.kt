package app.timeline.islamic

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import app.timeline.islamic.core.persistence.RoomCanvasRepository
import app.timeline.islamic.core.persistence.RoomTimelineRepository
import app.timeline.islamic.feature.canvas.CanvasFeature
import app.timeline.islamic.feature.corpus.CorpusManagementFeature
import app.timeline.islamic.feature.corpus.CorpusManagementUiState
import app.timeline.islamic.feature.corpus.CorpusPackUiItem
import app.timeline.islamic.feature.timeline.TimelineFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppDestination { TIMELINE, CANVAS, CORPUS }

class MainActivity : ComponentActivity() {
    private var corpusState by mutableStateOf(CorpusManagementUiState())
    private var pendingPackUpdateTarget: CorpusPackUiItem? = null

    private val openPublisherKeyring = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) performCorpusAction { importPublisherKeyring(uri) }
    }

    private val openCorpusPack = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val updateTarget = pendingPackUpdateTarget
        pendingPackUpdateTarget = null
        if (uri != null) performCorpusAction {
            if (updateTarget == null) importCorpusPack(uri)
            else updateCorpusPack(uri, updateTarget.packId, updateTarget.version)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val timelineApplication = application as TimelineApplication
        val databaseResult = runCatching { timelineApplication.database }
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { timelineApplication.initializeAgentRuntime() }
        }
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (databaseResult.isSuccess) {
                        val database = databaseResult.getOrThrow()
                        val timelineRepository = androidx.compose.runtime.remember(database) {
                            RoomTimelineRepository(database.timelineDao())
                        }
                        val canvasRepository = androidx.compose.runtime.remember(database) {
                            RoomCanvasRepository(database.canvasDao())
                        }
                        var destination by androidx.compose.runtime.remember { mutableStateOf(AppDestination.TIMELINE) }
                        LaunchedEffect(Unit) { refreshCorpusScreen() }
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                when (destination) {
                                    AppDestination.TIMELINE -> TimelineFeature(timelineRepository) { destination = AppDestination.CANVAS }
                                    AppDestination.CANVAS -> CanvasFeature(canvasRepository) { destination = AppDestination.TIMELINE }
                                    AppDestination.CORPUS -> CorpusManagementFeature(
                                        state = corpusState,
                                        onBack = { destination = AppDestination.TIMELINE },
                                        onRefresh = ::refreshCorpusScreen,
                                        onImportPublisherKeyring = { openPublisherKeyring.launch(ZIP_MIME_TYPES) },
                                        onImportPack = {
                                            pendingPackUpdateTarget = null
                                            openCorpusPack.launch(ZIP_MIME_TYPES)
                                        },
                                        onUpdatePack = { pack: CorpusPackUiItem ->
                                            pendingPackUpdateTarget = pack
                                            openCorpusPack.launch(ZIP_MIME_TYPES)
                                        },
                                        onRemovePack = { pack ->
                                            performCorpusAction { removeCorpusPack(pack.packId, pack.version) }
                                        }
                                    )
                                }
                            }
                            NavigationBar {
                                NavigationBarItem(
                                    selected = destination == AppDestination.TIMELINE,
                                    onClick = { destination = AppDestination.TIMELINE },
                                    icon = { Text("1") },
                                    label = { Text(stringResource(R.string.app_nav_timeline)) }
                                )
                                NavigationBarItem(
                                    selected = destination == AppDestination.CANVAS,
                                    onClick = { destination = AppDestination.CANVAS },
                                    icon = { Text("2") },
                                    label = { Text(stringResource(R.string.app_nav_canvas)) }
                                )
                                NavigationBarItem(
                                    selected = destination == AppDestination.CORPUS,
                                    onClick = { destination = AppDestination.CORPUS },
                                    icon = { Text("3") },
                                    label = { Text(stringResource(R.string.app_nav_corpus)) }
                                )
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.app_storage_open_error))
                        }
                    }
                }
            }
        }
    }

    private fun refreshCorpusScreen() {
        corpusState = corpusState.copy(loading = true, message = null)
        val app = application as TimelineApplication
        lifecycleScope.launch {
            corpusState = withContext(Dispatchers.IO) { app.loadCorpusManagementState() }
        }
    }

    private fun performCorpusAction(action: suspend TimelineApplication.() -> Unit) {
        val app = application as TimelineApplication
        corpusState = corpusState.copy(busy = true, message = null)
        lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { app.action() } }
            val refreshed = runCatching { withContext(Dispatchers.IO) { app.loadCorpusManagementState() } }
            corpusState = refreshed.getOrElse { corpusState.copy(loading = false, busy = false) }.let { state ->
                state.copy(
                    busy = false,
                    message = result.exceptionOrNull()?.let { "Operation failed: ${it::class.simpleName ?: "UNKNOWN"}" }
                        ?: refreshed.exceptionOrNull()?.let { "Refresh failed: ${it::class.simpleName ?: "UNKNOWN"}" }
                )
            }
        }
    }

    private companion object {
        val ZIP_MIME_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
    }
}
