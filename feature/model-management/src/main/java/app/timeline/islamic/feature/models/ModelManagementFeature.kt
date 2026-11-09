package app.timeline.islamic.feature.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.timeline.islamic.core.models.ModelLifecycleStatus
import app.timeline.islamic.core.models.ModelPreflightDecision
import app.timeline.islamic.core.models.ModelTask

data class ModelManagementUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val inventoryReadable: Boolean = true,
    val items: List<ModelUiItem> = emptyList(),
    val message: String? = null
)

data class ModelUiItem(
    val id: String,
    val displayName: String,
    val description: String,
    val descriptionArabic: String,
    val task: ModelTask,
    val expectedBytes: Long,
    val licenseId: String,
    val minimumRamBytes: Long?,
    val status: ModelLifecycleStatus?,
    val preflightDecision: ModelPreflightDecision? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementFeature(
    state: ModelManagementUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onImport: (String) -> Unit,
    onRemove: (String) -> Unit,
    onPreflight: (String) -> Unit
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.models_title)) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.models_back)) } },
            actions = { TextButton(onClick = onRefresh, enabled = !state.busy && !state.loading) { Text(stringResource(R.string.models_refresh)) } }
        )
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.models_offline_notice), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.models_inactive_notice), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (state.message != null) Text(state.message, color = MaterialTheme.colorScheme.error)
            when {
                state.loading -> CircularProgressIndicator()
                !state.inventoryReadable -> Text(stringResource(R.string.models_inventory_unavailable), color = MaterialTheme.colorScheme.error)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    items(state.items, key = ModelUiItem::id) { item ->
                        ModelCard(
                            item = item,
                            busy = state.busy,
                            onImport = { onImport(item.id) },
                            onRemove = { onRemove(item.id) },
                            onPreflight = { onPreflight(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    item: ModelUiItem,
    busy: Boolean,
    onImport: () -> Unit,
    onRemove: () -> Unit,
    onPreflight: () -> Unit
) {
    val sizeMb = (item.expectedBytes / (1024L * 1024L)).toInt()
    val isArabic = LocalConfiguration.current.locales[0]?.language == "ar"
    val statusText = when (item.status) {
        null -> stringResource(R.string.models_not_installed)
        ModelLifecycleStatus.INSTALLING -> stringResource(R.string.models_installing)
        ModelLifecycleStatus.INSTALLED -> stringResource(R.string.models_verified_not_active)
        ModelLifecycleStatus.QUARANTINED -> stringResource(R.string.models_quarantined)
        ModelLifecycleStatus.REMOVAL_PENDING -> stringResource(R.string.models_removal_pending)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                if (isArabic) item.descriptionArabic else item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(stringResource(R.string.models_artifact_size, sizeMb), style = MaterialTheme.typography.labelSmall)
            Text(stringResource(R.string.models_license, item.licenseId), style = MaterialTheme.typography.labelSmall)
            item.minimumRamBytes?.let { ram ->
                Text(stringResource(R.string.models_minimum_ram, (ram / (1024L * 1024L * 1024L)).toInt()), style = MaterialTheme.typography.labelSmall)
            }
            Text(statusText, color = if (item.status == ModelLifecycleStatus.QUARANTINED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            item.preflightDecision?.let { decision ->
                val message = when (decision) {
                    ModelPreflightDecision.BLOCKED_LOW_MEMORY -> stringResource(R.string.models_preflight_low_memory)
                    ModelPreflightDecision.BLOCKED_DEVICE_RAM -> stringResource(R.string.models_preflight_device_ram)
                    ModelPreflightDecision.BLOCKED_STORAGE -> stringResource(R.string.models_preflight_storage)
                    ModelPreflightDecision.BLOCKED_THERMAL -> stringResource(R.string.models_preflight_thermal)
                    ModelPreflightDecision.RUNTIME_PROBE_REQUIRED -> stringResource(R.string.models_preflight_runtime_probe)
                }
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.status == null) {
                    Button(onClick = onImport, enabled = !busy) { Text(stringResource(R.string.models_import)) }
                } else if (item.status == ModelLifecycleStatus.INSTALLED || item.status == ModelLifecycleStatus.QUARANTINED || item.status == ModelLifecycleStatus.REMOVAL_PENDING) {
                    OutlinedButton(onClick = onRemove, enabled = !busy) { Text(stringResource(R.string.models_remove)) }
                }
                OutlinedButton(onClick = onPreflight, enabled = !busy) { Text(stringResource(R.string.models_preflight)) }
            }
        }
    }
}
