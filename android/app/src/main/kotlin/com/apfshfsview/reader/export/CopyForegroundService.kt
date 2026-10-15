package com.apfshfsview.reader.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.apfshfsview.reader.session.ReaderSessions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CopyForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var worker: Job? = null
    private var latestStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, "Penyalinan", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Menyiapkan penyalinan…", null), serviceType)
        if (worker?.isActive != true) worker = scope.launch { drainQueue() }
        return START_NOT_STICKY
    }

    private suspend fun drainQueue() {
        while (true) {
            val job = CopyJobs.next()
            if (job == null) {
                if (CopyJobs.hasPending()) continue
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf(latestStartId)
                return
            }
            val result = withContext(Dispatchers.IO) { runJob(job) }
            CopyJobs.publish(result)
            updateNotification(result)
        }
    }

    private fun runJob(job: CopyJob): CopyProgress =
        try {
            val fileSystem = ReaderSessions.fileSystem(job.volumeHandle)
            CopyEngine(this, job, fileSystem) { progress ->
                CopyJobs.publish(progress)
                updateNotification(progress)
            }.run()
        } catch (error: Exception) {
            CopyProgress.idle(job, CopyState.FAILED, "Gagal menyalin: ${error.message}")
        }

    private fun updateNotification(progress: CopyProgress) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val percent = if (progress.totalBytes > 0) (progress.copiedBytes * 100 / progress.totalBytes).toInt() else null
        val text = "${progress.copiedFiles} dari ${progress.totalFiles} file · ${progress.currentName}"
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification(text, percent)) }
    }

    private fun notification(text: String, percent: Int?): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Menyalin ke HP")
            .setContentText(text)
            .setProgress(100, percent ?: 0, percent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "copy"
        const val NOTIFICATION_ID = 4108
    }
}
