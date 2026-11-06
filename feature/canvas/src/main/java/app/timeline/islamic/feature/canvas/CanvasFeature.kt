package app.timeline.islamic.feature.canvas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.timeline.islamic.core.agent.CanvasDomain
import app.timeline.islamic.core.agent.CanvasSaveResult
import app.timeline.islamic.core.agent.CanvasStatus
import app.timeline.islamic.core.agent.CanvasWorkspace
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasFeature(repository: app.timeline.islamic.core.agent.CanvasRepository, onBack: () -> Unit) {
    val vm: CanvasViewModel = viewModel(factory = remember(repository) { CanvasViewModel.Factory(repository) })
    val state by vm.state.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<CanvasWorkspace?>(null) }
    var createError by remember { mutableStateOf<String?>(null) }
    var selectedError by remember { mutableStateOf<String?>(null) }
    val updatedPrefix = stringResource(R.string.canvas_updated)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.canvas_title)) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.canvas_back)) } }
        )
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(16.dp)) {
            Text(stringResource(R.string.canvas_persistent_workspaces), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.canvas_description), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
            Button(onClick = { createError = null; showCreate = true }) { Text(stringResource(R.string.canvas_create)) }
            when (val current = state) {
                CanvasUiState.Loading -> CircularProgressIndicator(Modifier.padding(24.dp))
                CanvasUiState.StorageUnavailable -> Text(stringResource(R.string.canvas_storage_error), color = MaterialTheme.colorScheme.error)
                is CanvasUiState.Ready -> if (current.workspaces.isEmpty()) {
                    Text(stringResource(R.string.canvas_empty), modifier = Modifier.padding(top = 20.dp))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 16.dp)) {
                        items(current.workspaces, key = { it.id }) { workspace ->
                            Card(Modifier.fillMaxWidth().clickable { selectedError = null; selected = workspace }) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(workspace.title, style = MaterialTheme.typography.titleMedium)
                                    Text("${domainLabel(workspace.domain)} · ${statusLabel(workspace.status)} · ${stringResource(R.string.canvas_version, workspace.version)}",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(updatedPrefix.format(workspace.updatedAt.atZone(java.time.ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))),
                                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) CreateCanvasDialog(externalError = createError, onDismiss = { showCreate = false }) { title, domain, notes ->
        vm.create(title, domain, notes) { result ->
            result.onSuccess { showCreate = false; createError = null }
                .onFailure { createError = "save" }
        }
    }
    selected?.let { workspace ->
        EditCanvasDialog(
            workspace = workspace,
            externalError = selectedError,
            onDismiss = { selected = null },
            onSave = { title, notes ->
                vm.update(workspace, title, notes) { result ->
                    when (result) {
                        is CanvasSaveResult.Saved -> { selected = null; selectedError = null }
                        CanvasSaveResult.Conflict -> selectedError = "conflict"
                        CanvasSaveResult.NotFound -> selectedError = "not_found"
                        CanvasSaveResult.Failed -> selectedError = "save"
                    }
                }
            },
            onArchive = {
                vm.archive(workspace) { result ->
                    when (result) {
                        is CanvasSaveResult.Saved -> { selected = null; selectedError = null }
                        CanvasSaveResult.Conflict -> selectedError = "conflict"
                        CanvasSaveResult.NotFound -> selectedError = "not_found"
                        CanvasSaveResult.Failed -> selectedError = "save"
                    }
                }
            }
        )
    }
}

@Composable
private fun domainLabel(domain: CanvasDomain): String = when (domain) {
    CanvasDomain.GENERAL -> stringResource(R.string.canvas_general)
    CanvasDomain.HIFZ -> stringResource(R.string.canvas_hifz)
    CanvasDomain.TRAVEL -> stringResource(R.string.canvas_travel)
    CanvasDomain.HALAL -> stringResource(R.string.canvas_halal)
    CanvasDomain.ZAKAT -> stringResource(R.string.canvas_zakat)
    CanvasDomain.INHERITANCE -> stringResource(R.string.canvas_inheritance)
    CanvasDomain.RESEARCH -> stringResource(R.string.canvas_research)
}

@Composable
private fun statusLabel(status: CanvasStatus): String = when (status) {
    CanvasStatus.DRAFT -> stringResource(R.string.canvas_draft)
    CanvasStatus.ACTIVE -> stringResource(R.string.canvas_active)
    CanvasStatus.ARCHIVED -> stringResource(R.string.canvas_archived)
}

@Composable
private fun CreateCanvasDialog(
    externalError: String?,
    onDismiss: () -> Unit,
    onCreate: (String, CanvasDomain, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf(CanvasDomain.GENERAL) }
    var error by remember { mutableStateOf<String?>(null) }
    val enterTitle = stringResource(R.string.canvas_enter_title)
    val titleTooLong = stringResource(R.string.canvas_title_too_long)
    val notesTooLong = stringResource(R.string.canvas_notes_too_long)
    val saveFailed = stringResource(R.string.canvas_save_failed)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.canvas_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(title, { title = it; error = null }, label = { Text(stringResource(R.string.canvas_workspace_title)) }, singleLine = true)
                OutlinedTextField(notes, { notes = it; error = null }, label = { Text(stringResource(R.string.canvas_notes)) }, minLines = 3)
                Text(stringResource(R.string.canvas_domain), style = MaterialTheme.typography.labelLarge)
                CanvasDomain.entries.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(domain == option, onClick = { domain = option })
                        Text(domainLabel(option))
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (externalError != null) Text(saveFailed, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = {
            when {
                title.isBlank() -> error = enterTitle
                title.trim().length > 120 -> error = titleTooLong
                notes.length > 100_000 -> error = notesTooLong
                else -> onCreate(title.trim(), domain, notes)
            }
        }) { Text(stringResource(R.string.canvas_create_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.canvas_close)) } }
    )
}

@Composable
private fun EditCanvasDialog(
    workspace: CanvasWorkspace,
    externalError: String?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onArchive: () -> Unit
) {
    var title by remember(workspace.id) { mutableStateOf(workspace.title) }
    var notes by remember(workspace.id) { mutableStateOf(workspace.notes) }
    var error by remember { mutableStateOf<String?>(null) }
    val titleRequired = stringResource(R.string.canvas_title_required)
    val notesTooLong = stringResource(R.string.canvas_notes_too_long)
    val saveFailed = stringResource(R.string.canvas_save_failed)
    val conflict = stringResource(R.string.canvas_conflict)
    val notFound = stringResource(R.string.canvas_not_found)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(workspace.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${domainLabel(workspace.domain)} · ${stringResource(R.string.canvas_version, workspace.version)}", style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(title, { title = it; error = null }, label = { Text(stringResource(R.string.canvas_title_label)) }, singleLine = true)
                OutlinedTextField(notes, { notes = it; error = null }, label = { Text(stringResource(R.string.canvas_notes)) }, minLines = 5)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                when (externalError) {
                    "save" -> Text(saveFailed, color = MaterialTheme.colorScheme.error)
                    "conflict" -> Text(conflict, color = MaterialTheme.colorScheme.error)
                    "not_found" -> Text(notFound, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            if (title.isBlank()) error = titleRequired
            else if (notes.length > 100_000) error = notesTooLong
            else onSave(title.trim(), notes)
        }) { Text(stringResource(R.string.canvas_save_draft)) } },
        dismissButton = {
            Row {
                if (workspace.status != CanvasStatus.ARCHIVED) TextButton(onClick = onArchive) { Text(stringResource(R.string.canvas_archive)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.canvas_close)) }
            }
        }
    )
}
