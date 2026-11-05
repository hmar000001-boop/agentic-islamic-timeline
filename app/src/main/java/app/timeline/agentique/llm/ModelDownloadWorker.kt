package app.timeline.agentique.llm

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.timeline.agentique.R
import app.timeline.agentique.container
import app.timeline.agentique.core.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

const val MODEL_WORK_NAME = "model_download"

fun enqueueModelDownload(ctx: Context) {
    val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(ctx).enqueueUniqueWork(MODEL_WORK_NAME, ExistingWorkPolicy.KEEP, request)
}

/** Downloads the Gemma .task file once, with resume support. The only network use of the whole app. */
class ModelDownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private fun notification(): Notification =
        NotificationCompat.Builder(applicationContext, Notifier.CH_MODEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(applicationContext.getString(R.string.notif_model_title))
            .setOngoing(true)
            .build()

    override suspend fun getForegroundInfo(): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIF_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notification())
        }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val s = applicationContext.container.settings.flow.first()
        if (s.modelUrl.isBlank()) return@withContext Result.failure(workDataOf("error" to "no_url"))
        runCatching { setForeground(getForegroundInfo()) }

        val target = ModelFiles.file(applicationContext)
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        try {
            val existing = if (part.exists()) part.length() else 0L
            val conn = (URL(s.modelUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                if (s.modelToken.isNotBlank()) setRequestProperty("Authorization", "Bearer ${s.modelToken}")
                if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
            }
            val code = conn.responseCode
            if (code != 200 && code != 206) return@withContext Result.failure(workDataOf("error" to "http_$code"))
            val append = code == 206
            val base = if (append) existing else 0L
            val total = if (conn.contentLengthLong > 0) conn.contentLengthLong + base else -1L

            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = base
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                setProgress(workDataOf("pct" to pct))
                            }
                        }
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) return@withContext Result.failure(workDataOf("error" to "rename"))
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        }
    }

    private companion object {
        const val NOTIF_ID = 4201
    }
}
