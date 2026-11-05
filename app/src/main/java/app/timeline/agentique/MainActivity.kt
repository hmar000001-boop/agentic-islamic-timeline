package app.timeline.agentique

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.timeline.agentique.prayer.PrayerScheduler
import app.timeline.agentique.ui.TimelineRoot
import app.timeline.agentique.ui.TimelineTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TimelineTheme { TimelineRoot() } }
    }

    override fun onStart() {
        super.onStart()
        val c = container
        c.scope.launch { PrayerScheduler.schedule(applicationContext, c.settings.flow.first()) }
    }
}
