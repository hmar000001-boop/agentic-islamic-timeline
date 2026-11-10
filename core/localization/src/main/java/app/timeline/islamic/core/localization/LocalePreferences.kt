package app.timeline.islamic.core.localization

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.localeDataStore by preferencesDataStore(name = "locale_preferences")

/** Stores a BCP-47 tag only; Android resources/Compose locale wiring is a separate UI concern. */
class LocalePreferences(private val context: Context) {
    private val key = stringPreferencesKey("locale_tag")
    val localeTag: Flow<String> = context.localeDataStore.data.map { preferences ->
        preferences[key]?.takeIf(String::isNotBlank) ?: DEFAULT_LOCALE
    }

    suspend fun setLocaleTag(tag: String) {
        require(tag.isNotBlank() && tag.length <= 35) { "A valid BCP-47 locale tag is required" }
        val normalized = java.util.Locale.forLanguageTag(tag).toLanguageTag()
        require(normalized != "und") { "Unsupported locale tag" }
        context.localeDataStore.edit { it[key] = normalized }
    }

    private companion object { const val DEFAULT_LOCALE = "en" }
}
