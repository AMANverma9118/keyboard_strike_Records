package com.keywordrecord.keyboard

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        apiClient = ApiClient(this)
        val deviceId = DeviceIdManager.getDeviceUniqueId(this)

        val deviceIdText = findViewById<TextView>(R.id.txt_device_id)
        val serverUrlInput = findViewById<EditText>(R.id.input_server_url)
        val saveButton = findViewById<Button>(R.id.btn_save)
        val testButton = findViewById<Button>(R.id.btn_test_connection)

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

        findViewById<TextView>(R.id.txt_instructions).text = getString(R.string.setup_instructions)
    }
}
