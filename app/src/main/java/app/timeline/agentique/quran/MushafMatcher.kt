package app.timeline.agentique.quran

class IndexedAyah(val sura: Int, val aya: Int, val tokens: List<String>)

data class MushafMatch(val sura: Int, val from: Int, val to: Int, val coverage: Double)

/**
 * Deterministic page identification: the model only transcribes the photo; this code decides which
 * ayat it is, by word overlap with the verified corpus. Nothing here ever generates Quran text.
 */
object MushafMatcher {
    private const val MIN_AYAH_WORDS = 3
    private const val MIN_COVERAGE = 0.75
    private const val MAX_GAP = 3

    fun find(ocrText: String, index: List<IndexedAyah>): MushafMatch? {
        val words = ArabicNormalizer.tokens(ocrText).toSet()
        if (words.size < MIN_AYAH_WORDS) return null

        val hits = ArrayList<Pair<IndexedAyah, Double>>()
        for (a in index) {
            if (a.tokens.size < MIN_AYAH_WORDS) continue
            val coverage = a.tokens.count { it in words }.toDouble() / a.tokens.size
            if (coverage >= MIN_COVERAGE) hits += a to coverage
        }
        if (hits.isEmpty()) return null

        // Group hits into runs of nearby ayat in the same sura; keep the biggest run.
        val runs = ArrayList<MutableList<Pair<IndexedAyah, Double>>>()
        for (h in hits) {
            val last = runs.lastOrNull()?.last()
            if (last != null && last.first.sura == h.first.sura && h.first.aya - last.first.aya <= MAX_GAP) {
                runs.last().add(h)
            } else {
                runs += mutableListOf(h)
            }
        }
        val best = runs.maxByOrNull { r -> r.size * 1000 + r.sumOf { it.second } } ?: return null
        return MushafMatch(
            sura = best.first().first.sura,
            from = best.first().first.aya,
            to = best.last().first.aya,
            coverage = best.sumOf { it.second } / best.size,
        )
    }
}
