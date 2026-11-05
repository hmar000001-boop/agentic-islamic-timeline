package app.timeline.agentique.ui

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.flow.SharingStarted
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.timeline.agentique.R
import app.timeline.agentique.agent.Act
import app.timeline.agentique.agent.TimelineBuilder
import app.timeline.agentique.agent.TimelineCard
import app.timeline.agentique.agent.TimelineInput
import app.timeline.agentique.container
import app.timeline.agentique.core.DeviceLocation
import app.timeline.agentique.core.MethodOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

class TimelineViewModel(private val app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val ticker = flow { while (true) { emit(ZonedDateTime.now()); delay(30_000) } }
    private val locationTick = MutableStateFlow(0)

    val cards: StateFlow<List<TimelineCard>> =
        combine(c.settings.flow, c.hifz.data, ticker, locationTick) { s, h, now, _ ->
            val device = DeviceLocation.lastKnown(app)
            val at = (if (s.useDeviceLocation) device else null) ?: s.home
            val labels = app.resources.getStringArray(R.array.method_labels)
            val label = labels.getOrElse(MethodOptions.names.indexOf(s.method)) { s.method }
            TimelineBuilder.build(TimelineInput(now, s, at, device, h, label))
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refreshLocation() { locationTick.value += 1 }

    fun dismiss(card: TimelineCard) {
        viewModelScope.launch {
            val today = LocalDate.now().toEpochDay()
            c.settings.update { s ->
                s.copy(dismissed = s.dismissed.filter { it.startsWith("$today:") }.toSet() + "$today:${card.id}")
            }
        }
    }

    fun perform(act: Act, navigate: (String) -> Unit) {
        when (act) {
            is Act.Open -> navigate(act.route)
            is Act.SetHifzMinutes -> viewModelScope.launch { c.settings.update { it.copy(hifzMinutes = act.minutes) } }
            Act.EnableTravel -> viewModelScope.launch { c.settings.update { it.copy(travelMode = true) } }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(onNavigate: (String) -> Unit, vm: TimelineViewModel = viewModel()) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    var sourcesFor by remember { mutableStateOf<TimelineCard?>(null) }
    val listState = rememberLazyListState()

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.refreshLocation() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val highlightIdx = cards.indexOfFirst { it.highlight }
    LaunchedEffect(highlightIdx >= 0) { if (highlightIdx > 0) listState.scrollToItem(highlightIdx) }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cards, key = { it.id }) { card ->
            CardItem(
                card = card,
                onDismiss = { vm.dismiss(card) },
                onLongPress = { sourcesFor = card },
                onAct = { vm.perform(it, onNavigate) },
            )
        }
    }

    sourcesFor?.let { card ->
        AlertDialog(
            onDismissRequest = { sourcesFor = null },
            confirmButton = { TextButton(onClick = { sourcesFor = null }) { Text(stringResource(R.string.close)) } },
            title = { Text(stringResource(R.string.sources_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (card.sources.isEmpty()) Text("—") else card.sources.forEach { Text(it.text()) }
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CardItem(card: TimelineCard, onDismiss: () -> Unit, onLongPress: () -> Unit, onAct: (Act) -> Unit) {
    if (!card.dismissible) {
        CardBody(card, onLongPress, onAct)
        return
    }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v != SwipeToDismissBoxValue.Settled) { onDismiss(); true } else false
        },
    )
    SwipeToDismissBox(state = state, backgroundContent = {}) { CardBody(card, onLongPress, onAct) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardBody(card: TimelineCard, onLongPress: () -> Unit, onAct: (Act) -> Unit) {
    val container = when {
        card.highlight -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (card.past) 0.55f else 1f)
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(card.title.text(), style = MaterialTheme.typography.titleMedium)
            card.body?.let { Text(it.text(), style = MaterialTheme.typography.bodyMedium) }
            if (card.actions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    card.actions.forEach { a -> Button(onClick = { onAct(a.act) }) { Text(a.label.text()) } }
                }
            }
        }
    }
}
