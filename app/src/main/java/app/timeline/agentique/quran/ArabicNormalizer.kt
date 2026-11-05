package app.timeline.agentique.quran

/** Strips diacritics and unifies letter variants so OCR output can be compared with the corpus. */
object ArabicNormalizer {
    private val marks = Regex("[\\p{Mn}\\u0640]")
    private val nonWord = Regex("[^\\p{L}\\p{N}\\s]")
    private val spaces = Regex("\\s+")

    fun normalize(s: String): String {
        val unified = buildString(s.length) {
            for (c in s.replace(marks, "")) {
                append(
                    when (c) {
                        'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                        'ى', 'ی', 'ئ' -> 'ي'
                        'ک' -> 'ك'
                        'ة' -> 'ه'
                        'ؤ' -> 'و'
                        else -> c
                    },
                )
            }
        }
        return unified.lowercase().replace(nonWord, " ").replace(spaces, " ").trim()
    }

    fun tokens(s: String): List<String> = normalize(s).split(' ').filter { it.isNotEmpty() }
}
