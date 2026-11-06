package app.timeline.islamic.feature.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import app.timeline.islamic.core.timeline.TimelineItem
import app.timeline.islamic.core.timeline.TimelineItemStatus
import app.timeline.islamic.core.timeline.TimelineItemType
import app.timeline.islamic.core.timeline.TimelineRepository
import app.timeline.islamic.feature.timeline.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineFeature(repository: TimelineRepository, onOpenCanvas: () -> Unit) {
    val vm: TimelineViewModel = viewModel(factory = remember(repository) { TimelineViewModel.Factory(repository) })
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }
    val addDescription = stringResource(R.string.timeline_add)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.timeline_your_day), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.timeline_private_device), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = { TextButton(onClick = onOpenCanvas) { Text(stringResource(R.string.timeline_canvas)) } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showEditor = true }, modifier = Modifier.semantics { contentDescription = addDescription }) { Text("+") }
        }
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { DateHeader(state.selectedDate, onPrevious = { vm.moveDate(-1) }, onNext = { vm.moveDate(1) }, onToday = vm::showToday) }
            item { WeekStrip(state.selectedDate, vm::selectDate) }
            item { FilterRow(state.filter, vm::setFilter) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())),
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.timeline_item_count, state.visibleItems.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (state.isLoading) {
                item { Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Text(stringResource(R.string.timeline_loading), modifier = Modifier.padding(top = 12.dp)) } }
            } else if (state.error != null) {
                item { MessageCard(stringResource(R.string.timeline_storage_error_title), stringResource(R.string.timeline_storage_error_body)) }
            } else if (state.visibleItems.isEmpty()) {
                item { EmptyDayCard(onAdd = { showEditor = true }) }
            } else {
                items(state.visibleItems, key = { it.id }) { item -> TimelineEntry(item, onComplete = { vm.updateStatus(item.id, TimelineItemStatus.COMPLETED) }, onCancel = { vm.updateStatus(item.id, TimelineItemStatus.CANCELLED) }) }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (showEditor) {
        TimelineItemEditor(
            selectedDate = state.selectedDate,
            onDismiss = { showEditor = false },
            onSave = { title, type, date, time ->
                vm.createItem(title, type, date, time)
                showEditor = false
            }
        )
    }
}

@Composable
private fun DateHeader(date: LocalDate, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    val previousDescription = stringResource(R.string.timeline_previous_day)
    val nextDescription = stringResource(R.string.timeline_next_day)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrevious, modifier = Modifier.semantics { contentDescription = previousDescription }) { Text("‹") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onToday) { Text(stringResource(R.string.timeline_today)) }
        }
        TextButton(onClick = onNext, modifier = Modifier.semantics { contentDescription = nextDescription }) { Text("›") }
    }
}

@Composable
private fun WeekStrip(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val firstDay = java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val offsetFromFirstDay = Math.floorMod(selected.dayOfWeek.value - firstDay.value, 7).toLong()
    val start = selected.minusDays(offsetFromFirstDay)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (0L..6L).forEach { offset ->
            val date = start.plusDays(offset)
            val active = date == selected
            Column(
                modifier = Modifier.clickable { onSelect(date) }.background(
                    if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(16.dp)
                ).padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelSmall)
                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun FilterRow(selected: TimelineFilter, onSelect: (TimelineFilter) -> Unit) {
    val labels = listOf(
        TimelineFilter.ALL to R.string.timeline_all, TimelineFilter.EVENTS to R.string.timeline_events,
        TimelineFilter.TASKS to R.string.timeline_tasks, TimelineFilter.HABITS to R.string.timeline_habits,
        TimelineFilter.PROPOSALS to R.string.timeline_suggestions
    )
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(labels) { (filter, label) -> FilterChip(selected == filter, onClick = { onSelect(filter) }, label = { Text(stringResource(label)) }) }
    }
}

@Composable
private fun TimelineEntry(item: TimelineItem, onComplete: () -> Unit, onCancel: () -> Unit) {
    val time = remember(item.startsAt, item.zoneId) {
        runCatching { item.startsAt.atZone(java.time.ZoneId.of(item.zoneId)).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())) }.getOrDefault("—")
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.padding(end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(time, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Surface(Modifier.size(10.dp), shape = CircleShape, color = markerColor(item)) {}
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(typeLabel(item.type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.type == TimelineItemType.PROPOSAL || item.status == TimelineItemStatus.PROPOSED) {
                    Text(stringResource(R.string.timeline_suggestion_unauthorized), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 6.dp))
                } else {
                    Text(statusLabel(item.status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
                if (item.origin == app.timeline.islamic.core.timeline.TimelineItemOrigin.USER && item.status == TimelineItemStatus.CONFIRMED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        if (item.type == TimelineItemType.TASK || item.type == TimelineItemType.HABIT) TextButton(onClick = onComplete) { Text(stringResource(R.string.timeline_complete_action)) }
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.timeline_cancel_action)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun markerColor(item: TimelineItem) = when (item.type) {
    TimelineItemType.PRAYER -> MaterialTheme.colorScheme.primary
    TimelineItemType.PROPOSAL -> MaterialTheme.colorScheme.tertiary
    TimelineItemType.TASK, TimelineItemType.HABIT -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.outline
}

@Composable
private fun typeLabel(type: TimelineItemType): String = when (type) {
    TimelineItemType.PRAYER -> stringResource(R.string.timeline_prayer_type)
    TimelineItemType.EVENT -> stringResource(R.string.timeline_event_type)
    TimelineItemType.TASK -> stringResource(R.string.timeline_task_type)
    TimelineItemType.HABIT -> stringResource(R.string.timeline_habit_type)
    TimelineItemType.PROPOSAL -> stringResource(R.string.timeline_suggestions)
    TimelineItemType.OBSERVATION -> stringResource(R.string.timeline_observation_type)
    TimelineItemType.HISTORY -> stringResource(R.string.timeline_history_type)
}

@Composable
private fun statusLabel(status: TimelineItemStatus): String = when (status) {
    TimelineItemStatus.CONFIRMED -> stringResource(R.string.timeline_confirmed)
    TimelineItemStatus.COMPLETED -> stringResource(R.string.timeline_completed)
    TimelineItemStatus.CANCELLED -> stringResource(R.string.timeline_cancelled)
    TimelineItemStatus.DISMISSED -> stringResource(R.string.timeline_dismissed)
    TimelineItemStatus.ARCHIVED -> stringResource(R.string.timeline_archived)
    TimelineItemStatus.PROPOSED -> stringResource(R.string.timeline_proposed)
}

@Composable
private fun EmptyDayCard(onAdd: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.timeline_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.timeline_empty_body),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
            Button(onClick = onAdd, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.timeline_add)) }
        }
    }
}

@Composable
private fun MessageCard(title: String, message: String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(message, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable
private fun TimelineItemEditor(
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (String, TimelineItemType, LocalDate, LocalTime) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TimelineItemType.EVENT) }
    var dateText by remember(selectedDate) { mutableStateOf(selectedDate.toString()) }
    var timeText by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0).toString().take(5)) }
    var validation by remember { mutableStateOf<String?>(null) }
    val enterTitleMessage = stringResource(R.string.timeline_enter_title)
    val titleTooLongMessage = stringResource(R.string.timeline_title_too_long)
    val invalidDateMessage = stringResource(R.string.timeline_invalid_date)
    val invalidTimeMessage = stringResource(R.string.timeline_invalid_time)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timeline_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it; validation = null }, label = { Text(stringResource(R.string.timeline_title_label)) }, singleLine = true)
                Text(stringResource(R.string.timeline_type_label), style = MaterialTheme.typography.labelLarge)
                listOf(TimelineItemType.EVENT, TimelineItemType.TASK, TimelineItemType.HABIT).forEach { choice ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = type == choice, onClick = { type = choice })
                        Text(when (choice) {
                            TimelineItemType.EVENT -> stringResource(R.string.timeline_event_type)
                            TimelineItemType.TASK -> stringResource(R.string.timeline_task_type)
                            else -> stringResource(R.string.timeline_habit_type)
                        })
                    }
                }
                OutlinedTextField(dateText, { dateText = it; validation = null }, label = { Text(stringResource(R.string.timeline_date_label)) }, singleLine = true)
                OutlinedTextField(timeText, { timeText = it; validation = null }, label = { Text(stringResource(R.string.timeline_time_label)) }, singleLine = true)
                if (validation != null) Text(validation!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = {
            val parsedDate = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
            val parsedTime = runCatching { LocalTime.parse(timeText.trim()) }.getOrNull()
            when {
                title.isBlank() -> validation = enterTitleMessage
                title.trim().length > 160 -> validation = titleTooLongMessage
                parsedDate == null -> validation = invalidDateMessage
                parsedTime == null -> validation = invalidTimeMessage
                else -> onSave(title, type, parsedDate, parsedTime)
            }
        }) { Text(stringResource(R.string.timeline_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.timeline_cancel_button)) } }
    )
}
