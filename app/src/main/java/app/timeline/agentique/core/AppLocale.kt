package app.timeline.agentique.core

import java.util.Locale

/**
 * Arabic only for now. To add English: add "en" to [supported], create res/values-en/strings.xml
 * (and arrays.xml), add <locale android:name="en"/> to res/xml/locales_config.xml, and add "en"
 * entries to assets/halal_rules.json. Layout direction and formatting follow automatically.
 */
object AppLocale {
    val supported = listOf("ar")
    private val rtl = setOf("ar", "he", "fa", "ur")

    fun language(): String = Locale.getDefault().language.takeIf { it in supported } ?: "ar"
    fun locale(): Locale = Locale.forLanguageTag(language())
    fun isRtl(): Boolean = language() in rtl
}

/** Picks the entry for the current app language, falling back to Arabic, then anything. */
fun <T> Map<String, T>.localized(): T? = this[AppLocale.language()] ?: this["ar"] ?: values.firstOrNull()
