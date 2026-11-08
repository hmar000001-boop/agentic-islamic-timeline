package app.timeline.islamic.feature.corpus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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


data class CorpusPackUiItem(
    val packId: String,
    val version: String,
    val status: String,
    val licenseProfile: String,
    val manifestSha256: String
) {
    val stableId: String get() = "$packId@$version"
}

data class CorpusManagementUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val publisherRootConfigured: Boolean = false,
    val publisherRootConfigurationInvalid: Boolean = false,
    val keyringReady: Boolean = false,
    val keyringVersion: Long = 0,
    val packCount: Int = 0,
    val packs: List<CorpusPackUiItem> = emptyList(),
    val message: String? = null,
    val lastRevalidationSummary: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorpusManagementFeature(
    state: CorpusManagementUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onImportPublisherKeyring: () -> Unit,
    onImportPack: () -> Unit,
    onUpdatePack: (CorpusPackUiItem) -> Unit,
    onRemovePack: (CorpusPackUiItem) -> Unit
) {
    var removalTarget by remember { mutableStateOf<CorpusPackUiItem?>(null) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.corpus_title)) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.corpus_back)) } },
            actions = { TextButton(enabled = !state.busy, onClick = onRefresh) { Text(stringResource(R.string.corpus_refresh)) } }
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.corpus_trust_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        when {
                            state.publisherRootConfigurationInvalid -> Text(stringResource(R.string.corpus_root_invalid), color = MaterialTheme.colorScheme.error)
                            !state.publisherRootConfigured -> Text(stringResource(R.string.corpus_root_unconfigured), color = MaterialTheme.colorScheme.error)
                            else -> Text(stringResource(R.string.corpus_root_configured), color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            if (state.keyringReady) stringResource(R.string.corpus_keyring_ready, state.keyringVersion)
                            else stringResource(R.string.corpus_keyring_missing),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = onImportPublisherKeyring,
                            enabled = !state.busy && state.publisherRootConfigured
                        ) { Text(stringResource(R.string.corpus_import_keyring)) }
                        Text(stringResource(R.string.corpus_trust_explanation), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.corpus_index_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.corpus_lexical_active), color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.corpus_embeddings_unavailable), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onImportPack, enabled = !state.busy && state.keyringReady) {
                            Text(stringResource(R.string.corpus_import_pack))
                        }
                    }
                }
            }
            state.lastRevalidationSummary?.let { summary -> item { Text(summary, style = MaterialTheme.typography.bodySmall) } }
            state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            item { Text(stringResource(R.string.corpus_installed_header, state.packs.size), style = MaterialTheme.typography.titleLarge) }
            if (state.loading) {
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
            } else if (state.packs.isEmpty()) {
                item { Text(stringResource(R.string.corpus_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(state.packs, key = { it.stableId }) { pack ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(pack.packId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.corpus_version_status, pack.version, pack.status))
                            Text(stringResource(R.string.corpus_license_profile, pack.licenseProfile), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.corpus_manifest_digest, pack.manifestSha256.take(16)), style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = { onUpdatePack(pack) },
                                    enabled = !state.busy && state.keyringReady && pack.status == "INSTALLED"
                                ) { Text(stringResource(R.string.corpus_update)) }
                                TextButton(onClick = { removalTarget = pack }, enabled = !state.busy) {
                                    Text(stringResource(R.string.corpus_remove), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.padding(bottom = 12.dp)) }
        }
    }

    removalTarget?.let { pack ->
        AlertDialog(
            onDismissRequest = { removalTarget = null },
            title = { Text(stringResource(R.string.corpus_remove_title)) },
            text = { Text(stringResource(R.string.corpus_remove_confirm, pack.packId, pack.version)) },
            confirmButton = {
                TextButton(onClick = { removalTarget = null; onRemovePack(pack) }) {
                    Text(stringResource(R.string.corpus_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removalTarget = null }) { Text(stringResource(R.string.corpus_cancel)) } }
        )
    }
}
