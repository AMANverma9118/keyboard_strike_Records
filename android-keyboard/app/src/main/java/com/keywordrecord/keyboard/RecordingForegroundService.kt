package com.keywordrecord.keyboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class RecordingForegroundService : Service() {

    private var apiClient: ApiClient? = null
    private val syncHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val syncRunnable = object : Runnable {
        override fun run() {
            try {
                apiClient?.flushOfflineQueue()
            } catch (e: Exception) {
                Log.e(TAG, "Offline sync failed", e)
            }
            syncHandler.postDelayed(this, 30_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            if (apiClient == null) {
                apiClient = ApiClient(this)
            }
            createNotificationChannel()
            val notification = buildNotification()
            startForegroundSafe(notification)
            syncHandler.removeCallbacks(syncRunnable)
            syncHandler.postDelayed(syncRunnable, 5_000L)
            START_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "Foreground service failed", e)
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        syncHandler.removeCallbacks(syncRunnable)
        super.onDestroy()
    }

    private fun startForegroundSafe(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "RecordingFGS"
        private const val CHANNEL_ID = "app_store_recording"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            try {
                val intent = Intent(context, RecordingForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not start foreground service", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, RecordingForegroundService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
