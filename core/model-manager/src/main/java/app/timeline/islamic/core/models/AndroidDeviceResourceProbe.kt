package app.timeline.islamic.core.models

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.StatFs

/** Captures resource estimates only; it does not initialize LiteRT-LM or assert compatibility. */
class AndroidDeviceResourceProbe(context: Context) {
    private val appContext = context.applicationContext

    fun snapshot(): ModelResourceSnapshot {
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val debugMemory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val stats = StatFs(appContext.noBackupFilesDir.absolutePath)
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (appContext.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
        } else null
        return ModelResourceSnapshot(
            totalRamBytes = memory.totalMem,
            availableStorageBytes = stats.availableBytes,
            lowMemory = memory.lowMemory,
            processPssBytes = debugMemory.totalPss.toLong() * 1024L,
            memoryClassMb = activityManager.memoryClass,
            thermalStatus = thermal
        )
    }
}
