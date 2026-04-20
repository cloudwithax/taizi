package com.taizi.data.scraper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.taizi.MainActivity
import com.taizi.R
import com.taizi.domain.repository.LibraryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ScrapeStatus(
    val gameName: String = "",
    val systemName: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val isRunning: Boolean = false
)

@AndroidEntryPoint
class BoxArtScrapeService : Service() {

    @Inject lateinit var repository: LibraryRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scrapeJob: Job? = null

    companion object {
        private const val TAG = "BoxArtScrape"
        private const val CHANNEL_ID = "taizi_scrape"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "com.taizi.STOP_SCRAPE"

        private val _status = MutableStateFlow(ScrapeStatus())
        val status: StateFlow<ScrapeStatus> = _status.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, BoxArtScrapeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BoxArtScrapeService::class.java))
        }

        fun isRunning(): Boolean = _status.value.isRunning
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopScraping()
            return START_NOT_STICKY
        }

        if (scrapeJob?.isActive == true) {
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("Starting box art scrape…", 0, 0))
        startScraping()

        return START_NOT_STICKY
    }

    private fun startScraping() {
        scrapeJob = scope.launch {
            _status.value = ScrapeStatus(isRunning = true)
            Log.d(TAG, "Scrape service started")

            val result = repository.scrapeAll { gameName, systemName, current, total ->
                _status.value = ScrapeStatus(gameName, systemName, current, total, isRunning = true)
                updateNotification(gameName, systemName, current, total)
            }

            result.fold(
                onSuccess = { count ->
                    Log.d(TAG, "Scrape complete: $count games scraped")
                    showCompleteNotification(count)
                },
                onFailure = { error ->
                    Log.e(TAG, "Scrape failed: ${error.message}")
                    showErrorNotification(error.message ?: "Unknown error")
                }
            )

            _status.value = ScrapeStatus(isRunning = false)
            stopSelf()
        }
    }

    private fun stopScraping() {
        scrapeJob?.cancel()
        _status.value = ScrapeStatus(isRunning = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scrapeJob?.cancel()
        scope.cancel()
        _status.value = ScrapeStatus(isRunning = false)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Box Art Scraping",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress while downloading box art"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, current: Int, total: Int): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, BoxArtScrapeService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Scraping box art")
            .setContentText(text)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopIntent)

        if (total > 0) {
            builder.setProgress(total, current, false)
            builder.setSubText("$current / $total")
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun updateNotification(gameName: String, systemName: String, current: Int, total: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification("$gameName · $systemName", current, total))
    }

    private fun showCompleteNotification(count: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        stopForeground(STOP_FOREGROUND_REMOVE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Box art scrape complete")
            .setContentText("Downloaded art for $count games")
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        nm.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showErrorNotification(message: String) {
        val nm = getSystemService(NotificationManager::class.java)
        stopForeground(STOP_FOREGROUND_REMOVE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Box art scrape failed")
            .setContentText(message)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID + 1, notification)
    }
}
