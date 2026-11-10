package app.timeline.islamic.feature.canvas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.timeline.islamic.core.agent.CanvasDomain
import app.timeline.islamic.core.agent.CanvasRepository
import app.timeline.islamic.core.agent.CanvasSaveResult
import app.timeline.islamic.core.agent.CanvasWorkspace
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CanvasUiState {
    data object Loading : CanvasUiState
    data class Ready(val workspaces: List<CanvasWorkspace>) : CanvasUiState
    data object StorageUnavailable : CanvasUiState
}

class CanvasViewModel(private val repository: CanvasRepository) : ViewModel() {
    val state: StateFlow<CanvasUiState> = repository.observeWorkspaces()
        .map<List<CanvasWorkspace>, CanvasUiState> { CanvasUiState.Ready(it) }
        .catch { emit(CanvasUiState.StorageUnavailable) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CanvasUiState.Loading)

    fun create(title: String, domain: CanvasDomain, notes: String, onResult: (Result<CanvasWorkspace>) -> Unit) {
        viewModelScope.launch { onResult(runCatching { repository.create(title, domain, notes) }) }
    }

    fun update(workspace: CanvasWorkspace, title: String, notes: String, onResult: (CanvasSaveResult) -> Unit) {
        viewModelScope.launch { onResult(runCatching { repository.updateDraft(workspace.id, workspace.version, title, notes) }.getOrElse { CanvasSaveResult.Failed }) }
    }

    fun archive(workspace: CanvasWorkspace, onResult: (CanvasSaveResult) -> Unit) {
        viewModelScope.launch { onResult(runCatching { repository.archive(workspace.id, workspace.version) }.getOrElse { CanvasSaveResult.Failed }) }
    }

    class Factory(private val repository: CanvasRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CanvasViewModel::class.java))
            return CanvasViewModel(repository) as T
        }
    }
}
