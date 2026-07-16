package com.keywordrecord.keyboard

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object LauncherHelper {
    private const val PREFS_NAME = "keyword_record_prefs"
    private const val KEY_LAUNCHER_HIDDEN = "launcher_hidden"

    fun isLauncherHidden(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LAUNCHER_HIDDEN, false)
    }

    fun setLauncherHidden(context: Context, hidden: Boolean) {
        val component = ComponentName(context, LauncherAlias::class.java)
        val state = if (hidden) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        context.packageManager.setComponentEnabledSetting(
            component,
            state,
            PackageManager.DONT_KILL_APP
        )
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LAUNCHER_HIDDEN, hidden)
            .apply()
    }
}
