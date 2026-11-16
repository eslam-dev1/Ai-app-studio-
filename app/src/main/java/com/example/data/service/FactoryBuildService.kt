package com.example.data.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BackgroundFactoryState(
    val isBusy: Boolean = false,
    val title: String = "",
    val message: String = "",
    val progress: Float = 0f,
    val canCancel: Boolean = true
)

object BackgroundTaskManager {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    private val _taskState = MutableStateFlow(BackgroundFactoryState())
    val taskState: StateFlow<BackgroundFactoryState> = _taskState.asStateFlow()

    fun updateState(
        isBusy: Boolean,
        title: String,
        message: String,
        progress: Float = 0f,
        canCancel: Boolean = true
    ) {
        _taskState.value = BackgroundFactoryState(
            isBusy = isBusy,
            title = title,
            message = message,
            progress = progress.coerceIn(0f, 1f),
            canCancel = canCancel
        )
    }

    fun startTask(context: Context, title: String, initialMessage: String = "Initializing…") {
        updateState(isBusy = true, title = title, message = initialMessage, progress = 0.05f)
        FactoryBuildService.startService(context, title)
    }

    fun updateProgress(context: Context, title: String, message: String, progress: Float) {
        updateState(isBusy = true, title = title, message = message, progress = progress)
        FactoryBuildService.updateNotification(context, title, message, (progress * 100).toInt())
    }

    fun finishTask(context: Context, title: String, completionMessage: String) {
        updateState(isBusy = false, title = title, message = completionMessage, progress = 1f)
        FactoryBuildService.showCompletionNotification(context, title, completionMessage)
        FactoryBuildService.stopService(context)
        activeJob = null
    }

    fun failTask(context: Context, title: String, errorMessage: String) {
        updateState(isBusy = false, title = title, message = errorMessage, progress = 0f)
        FactoryBuildService.showCompletionNotification(context, title, "Failed: $errorMessage")
        FactoryBuildService.stopService(context)
        activeJob = null
    }

    fun cancelTask(context: Context? = null) {
        activeJob?.cancel()
        activeJob = null
        updateState(isBusy = false, title = "", message = "Cancelled", progress = 0f)
        if (context != null) {
            FactoryBuildService.stopService(context)
        }
    }

    fun launchTask(
        context: Context,
        title: String,
        action: suspend (updateProgress: suspend (Float, String) -> Unit) -> Unit
    ): Job {
        startTask(context, title)
        val job = scope.launch {
            try {
                action { prog, msg ->
                    updateProgress(context, title, msg, prog)
                }
                finishTask(context, title, "Task completed successfully.")
            } catch (ex: Exception) {
                failTask(context, title, ex.localizedMessage ?: "Unknown error")
            }
        }
        activeJob = job
        return job
    }
}

class FactoryBuildService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    companion object {
        const val CHANNEL_ID = "ai_app_studio_factory_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.action.START_FACTORY_SERVICE"
        const val ACTION_STOP = "com.example.action.STOP_FACTORY_SERVICE"
        const val EXTRA_TITLE = "extra_task_title"

        @Volatile
        private var isForegroundActive = false

        @Volatile
        private var pendingStopRequested = false

        @Volatile
        private var lastRequestedTitle: String = "AI Factory Task"

        fun startService(context: Context, title: String) {
            lastRequestedTitle = title
            pendingStopRequested = false
            val intent = Intent(context, FactoryBuildService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stopService(context: Context) {
            if (isForegroundActive) {
                pendingStopRequested = false
                val intent = Intent(context, FactoryBuildService::class.java).apply {
                    action = ACTION_STOP
                }
                runCatching { context.stopService(intent) }
            } else {
                // Defer stopping until onCreate/onStartCommand has called startForeground()
                // so Android never raises ForegroundServiceDidNotStartInTimeException.
                pendingStopRequested = true
            }
        }

        fun updateNotification(context: Context, title: String, message: String, percent: Int) {
            if (pendingStopRequested) return
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val notification = buildNotification(context, title, message, percent, isCompleted = false)
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }

        fun showCompletionNotification(context: Context, title: String, message: String) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val notification = buildNotification(context, "$title • Ready", message, 100, isCompleted = true)
            notificationManager?.notify(NOTIFICATION_ID + 1, notification)
        }

        fun showErrorNotification(context: Context, title: String, message: String) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingOpenIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            createChannelIfNeeded(context)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentIntent(pendingOpenIntent)
                .setAutoCancel(true)
                .build()
            notificationManager?.notify(NOTIFICATION_ID + 2, notification)
        }

        @SuppressLint("UnspecifiedImmutableFlag")
        private fun buildNotification(
            context: Context,
            title: String,
            message: String,
            percent: Int,
            isCompleted: Boolean
        ): Notification {
            createChannelIfNeeded(context)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingOpenIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(pendingOpenIntent)
                .setOngoing(!isCompleted)
                .setAutoCancel(isCompleted)

            if (!isCompleted) {
                builder.setProgress(100, percent.coerceIn(0, 100), percent <= 0)

                val stopIntent = Intent(context, FactoryBuildService::class.java).apply {
                    action = ACTION_STOP
                }
                val pendingStopIntent = PendingIntent.getService(
                    context,
                    1,
                    stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                )
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStopIntent)
            } else {
                builder.setProgress(0, 0, false)
            }

            return builder.build()
        }

        private fun createChannelIfNeeded(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "AI Factory Background Execution",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Maintains persistent background execution for AI model downloads, code generation, and APK builds."
                }
                manager?.createNotificationChannel(channel)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(notification: Notification) {
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
        isForegroundActive = true
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        val initialNotification = buildNotification(
            context = this,
            title = "AI Factory Active",
            message = "Executing $lastRequestedTitle in background…",
            percent = 5,
            isCompleted = false
        )
        startForegroundCompat(initialNotification)

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AiAppStudio::FactoryWakeLock")?.apply {
                acquire(30 * 60 * 1000L) // 30 mins timeout safety
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AiAppStudio::FactoryWifiLock")?.apply {
                acquire()
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val taskTitle = intent?.getStringExtra(EXTRA_TITLE) ?: lastRequestedTitle

        val notification = buildNotification(
            context = this,
            title = "AI Factory Active",
            message = "Executing $taskTitle in background…",
            percent = 15,
            isCompleted = false
        )
        startForegroundCompat(notification)

        if (action == ACTION_STOP || pendingStopRequested) {
            pendingStopRequested = false
            if (action == ACTION_STOP) {
                BackgroundTaskManager.cancelTask(null)
            }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isForegroundActive = false
            stopSelf(startId)
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isForegroundActive = false
        runCatching { wakeLock?.release() }
        runCatching { wifiLock?.release() }
        wakeLock = null
        wifiLock = null
        super.onDestroy()
    }
}
