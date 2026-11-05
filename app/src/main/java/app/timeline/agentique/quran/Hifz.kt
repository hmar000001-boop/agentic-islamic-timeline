package app.timeline.agentique.quran

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

@Serializable
data class HifzItem(
    val id: String,
    val sura: Int,
    val from: Int,
    val to: Int,
    val dueDay: Long,
    val reps: Int = 0,
    val intervalDays: Int = 0,
    val ease: Double = 2.3,
    val lapses: Int = 0,
)

@Serializable
data class HifzData(val items: List<HifzItem> = emptyList(), val lastReviewDay: Long? = null)

enum class Grade { AGAIN, HARD, GOOD, EASY }

/** Simplified SM-2 spaced repetition. */
object Sm2 {
    fun next(i: HifzItem, g: Grade, today: Long): HifzItem {
        val ease = when (g) {
            Grade.AGAIN -> max(1.3, i.ease - 0.2)
            Grade.HARD -> max(1.3, i.ease - 0.15)
            Grade.GOOD -> i.ease
            Grade.EASY -> i.ease + 0.15
        }
        val interval = when (g) {
            Grade.AGAIN -> 1
            Grade.HARD -> max(1, (max(i.intervalDays, 1) * 1.2).roundToInt())
            Grade.GOOD -> when (i.reps) {
                0 -> 1
                1 -> 3
                else -> max(1, (i.intervalDays * ease).roundToInt())
            }
            Grade.EASY -> when (i.reps) {
                0 -> 3
                1 -> 6
                else -> max(1, (i.intervalDays * ease * 1.3).roundToInt())
            }
        }
        return i.copy(
            reps = if (g == Grade.AGAIN) 0 else i.reps + 1,
            lapses = if (g == Grade.AGAIN) i.lapses + 1 else i.lapses,
            ease = ease,
            intervalDays = interval,
            dueDay = today + interval,
        )
    }
}

object HifzPlanner {
    const val SECONDS_PER_AYAH = 25

    fun seconds(i: HifzItem): Int = (i.to - i.from + 1) * SECONDS_PER_AYAH

    fun due(items: List<HifzItem>, today: Long): List<HifzItem> =
        items.filter { it.dueDay <= today }.sortedBy { it.dueDay }

    /** Fills the time budget with due items (always at least one, so a long passage is never skipped). */
    fun session(items: List<HifzItem>, today: Long, minutes: Int): List<HifzItem> {
        var budget = minutes * 60
        val out = ArrayList<HifzItem>()
        for (i in due(items, today)) {
            if (out.isNotEmpty() && budget - seconds(i) < 0) break
            out += i
            budget -= seconds(i)
        }
        return out
    }

    fun sessionMinutes(session: List<HifzItem>): Int =
        max(1, Math.ceil(session.sumOf { seconds(it) } / 60.0).toInt())

    fun daysSince(lastReviewDay: Long?, today: Long): Int = lastReviewDay?.let { (today - it).toInt() } ?: 0
}

class HifzStore(ctx: Context) {
    private val file = File(ctx.filesDir, "hifz.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val state = MutableStateFlow(read())
    val data: StateFlow<HifzData> = state

    private fun read(): HifzData =
        runCatching { json.decodeFromString<HifzData>(file.readText()) }.getOrDefault(HifzData())

    private suspend fun mutate(f: (HifzData) -> HifzData) = withContext(Dispatchers.IO) {
        state.update(f)
        file.writeText(json.encodeToString(state.value))
    }

    suspend fun add(sura: Int, from: Int, to: Int, today: Long) =
        mutate { it.copy(items = it.items + HifzItem(UUID.randomUUID().toString(), sura, from, to, dueDay = today)) }

    suspend fun grade(id: String, g: Grade, today: Long) = mutate { d ->
        d.copy(items = d.items.map { if (it.id == id) Sm2.next(it, g, today) else it }, lastReviewDay = today)
    }

    suspend fun remove(id: String) = mutate { d -> d.copy(items = d.items.filterNot { it.id == id }) }
}
