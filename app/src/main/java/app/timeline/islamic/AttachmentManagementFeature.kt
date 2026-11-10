package app.timeline.islamic

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date

internal data class AttachmentUiItem(
    val id: String,
    val mimeType: String,
    val sizeBytes: Long,
    val importedAtEpochMillis: Long
)

internal enum class AttachmentUiMessage {
    IMPORTED,
    IMPORT_FAILED,
    IMPORT_UNSUPPORTED,
    IMPORT_TOO_LARGE,
    DELETED,
    DELETE_CLEANUP_PENDING,
    DELETE_FAILED,
    DELETE_NOT_FOUND,
    PREVIEW_FAILED,
    STORAGE_NOT_READY
}

internal sealed interface AttachmentPreviewUiState {
    val item: AttachmentUiItem

    data class Loading(override val item: AttachmentUiItem) : AttachmentPreviewUiState
    data class Text(
        override val item: AttachmentUiItem,
        val content: String,
        val truncated: Boolean
    ) : AttachmentPreviewUiState
    data class Pdf(
        override val item: AttachmentUiItem,
        val bitmap: Bitmap,
        val pageIndex: Int,
        val pageCount: Int
    ) : AttachmentPreviewUiState
    data class Failed(override val item: AttachmentUiItem) : AttachmentPreviewUiState
}

internal data class AttachmentManagementUiState(
    val loading: Boolean = false,
    val busy: Boolean = false,
    val items: List<AttachmentUiItem> = emptyList(),
    val message: AttachmentUiMessage? = null,
    val preview: AttachmentPreviewUiState? = null,
    val deleteConfirmation: AttachmentUiItem? = null
)

@Composable
internal fun AttachmentManagementFeature(
    state: AttachmentManagementUiState,
    recoveryReady: Boolean,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onRefresh: () -> Unit,
    onPreview: (AttachmentUiItem) -> Unit,
    onRequestDelete: (AttachmentUiItem) -> Unit,
    onConfirmDelete: (AttachmentUiItem) -> Unit,
    onCancelDelete: () -> Unit,
    onDismissPreview: () -> Unit,
    onPdfPage: (AttachmentUiItem, Int) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.attachment_back)) }
            Text(stringResource(R.string.attachment_title), style = MaterialTheme.typography.titleLarge)
        }
        Text(stringResource(R.string.attachment_description), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.attachment_supported_types), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onImport, enabled = recoveryReady && !state.busy) {
                Text(stringResource(R.string.attachment_import))
            }
            OutlinedButton(onClick = onRefresh, enabled = recoveryReady && !state.busy) {
                Text(stringResource(R.string.attachment_refresh))
            }
        }
        if (!recoveryReady) {
            Text(stringResource(R.string.attachment_waiting_for_recovery), color = MaterialTheme.colorScheme.error)
        }
        state.message?.let { message ->
            val resource = when (message) {
                AttachmentUiMessage.IMPORTED -> R.string.attachment_imported
                AttachmentUiMessage.IMPORT_FAILED -> R.string.attachment_import_failed
                AttachmentUiMessage.IMPORT_UNSUPPORTED -> R.string.attachment_import_unsupported
                AttachmentUiMessage.IMPORT_TOO_LARGE -> R.string.attachment_import_too_large
                AttachmentUiMessage.DELETED -> R.string.attachment_deleted
                AttachmentUiMessage.DELETE_CLEANUP_PENDING -> R.string.attachment_deleted_cleanup_pending
                AttachmentUiMessage.DELETE_FAILED -> R.string.attachment_delete_failed
                AttachmentUiMessage.DELETE_NOT_FOUND -> R.string.attachment_delete_not_found
                AttachmentUiMessage.PREVIEW_FAILED -> R.string.attachment_preview_failed
                AttachmentUiMessage.STORAGE_NOT_READY -> R.string.attachment_waiting_for_recovery
            }
            Text(stringResource(resource), color = MaterialTheme.colorScheme.secondary)
        }
        if (state.busy) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text(stringResource(R.string.attachment_working))
            }
        }
        when {
            state.loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.items.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.attachment_empty), style = MaterialTheme.typography.bodyMedium)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items, key = AttachmentUiItem::id) { item ->
                    AttachmentRow(
                        item = item,
                        enabled = recoveryReady && !state.busy,
                        onPreview = { onPreview(item) },
                        onDelete = { onRequestDelete(item) }
                    )
                }
            }
        }
    }

    state.deleteConfirmation?.let { item ->
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text(stringResource(R.string.attachment_delete_title)) },
            text = { Text(stringResource(R.string.attachment_delete_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = { onConfirmDelete(item) },
                    enabled = !state.busy,
                    modifier = Modifier.testTag("attachment-confirm-delete")
                ) {
                    Text(stringResource(R.string.attachment_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete, enabled = !state.busy) {
                    Text(stringResource(R.string.attachment_cancel))
                }
            }
        )
    }

    state.preview?.let { preview ->
        AttachmentPreviewDialog(
            preview = preview,
            onDismiss = onDismissPreview,
            onPdfPage = onPdfPage
        )
    }
}

@Composable
private fun AttachmentRow(
    item: AttachmentUiItem,
    enabled: Boolean,
    onPreview: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(
                    if (item.mimeType == AttachmentImportPolicy.PDF_MIME_TYPE) R.string.attachment_pdf_label
                    else R.string.attachment_text_label
                ),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(
                    R.string.attachment_item_metadata,
                    formatAttachmentSize(item.sizeBytes),
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(item.importedAtEpochMillis))
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onPreview, enabled = enabled) {
                    Text(stringResource(R.string.attachment_preview))
                }
                TextButton(onClick = onDelete, enabled = enabled) {
                    Text(stringResource(R.string.attachment_delete))
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreviewDialog(
    preview: AttachmentPreviewUiState,
    onDismiss: () -> Unit,
    onPdfPage: (AttachmentUiItem, Int) -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(
                            if (preview.item.mimeType == AttachmentImportPolicy.PDF_MIME_TYPE) R.string.attachment_pdf_label
                            else R.string.attachment_text_label
                        ),
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.attachment_close)) }
                }
                when (preview) {
                    is AttachmentPreviewUiState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    is AttachmentPreviewUiState.Failed -> Text(stringResource(R.string.attachment_preview_failed))
                    is AttachmentPreviewUiState.Text -> {
                        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                            Text(preview.content, style = MaterialTheme.typography.bodyMedium)
                            if (preview.truncated) {
                                Text(stringResource(R.string.attachment_preview_truncated), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    is AttachmentPreviewUiState.Pdf -> {
                        Image(
                            bitmap = preview.bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.attachment_pdf_page_description, preview.pageIndex + 1),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                            contentScale = ContentScale.Fit
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { onPdfPage(preview.item, preview.pageIndex - 1) },
                                enabled = preview.pageIndex > 0
                            ) { Text(stringResource(R.string.attachment_previous_page)) }
                            Text(stringResource(R.string.attachment_page_count, preview.pageIndex + 1, preview.pageCount))
                            TextButton(
                                onClick = { onPdfPage(preview.item, preview.pageIndex + 1) },
                                enabled = preview.pageIndex + 1 < preview.pageCount
                            ) { Text(stringResource(R.string.attachment_next_page)) }
                        }
                    }
                }
            }
        }
    }
}

private fun formatAttachmentSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${DecimalFormat("0.#").format(bytes / 1024.0)} KB"
    else -> "${DecimalFormat("0.#").format(bytes / (1024.0 * 1024.0))} MB"
}
