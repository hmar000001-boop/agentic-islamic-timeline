package app.timeline.agentique.halal

import android.content.Context
import app.timeline.agentique.core.localized
import app.timeline.agentique.quran.ArabicNormalizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class HalalStatus { HARAM, DOUBTFUL }

@Serializable
data class HalalSource(val title: String, val ref: String = "")

@Serializable
data class HalalRule(
    val id: String,
    val status: HalalStatus,
    val aliases: Map<String, List<String>>,
    val note: Map<String, String>,
    val sources: List<HalalSource> = emptyList(),
) {
    fun noteText(): String = note.localized().orEmpty()
}

@Serializable
data class HalalFile(val version: Int, val reviewed: Boolean, val rules: List<HalalRule>)

data class Finding(val rule: HalalRule, val matched: String)
enum class Verdict { HARAM_FOUND, DOUBTFUL_FOUND, NONE_FLAGGED }
data class HalalReport(val findings: List<Finding>, val verdict: Verdict)

/** Rule matching over ingredient text. Deterministic; the model is only used upstream for OCR. */
class HalalAnalyzer(rules: List<HalalRule>) {
    private val compiled: List<Pair<HalalRule, List<Regex>>> =
        rules.map { r -> r to r.aliases.values.flatten().mapNotNull { regexFor(it) } }

    fun analyze(text: String): HalalReport {
        val t = prepare(text)
        val findings = compiled.mapNotNull { (rule, regexes) ->
            regexes.firstNotNullOfOrNull { it.find(t)?.value }?.let { Finding(rule, it.trim()) }
        }.sortedBy { it.rule.status.ordinal }
        val verdict = when {
            findings.any { it.rule.status == HalalStatus.HARAM } -> Verdict.HARAM_FOUND
            findings.isNotEmpty() -> Verdict.DOUBTFUL_FOUND
            else -> Verdict.NONE_FLAGGED
        }
        return HalalReport(findings, verdict)
    }

    private fun prepare(s: String): String =
        ArabicNormalizer.normalize(s.replace(Regex("\\b[eE][\\s-]*(\\d{3,4})"), "e\$1"))

    private fun regexFor(alias: String): Regex? {
        val norm = ArabicNormalizer.normalize(alias)
        if (norm.isEmpty()) return null
        val body = Regex.escape(norm)
        val arabic = norm.any { it in '\u0600'..'\u06FF' }
        val pattern = when {
            arabic -> "(?<![\\u0621-\\u064A])(?:وال|بال|لل|فال|ال|و|ب|ل|ف)?$body(?![\\u0621-\\u064A])"
            Regex("e\\d{3,4}").matches(norm) -> "(?<![a-z0-9])$body(?![0-9])"
            else -> "(?<![a-z0-9])${body}s?(?![a-z0-9])"
        }
        return Regex(pattern)
    }
}

class HalalRules(private val ctx: Context) {
    val file: HalalFile by lazy {
        val text = ctx.assets.open("halal_rules.json").bufferedReader().use { it.readText() }
        Json { ignoreUnknownKeys = true }.decodeFromString<HalalFile>(text)
    }
    val analyzer: HalalAnalyzer by lazy { HalalAnalyzer(file.rules) }
    fun analyze(text: String): HalalReport = analyzer.analyze(text)
}
