package com.smartcampro.app.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.smartcampro.app.R
import com.smartcampro.app.network.ApiClient
import com.smartcampro.app.utils.Constants

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var apiClient: ApiClient

    private lateinit var serverUrlInput: EditText
    private lateinit var cameraNameInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var connectButton: Button
    private lateinit var statusText: TextView

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)

        serverUrlInput = findViewById(R.id.serverUrlInput)
        cameraNameInput = findViewById(R.id.cameraNameInput)
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        connectButton = findViewById(R.id.connectButton)
        statusText = findViewById(R.id.statusText)

        val savedUrl = prefs.getString(Constants.PREF_SERVER_URL, "")
        if (savedUrl.isNullOrEmpty() || savedUrl.contains("your-server")) {
            serverUrlInput.setText(Constants.DEFAULT_SERVER_URL)
        } else {
            serverUrlInput.setText(savedUrl)
        }
        cameraNameInput.setText(prefs.getString(Constants.PREF_CAMERA_NAME, "Camera 1"))
        usernameInput.setText(prefs.getString("username", ""))

        connectButton.setOnClickListener { connect() }

        requestPermissions()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val denied = permissions.zip(grantResults.toList()).filter { it.second != PackageManager.PERMISSION_GRANTED }
            if (denied.isNotEmpty()) {
                Toast.makeText(this, "Some permissions denied. Camera may not work.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun showStatus(message: String, isError: Boolean = false) {
        runOnUiThread {
            statusText.text = message
            statusText.setTextColor(if (isError) Color.parseColor("#ef4444") else Color.parseColor("#94a3b8"))
        }
    }

    private fun connect() {
        val serverUrl = serverUrlInput.text.toString().trim()
        val cameraName = cameraNameInput.text.toString().trim()
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        if (serverUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Bitte alle Felder ausfuellen", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "Passwort muss mindestens 6 Zeichen lang sein", Toast.LENGTH_SHORT).show()
            return
        }

        statusText.text = "Verbinde mit Server..."
        statusText.setTextColor(Color.parseColor("#f59e0b"))
        connectButton.isEnabled = false
        connectButton.text = "Verbinde..."

        apiClient = ApiClient(serverUrl)
        apiClient.login(username, password, object : ApiClient.ApiCallback {
            override fun onSuccess(response: org.json.JSONObject) {
                val token = response.getString("access_token")

                prefs.edit().apply {
                    putString(Constants.PREF_SERVER_URL, serverUrl)
                    putString(Constants.PREF_CAMERA_NAME, cameraName)
                    putString(Constants.PREF_AUTH_TOKEN, token)
                    putString("username", username)
                    apply()
                }

                runOnUiThread {
                    showStatus("Verbunden! Kamera wird gestartet...")
                    statusText.setTextColor(Color.parseColor("#22c55e"))
                }

                startCameraActivity(token, cameraName)
            }

            override fun onError(error: String) {
                runOnUiThread {
                    showStatus("Fehler: $error", isError = true)
                    connectButton.isEnabled = true
                    connectButton.text = "Anmelden"
                    Toast.makeText(this@MainActivity, "Verbindung fehlgeschlagen: $error", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun startCameraActivity(token: String, cameraName: String) {
        val intent = Intent(this, CameraActivity::class.java).apply {
            putExtra("token", token)
            putExtra("camera_name", cameraName)
            putExtra("server_url", prefs.getString(Constants.PREF_SERVER_URL, ""))
        }
        startActivity(intent)
        finish()
    }
}
