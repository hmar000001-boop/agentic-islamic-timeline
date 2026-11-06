package app.timeline.islamic

import android.app.Application
import app.timeline.islamic.core.persistence.EncryptedDatabaseFactory

class TimelineApplication : Application() {
    // Lazy open keeps the UI shell independent from a model; database errors remain explicit.
    val database by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { EncryptedDatabaseFactory.get(this) }
}
