package app.timeline.islamic

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AttachmentManagementFeatureTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun importIsDisabledUntilEncryptedStorageRecoveryIsReady() {
        composeRule.setContent {
            MaterialTheme {
                AttachmentManagementFeature(
                    state = AttachmentManagementUiState(),
                    recoveryReady = false,
                    onBack = {},
                    onImport = {},
                    onRefresh = {},
                    onPreview = {},
                    onRequestDelete = {},
                    onConfirmDelete = {},
                    onCancelDelete = {},
                    onDismissPreview = {},
                    onPdfPage = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Import document").assertIsNotEnabled()
        composeRule.onNodeWithText("No private documents have been imported.").assertExists()
    }

    @Test
    fun deleteCallbackRequiresExplicitConfirmation() {
        val item = AttachmentUiItem(
            id = "test-attachment",
            mimeType = AttachmentImportPolicy.TEXT_MIME_TYPE,
            sizeBytes = 12,
            importedAtEpochMillis = 1_798_000_000_000L
        )
        val state = mutableStateOf(AttachmentManagementUiState(items = listOf(item)))
        var confirmedDeletes = 0

        composeRule.setContent {
            MaterialTheme {
                AttachmentManagementFeature(
                    state = state.value,
                    recoveryReady = true,
                    onBack = {},
                    onImport = {},
                    onRefresh = {},
                    onPreview = {},
                    onRequestDelete = { selected ->
                        state.value = state.value.copy(deleteConfirmation = selected)
                    },
                    onConfirmDelete = {
                        confirmedDeletes += 1
                        state.value = state.value.copy(deleteConfirmation = null)
                    },
                    onCancelDelete = { state.value = state.value.copy(deleteConfirmation = null) },
                    onDismissPreview = {},
                    onPdfPage = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithText("Delete document?").assertExists()
        composeRule.runOnIdle { assertEquals(0, confirmedDeletes) }
        composeRule.onNodeWithTag("attachment-confirm-delete").performClick()
        composeRule.runOnIdle { assertEquals(1, confirmedDeletes) }
    }

    @Test
    fun importActionIsExposedOnlyAfterRecoveryIsReady() {
        var imports = 0
        composeRule.setContent {
            MaterialTheme {
                AttachmentManagementFeature(
                    state = AttachmentManagementUiState(),
                    recoveryReady = true,
                    onBack = {},
                    onImport = { imports++ },
                    onRefresh = {},
                    onPreview = {},
                    onRequestDelete = {},
                    onConfirmDelete = {},
                    onCancelDelete = {},
                    onDismissPreview = {},
                    onPdfPage = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Import document").performClick()
        composeRule.runOnIdle { assertEquals(1, imports) }
    }
}
