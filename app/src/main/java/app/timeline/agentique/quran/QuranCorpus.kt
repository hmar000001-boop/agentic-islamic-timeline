package app.timeline.agentique.quran

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Verified Quran text (Tanzil, format `sura|aya|text`), shipped as an asset and never modified. */
class QuranCorpus(private val ctx: Context) {
    data class Ayah(val sura: Int, val aya: Int, val text: String)

    @Volatile private var ayat: List<Ayah>? = null
    @Volatile private var index: List<IndexedAyah>? = null
    @Volatile private var counts: Map<Int, Int> = emptyMap()

    suspend fun load(): Boolean = withContext(Dispatchers.IO) {
        if (ayat != null) return@withContext true
        val list = runCatching {
            ctx.assets.open(ASSET).bufferedReader().useLines { lines -> lines.mapNotNull { parse(it) }.toList() }
        }.getOrNull()
        if (list.isNullOrEmpty()) {
            false
        } else {
            counts = list.groupingBy { it.sura }.eachCount()
            index = list.map { IndexedAyah(it.sura, it.aya, ArabicNormalizer.tokens(it.text)) }
            ayat = list
            true
        }
    }

    fun range(sura: Int, from: Int, to: Int): List<Ayah> =
        ayat?.filter { it.sura == sura && it.aya in from..to }.orEmpty()

    fun suraLength(sura: Int): Int = counts[sura] ?: 0

    fun match(ocrText: String): MushafMatch? = index?.let { MushafMatcher.find(ocrText, it) }

    private fun parse(line: String): Ayah? {
        if (line.isBlank() || line.startsWith("#")) return null
        val parts = line.split('|', limit = 3)
        if (parts.size < 3) return null
        val sura = parts[0].trim().toIntOrNull() ?: return null
        val aya = parts[1].trim().toIntOrNull() ?: return null
        return Ayah(sura, aya, parts[2].trim())
    }

    private companion object {
        const val ASSET = "quran/quran-uthmani.txt"
    }
}
