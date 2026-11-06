package app.timeline.islamic.core.agent

import java.time.Instant
import kotlinx.coroutines.flow.Flow

enum class CanvasStatus { DRAFT, ACTIVE, ARCHIVED }
enum class CanvasDomain { GENERAL, HIFZ, TRAVEL, HALAL, ZAKAT, INHERITANCE, RESEARCH }

data class CanvasWorkspace(
    val id: String,
    val domain: CanvasDomain,
    val title: String,
    val notes: String,
    val status: CanvasStatus,
    val currentStep: String?,
    val version: Long,
    val updatedAt: Instant
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(version >= 1)
    }
}

sealed interface CanvasSaveResult {
    data class Saved(val workspace: CanvasWorkspace) : CanvasSaveResult
    data object Conflict : CanvasSaveResult
    data object NotFound : CanvasSaveResult
    data object Failed : CanvasSaveResult
}

interface CanvasRepository {
    fun observeWorkspaces(): Flow<List<CanvasWorkspace>>
    suspend fun getWorkspace(id: String): CanvasWorkspace?
    suspend fun create(title: String, domain: CanvasDomain, notes: String): CanvasWorkspace
    suspend fun updateDraft(
        id: String,
        expectedVersion: Long,
        title: String,
        notes: String
    ): CanvasSaveResult
    suspend fun archive(id: String, expectedVersion: Long): CanvasSaveResult
}
