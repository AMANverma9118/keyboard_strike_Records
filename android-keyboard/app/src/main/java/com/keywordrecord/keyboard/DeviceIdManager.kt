package com.keywordrecord.keyboard

import android.content.Context
import java.util.UUID

object DeviceIdManager {
    private const val PREFS_NAME = "keyword_record_prefs"
    private const val KEY_DEVICE_ID = "device_unique_id"

    fun getDeviceUniqueId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) {
            return existing
        }

        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }
}
