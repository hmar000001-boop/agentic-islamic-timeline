package app.timeline.agentique.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.timeline.agentique.R
import app.timeline.agentique.container
import app.timeline.agentique.core.AppSettings
import app.timeline.agentique.core.UiText
import app.timeline.agentique.llm.ModelFiles
import app.timeline.agentique.llm.Prompts
import app.timeline.agentique.quran.Grade
import app.timeline.agentique.quran.HifzItem
import app.timeline.agentique.quran.HifzPlanner
import app.timeline.agentique.quran.MushafMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface PhotoState {
    data object Idle : PhotoState
    data object Working : PhotoState
    data object NoMatch : PhotoState
    data class Found(val match: MushafMatch) : PhotoState
    data class Failed(val message: UiText) : PhotoState
}

class HifzViewModel(private val app: Application) : AndroidViewModel(app) {
    private val c = app.container
    val data = c.hifz.data
    val corpusReady = MutableStateFlow<Boolean?>(null)
    val session = MutableStateFlow<List<HifzItem>>(emptyList())
    val index = MutableStateFlow(0)
    val photo = MutableStateFlow<PhotoState>(PhotoState.Idle)

    init { viewModelScope.launch { corpusReady.value = c.corpus.load() } }

    private fun today() = LocalDate.now().toEpochDay()
    fun suraLength(sura: Int) = c.corpus.suraLength(sura)
    fun ayatText(item: HifzItem): String =
        c.corpus.range(item.sura, item.from, item.to).joinToString(" ") { it.text }

    fun add(sura: Int, from: Int, to: Int) { viewModelScope.launch { c.hifz.add(sura, from, to, today()) } }
    fun remove(id: String) { viewModelScope.launch { c.hifz.remove(id) } }

    fun startSession(minutes: Int) {
        session.value = HifzPlanner.session(data.value.items, today(), minutes)
        index.value = 0
    }

    fun grade(item: HifzItem, g: Grade) {
        viewModelScope.launch { c.hifz.grade(item.id, g, today()) }
        index.value += 1
    }

    fun identify(bmp: Bitmap) {
        if (!ModelFiles.installed(app)) { photo.value = PhotoState.Failed(UiText(R.string.need_model)); return }
        photo.value = PhotoState.Working
        viewModelScope.launch {
            if (!c.corpus.load()) { photo.value = PhotoState.Failed(UiText(R.string.hifz_corpus_missing)); return@launch }
            c.llm.extractText(bmp, Prompts.MUSHAF).fold(
                onSuccess = { text ->
                    val m = withContext(Dispatchers.Default) { c.corpus.match(text) }
                    photo.value = if (m != null) PhotoState.Found(m) else PhotoState.NoMatch
                },
                onFailure = { e -> photo.value = PhotoState.Failed(UiText(R.string.ocr_error, e.message ?: "")) },
            )
        }
    }
}

@Composable
fun HifzScreen(vm: HifzViewModel = viewModel()) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val data by vm.data.collectAsStateWithLifecycle()
    val ready by vm.corpusReady.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val idx by vm.index.collectAsStateWithLifecycle()
    val photo by vm.photo.collectAsStateWithLifecycle()
    val settings by ctx.container.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val names = stringArrayResource(R.array.surah_names)
    val today = LocalDate.now().toEpochDay()

    Page {
        if (ready == false) Text(stringResource(R.string.hifz_corpus_missing), color = MaterialTheme.colorScheme.error)

        if (session.isNotEmpty() && idx < session.size) {
            SessionCard(vm, session[idx], idx, session.size, names)
        } else {
            if (session.isNotEmpty()) Text(stringResource(R.string.hifz_session_done), style = MaterialTheme.typography.titleMedium)
            val due = HifzPlanner.due(data.items, today)
            if (due.isEmpty()) Note(stringResource(R.string.hifz_nothing_due))
            else Button(onClick = { vm.startSession(settings.hifzMinutes) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hifz_start, settings.hifzMinutes))
            }
        }

        SectionTitle(stringResource(R.string.hifz_photo_title))
        Note(stringResource(R.string.hifz_photo_hint))
        ImageButtons(enabled = photo != PhotoState.Working) { vm.identify(it) }
        when (val p = photo) {
            PhotoState.Idle -> Unit
            PhotoState.Working -> Text(stringResource(R.string.hifz_photo_working))
            PhotoState.NoMatch -> Text(stringResource(R.string.hifz_photo_none))
            is PhotoState.Failed -> Text(p.message.text(), color = MaterialTheme.colorScheme.error)
            is PhotoState.Found -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.hifz_photo_found, names.getOrElse(p.match.sura - 1) { "" },
                            p.match.from, p.match.to, (p.match.coverage * 100).toInt(),
                        ),
                    )
                    Button(onClick = { vm.add(p.match.sura, p.match.from, p.match.to) }) {
                        Text(stringResource(R.string.hifz_photo_add))
                    }
                }
            }
        }

        AddForm(vm, names, ready == true)

        SectionTitle(stringResource(R.string.hifz_plan))
        if (data.items.isEmpty()) Note(stringResource(R.string.hifz_empty))
        data.items.forEach { item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.hifz_item, names.getOrElse(item.sura - 1) { "" }, item.from, item.to))
                    val d = (item.dueDay - today).toInt()
                    Note(if (d <= 0) stringResource(R.string.hifz_due_today) else stringResource(R.string.hifz_due_in, d))
                }
                IconButton(onClick = { vm.remove(item.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        }
    }
}

@Composable
private fun SessionCard(vm: HifzViewModel, item: HifzItem, idx: Int, total: Int, names: Array<String>) {
    var shown by remember(item.id, idx) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Note(stringResource(R.string.hifz_progress, idx + 1, total))
            Text(
                stringResource(R.string.hifz_item, names.getOrElse(item.sura - 1) { "" }, item.from, item.to),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (shown) vm.ayatText(item) else "• • •",
                fontSize = 24.sp, lineHeight = 44.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { shown = !shown }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (shown) R.string.hifz_hide else R.string.hifz_reveal))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    Grade.AGAIN to R.string.grade_again, Grade.HARD to R.string.grade_hard,
                    Grade.GOOD to R.string.grade_good, Grade.EASY to R.string.grade_easy,
                ).forEach { (g, label) ->
                    Button(onClick = { vm.grade(item, g) }, modifier = Modifier.weight(1f)) { Text(stringResource(label)) }
                }
            }
        }
    }
}

@Composable
private fun AddForm(vm: HifzViewModel, names: Array<String>, enabled: Boolean) {
    var sura by rememberSaveable { mutableIntStateOf(1) }
    var from by rememberSaveable { mutableStateOf("1") }
    var to by rememberSaveable { mutableStateOf("1") }
    var open by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }

    SectionTitle(stringResource(R.string.hifz_add_title))
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("${stringResource(R.string.hifz_surah)}: ${names.getOrElse(sura - 1) { "" }}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            names.forEachIndexed { i, n -> DropdownMenuItem(text = { Text("${i + 1}. $n") }, onClick = { sura = i + 1; open = false }) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { NumField(stringResource(R.string.hifz_from), from, { from = it }, androidx.compose.ui.text.input.KeyboardType.Number) }
        Box(Modifier.weight(1f)) { NumField(stringResource(R.string.hifz_to), to, { to = it }, androidx.compose.ui.text.input.KeyboardType.Number) }
    }
    if (invalid) Text(stringResource(R.string.hifz_invalid_range), color = MaterialTheme.colorScheme.error)
    Button(enabled = enabled, onClick = {
        val f = app.timeline.agentique.core.parseNum(from)?.toInt()
        val t = app.timeline.agentique.core.parseNum(to)?.toInt()
        val len = vm.suraLength(sura)
        invalid = f == null || t == null || f < 1 || t < f || t > len
        if (!invalid) vm.add(sura, f!!, t!!)
    }) { Text(stringResource(R.string.hifz_add)) }
}
