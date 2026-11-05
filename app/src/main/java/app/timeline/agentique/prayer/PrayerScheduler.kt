package app.timeline.agentique.prayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import app.timeline.agentique.container
import app.timeline.agentique.core.AppSettings
import app.timeline.agentique.core.LocationResolver
import app.timeline.agentique.core.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

object PrayerScheduler {
    const val ACTION_FIRE = "app.timeline.agentique.PRAYER_FIRE"
    private const val EXTRA_PRAYER = "prayer"

    private fun pending(ctx: Context, p: Prayer?): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_PRAYER, p?.name)
        return PendingIntent.getBroadcast(ctx, 1001, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** One alarm at a time: schedule the next prayer, and the receiver schedules the one after. */
    fun schedule(ctx: Context, s: AppSettings) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (!s.alarms) {
            am.cancel(pending(ctx, null))
            return
        }
        val at = LocationResolver.current(ctx, s) ?: return
        val next = PrayerCalc.next(ZonedDateTime.now(), at, s) ?: return
        val pi = pending(ctx, next.prayer)
        val whenMs = next.time.toInstant().toEpochMilli()
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
    }

    internal fun prayerFrom(intent: Intent): Prayer? =
        intent.getStringExtra(EXTRA_PRAYER)?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val result = goAsync()
        val prayer = PrayerScheduler.prayerFrom(intent)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (prayer != null) Notifier.prayer(ctx, prayer)
                PrayerScheduler.schedule(ctx, ctx.container.settings.flow.first())
            } finally {
                result.finish()
            }
        }
    }
}

/** Reschedules after reboot, app update, clock or time-zone change. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                PrayerScheduler.schedule(ctx, ctx.container.settings.flow.first())
            } finally {
                result.finish()
            }
        }
    }
}
