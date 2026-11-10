package app.timeline.islamic.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.timeline.islamic.core.timeline.TimelineItem
import app.timeline.islamic.core.timeline.TimelineItemOrigin
import app.timeline.islamic.core.timeline.TimelineItemStatus
import app.timeline.islamic.core.timeline.TimelineItemType
import app.timeline.islamic.core.timeline.TimelineRepository
import app.timeline.islamic.core.timeline.TimelineWindow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TimelineFilter { ALL, EVENTS, TASKS, HABITS, PROPOSALS }

fun TimelineFilter.matches(item: TimelineItem): Boolean = when (this) {
    TimelineFilter.ALL -> true
    TimelineFilter.EVENTS -> item.type == TimelineItemType.EVENT || item.type == TimelineItemType.PRAYER
    TimelineFilter.TASKS -> item.type == TimelineItemType.TASK
    TimelineFilter.HABITS -> item.type == TimelineItemType.HABIT
    TimelineFilter.PROPOSALS -> item.type == TimelineItemType.PROPOSAL
}

data class TimelineUiState(
    val selectedDate: LocalDate,
    val zoneId: ZoneId,
    val items: List<TimelineItem> = emptyList(),
    val filter: TimelineFilter = TimelineFilter.ALL,
    val isLoading: Boolean = true,
    val error: TimelineLoadError? = null
) {
    val visibleItems: List<TimelineItem>
        get() = items.filter(filter::matches)
}

enum class TimelineLoadError { STORAGE_UNAVAILABLE, INVALID_STORED_ITEM }

class TimelineViewModel(
    private val repository: TimelineRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    initialDate: LocalDate = LocalDate.now(zone)
) : ViewModel() {
    private val selectedDate = MutableStateFlow(initialDate)
    private val selectedFilter = MutableStateFlow(TimelineFilter.ALL)
    private val items = MutableStateFlow<List<TimelineItem>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val loadError = MutableStateFlow<TimelineLoadError?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val observedItems = selectedDate.flatMapLatest { date ->
        val window = TimelineWindow.forDate(date, zone)
        repository.observeItems(window.fromInclusive, window.untilExclusive)
            .onStart { loading.value = true; loadError.value = null }
            .catch { loading.value = false; loadError.value = TimelineLoadError.STORAGE_UNAVAILABLE; emit(emptyList()) }
    }

    val uiState: StateFlow<TimelineUiState> = combine(
        selectedDate, selectedFilter, items, loading, loadError
    ) { date, filter, currentItems, isLoading, error ->
        TimelineUiState(date, zone, currentItems, filter, isLoading, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState(initialDate, zone))

    init {
        viewModelScope.launch {
            observedItems.collect { value -> items.value = value; loading.value = false }
        }
    }

    fun selectDate(date: LocalDate) { selectedDate.value = date }
    fun moveDate(days: Long) { selectedDate.value = selectedDate.value.plusDays(days) }
    fun showToday() { selectedDate.value = LocalDate.now(zone) }
    fun setFilter(filter: TimelineFilter) { selectedFilter.value = filter }

    fun createItem(title: String, type: TimelineItemType, date: LocalDate, startTime: java.time.LocalTime) {
        require(type == TimelineItemType.EVENT || type == TimelineItemType.TASK || type == TimelineItemType.HABIT)
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty() && normalizedTitle.length <= 160)
        val localDateTime = LocalDateTime.of(date, startTime)
        val instant = localDateTime.atZone(zone).toInstant()
        viewModelScope.launch {
            runCatching {
                repository.upsert(TimelineItem(
                    id = UUID.randomUUID().toString(), type = type, title = normalizedTitle,
                    startsAt = instant, endsAt = null, zoneId = zone.id,
                    origin = TimelineItemOrigin.USER, status = TimelineItemStatus.CONFIRMED,
                    sourceVersion = "user-entry-v1"
                ))
            }.onFailure { loadError.value = TimelineLoadError.STORAGE_UNAVAILABLE }
        }
    }

    fun updateStatus(itemId: String, status: TimelineItemStatus) {
        require(status == TimelineItemStatus.COMPLETED || status == TimelineItemStatus.CANCELLED)
        val item = items.value.firstOrNull { it.id == itemId } ?: return
        if (item.origin != TimelineItemOrigin.USER || item.status != TimelineItemStatus.CONFIRMED) return
        viewModelScope.launch {
            runCatching { repository.upsert(item.copy(status = status)) }
                .onFailure { loadError.value = TimelineLoadError.STORAGE_UNAVAILABLE }
        }
    }

    class Factory(private val repository: TimelineRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TimelineViewModel::class.java))
            return TimelineViewModel(repository) as T
        }
    }
}
