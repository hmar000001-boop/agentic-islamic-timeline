package app.timeline.islamic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.timeline.islamic.core.persistence.RoomCanvasRepository
import app.timeline.islamic.core.persistence.RoomTimelineRepository
import app.timeline.islamic.feature.canvas.CanvasFeature
import app.timeline.islamic.feature.timeline.TimelineFeature

private enum class AppDestination { TIMELINE, CANVAS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val databaseResult = runCatching { (application as TimelineApplication).database }
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (databaseResult.isSuccess) {
                            val database = databaseResult.getOrThrow()
                            val timelineRepository = remember(database) { RoomTimelineRepository(database.timelineDao()) }
                            val canvasRepository = remember(database) { RoomCanvasRepository(database.canvasDao()) }
                            var destination by remember { mutableStateOf(AppDestination.TIMELINE) }
                            when (destination) {
                                AppDestination.TIMELINE -> TimelineFeature(timelineRepository) { destination = AppDestination.CANVAS }
                                AppDestination.CANVAS -> CanvasFeature(canvasRepository) { destination = AppDestination.TIMELINE }
                            }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.app_storage_open_error))
                        }
                    }
                }
            }
        }
    }
}
