package app.timeline.agentique.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.timeline.agentique.R
import app.timeline.agentique.container
import app.timeline.agentique.core.UiText
import app.timeline.agentique.halal.HalalReport
import app.timeline.agentique.halal.HalalStatus
import app.timeline.agentique.halal.Verdict
import app.timeline.agentique.llm.ModelFiles
import app.timeline.agentique.llm.Prompts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class HalalViewModel(private val app: Application) : AndroidViewModel(app) {
    private val c = app.container
    val text = MutableStateFlow("")
    val report = MutableStateFlow<HalalReport?>(null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<UiText?>(null)

    fun setText(t: String) { text.value = t }

    fun scan(bmp: Bitmap) {
        if (!ModelFiles.installed(app)) { message.value = UiText(R.string.need_model); return }
        busy.value = true
        message.value = null
        viewModelScope.launch {
            c.llm.extractText(bmp, Prompts.LABEL).fold(
                onSuccess = { text.value = it; report.value = null },
                onFailure = { message.value = UiText(R.string.ocr_error, it.message ?: "") },
            )
            busy.value = false
        }
    }

    fun analyze() { report.value = c.halal.analyze(text.value) }
}

@Composable
fun HalalScreen(vm: HalalViewModel = viewModel()) {
    val ctx = LocalContext.current
    val text by vm.text.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val header = stringResource(R.string.halal_share_header)

    Page {
        Note(stringResource(R.string.halal_unreviewed_banner))
        Note(stringResource(R.string.halal_hint))
        ImageButtons(enabled = !busy) { vm.scan(it) }
        if (busy) Text(stringResource(R.string.halal_scan_working))
        message?.let { Text(it.text(), color = MaterialTheme.colorScheme.error) }

        OutlinedTextField(
            value = text, onValueChange = vm::setText,
            label = { Text(stringResource(R.string.halal_text_label)) },
            minLines = 4, modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = vm::analyze, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.halal_analyze))
        }

        report?.let { r ->
            val (verdictText, color) = when (r.verdict) {
                Verdict.HARAM_FOUND -> stringResource(R.string.halal_verdict_haram) to MaterialTheme.colorScheme.error
                Verdict.DOUBTFUL_FOUND -> stringResource(R.string.halal_verdict_doubtful) to MaterialTheme.colorScheme.tertiary
                Verdict.NONE_FLAGGED -> stringResource(R.string.halal_verdict_none) to MaterialTheme.colorScheme.primary
            }
            Text(verdictText, style = MaterialTheme.typography.titleMedium, color = color)
            if (r.verdict == Verdict.NONE_FLAGGED) Note(stringResource(R.string.halal_none_note))

            r.findings.forEach { f ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(if (f.rule.status == HalalStatus.HARAM) R.string.halal_status_haram else R.string.halal_status_doubtful),
                                color = if (f.rule.status == HalalStatus.HARAM) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(f.matched, style = MaterialTheme.typography.titleSmall)
                        }
                        Text(f.rule.noteText())
                        Note(
                            if (f.rule.sources.isEmpty()) stringResource(R.string.halal_no_source)
                            else f.rule.sources.joinToString("، ") { it.title },
                        )
                    }
                }
            }
            if (r.findings.isNotEmpty()) {
                OutlinedButton(onClick = {
                    share(ctx, buildString {
                        appendLine(header)
                        appendLine(text)
                        r.findings.forEach { appendLine("- ${it.matched}: ${it.rule.noteText()}") }
                    })
                }) { Text(stringResource(R.string.halal_ask_scholar)) }
            }
        }
    }
}
