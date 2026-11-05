package app.timeline.agentique

import android.app.Application
import android.content.Context
import app.timeline.agentique.core.Notifier
import app.timeline.agentique.core.SettingsStore
import app.timeline.agentique.halal.HalalRules
import app.timeline.agentique.llm.LlmEngine
import app.timeline.agentique.llm.MediaPipeLlmEngine
import app.timeline.agentique.prayer.PrayerScheduler
import app.timeline.agentique.quran.HifzStore
import app.timeline.agentique.quran.QuranCorpus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

class TimelineApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
    }
}

/** Tiny service locator: everything lives on-device, no network except the model download. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(app)
    val corpus = QuranCorpus(app)
    val hifz = HifzStore(app)
    val halal = HalalRules(app)
    val llm: LlmEngine = MediaPipeLlmEngine(app)

    init {
        scope.launch {
            settings.flow
                .distinctUntilChangedBy { listOf(it.alarms, it.method, it.madhhab, it.home, it.useDeviceLocation) }
                .collect { PrayerScheduler.schedule(app, it) }
        }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as TimelineApp).container
