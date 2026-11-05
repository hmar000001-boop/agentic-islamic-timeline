package app.timeline.agentique.core

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.timeline.agentique.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class Madhhab { HANAFI, MALIKI, SHAFII, HANBALI }
enum class Proactivity { LOW, NORMAL, HIGH }
data class GeoPoint(val lat: Double, val lon: Double)

/** The model never invents these: madhhab stays null until the user picks one. */
data class AppSettings(
    val madhhab: Madhhab? = null,
    val method: String = "MUSLIM_WORLD_LEAGUE",
    val home: GeoPoint? = null,
    val useDeviceLocation: Boolean = true,
    val alarms: Boolean = true,
    val proactivity: Proactivity = Proactivity.NORMAL,
    val travelMode: Boolean = false,
    val travelThresholdKm: Double = 81.0,
    val hifzMinutes: Int = 15,
    val zakatNetWealth: Double? = null,
    val zakatNisab: Double? = null,
    val zakatDue: Double? = null,
    val zakatDay: Long? = null,
    val dismissed: Set<String> = emptySet(),
    val modelUrl: String = BuildConfig.DEFAULT_MODEL_URL,
    val modelToken: String = "",
)

object MethodOptions {
    /** Parallel to R.array.method_labels. */
    val names = listOf(
        "MUSLIM_WORLD_LEAGUE", "EGYPTIAN", "KARACHI", "UMM_AL_QURA", "DUBAI", "MOON_SIGHTING_COMMITTEE",
        "NORTH_AMERICA", "KUWAIT", "QATAR", "SINGAPORE", "TURKEY",
    )
}

private val Context.settingsDs by preferencesDataStore(name = "settings")

class SettingsStore(private val ctx: Context) {
    private object K {
        val madhhab = stringPreferencesKey("madhhab")
        val method = stringPreferencesKey("method")
        val homeLat = doublePreferencesKey("home_lat")
        val homeLon = doublePreferencesKey("home_lon")
        val useDevice = booleanPreferencesKey("use_device")
        val alarms = booleanPreferencesKey("alarms")
        val proactivity = stringPreferencesKey("proactivity")
        val travel = booleanPreferencesKey("travel")
        val travelKm = doublePreferencesKey("travel_km")
        val hifzMin = intPreferencesKey("hifz_min")
        val zNet = doublePreferencesKey("z_net")
        val zNisab = doublePreferencesKey("z_nisab")
        val zDue = doublePreferencesKey("z_due")
        val zDay = longPreferencesKey("z_day")
        val dismissed = stringSetPreferencesKey("dismissed")
        val modelUrl = stringPreferencesKey("model_url")
        val modelToken = stringPreferencesKey("model_token")
    }

    val flow: Flow<AppSettings> = ctx.settingsDs.data.map { read(it) }

    suspend fun update(block: (AppSettings) -> AppSettings) {
        ctx.settingsDs.edit { p -> write(p, block(read(p))) }
    }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        val lat = p[K.homeLat]
        val lon = p[K.homeLon]
        return AppSettings(
            madhhab = p[K.madhhab]?.let { runCatching { Madhhab.valueOf(it) }.getOrNull() },
            method = p[K.method] ?: d.method,
            home = if (lat != null && lon != null) GeoPoint(lat, lon) else null,
            useDeviceLocation = p[K.useDevice] ?: d.useDeviceLocation,
            alarms = p[K.alarms] ?: d.alarms,
            proactivity = p[K.proactivity]?.let { runCatching { Proactivity.valueOf(it) }.getOrNull() } ?: d.proactivity,
            travelMode = p[K.travel] ?: d.travelMode,
            travelThresholdKm = p[K.travelKm] ?: d.travelThresholdKm,
            hifzMinutes = p[K.hifzMin] ?: d.hifzMinutes,
            zakatNetWealth = p[K.zNet],
            zakatNisab = p[K.zNisab],
            zakatDue = p[K.zDue],
            zakatDay = p[K.zDay],
            dismissed = p[K.dismissed] ?: emptySet(),
            modelUrl = p[K.modelUrl] ?: d.modelUrl,
            modelToken = p[K.modelToken] ?: d.modelToken,
        )
    }

    private fun write(p: MutablePreferences, s: AppSettings) {
        fun <T> set(k: Preferences.Key<T>, v: T?) {
            if (v == null) p.remove(k) else p[k] = v
        }
        set(K.madhhab, s.madhhab?.name)
        set(K.method, s.method)
        set(K.homeLat, s.home?.lat)
        set(K.homeLon, s.home?.lon)
        set(K.useDevice, s.useDeviceLocation)
        set(K.alarms, s.alarms)
        set(K.proactivity, s.proactivity.name)
        set(K.travel, s.travelMode)
        set(K.travelKm, s.travelThresholdKm)
        set(K.hifzMin, s.hifzMinutes)
        set(K.zNet, s.zakatNetWealth)
        set(K.zNisab, s.zakatNisab)
        set(K.zDue, s.zakatDue)
        set(K.zDay, s.zakatDay)
        set(K.dismissed, s.dismissed)
        set(K.modelUrl, s.modelUrl)
        set(K.modelToken, s.modelToken)
    }
}
