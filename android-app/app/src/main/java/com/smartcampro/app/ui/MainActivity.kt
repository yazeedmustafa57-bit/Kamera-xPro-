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
import com.smartcampro.app.utils.UserManager
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var apiClient: ApiClient
    private lateinit var userManager: UserManager

    private lateinit var serverUrlInput: EditText
    private lateinit var cameraNameInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var connectButton: Button
    private lateinit var standaloneButton: Button
    private lateinit var statusText: TextView
    private lateinit var userInfoText: TextView
    private lateinit var logoutButton: Button

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        apiClient = ApiClient(Constants.DEFAULT_SERVER_URL)
        userManager = UserManager(this)

        serverUrlInput = findViewById(R.id.serverUrlInput)
        cameraNameInput = findViewById(R.id.cameraNameInput)
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        connectButton = findViewById(R.id.connectButton)
        standaloneButton = findViewById(R.id.standaloneButton)
        statusText = findViewById(R.id.statusText)
        userInfoText = findViewById(R.id.userInfoText)
        logoutButton = findViewById(R.id.logoutButton)

        // User info
        val currentUser = userManager.getLoggedInUser() ?: "Gast"
        val role = userManager.getUserRole(currentUser)
        userInfoText.text = "$currentUser ($role)"

        // Saved values
        val savedUrl = prefs.getString(Constants.PREF_SERVER_URL, "")
        if (!savedUrl.isNullOrEmpty()) serverUrlInput.setText(savedUrl)
        else serverUrlInput.hint = "z.B. http://192.168.1.100:8000"
        cameraNameInput.setText(prefs.getString(Constants.PREF_CAMERA_NAME, "Kamera 1"))
        usernameInput.setText(prefs.getString("username", ""))

        // Buttons
        connectButton.setOnClickListener { connect() }
        standaloneButton.setOnClickListener { startStandalone() }
        logoutButton.setOnClickListener {
            userManager.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Navigation
        findViewById<Button>(R.id.navGallery).setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
        findViewById<Button>(R.id.navEvents).setOnClickListener {
            startActivity(Intent(this, EventsActivity::class.java))
        }
        findViewById<Button>(R.id.navSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        showStatus("Kamera starten oder mit Server verbinden", false)
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
                Toast.makeText(this, "Kamera/Mikrofon Berechtigungen fehlen!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.VIBRATE
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

    private fun startStandalone() {
        val cameraName = cameraNameInput.text.toString().trim().ifEmpty { "Kamera 1" }
        prefs.edit().putString(Constants.PREF_CAMERA_NAME, cameraName).apply()
        val intent = Intent(this, CameraActivity::class.java).apply {
            putExtra("token", "standalone")
            putExtra("camera_name", cameraName)
            putExtra("camera_id", "")
            putExtra("server_url", "")
        }
        startActivity(intent)
    }

    private fun connect() {
        val serverUrl = serverUrlInput.text.toString().trim()
        val cameraName = cameraNameInput.text.toString().trim().ifEmpty { "Kamera 1" }
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (serverUrl.isEmpty()) {
            showStatus("Server-URL eingeben!", true)
            return
        }
        if (username.isEmpty() || password.isEmpty()) {
            showStatus("Benutzername und Passwort eingeben!", true)
            return
        }

        apiClient = ApiClient(serverUrl)
        showStatus("Verbinde mit Server...", false)
        connectButton.isEnabled = false
        connectButton.text = "Verbinde..."

        apiClient.login(username, password, object : ApiClient.ApiCallback {
            override fun onSuccess(response: String) {
                try {
                    val json = JSONObject(response)
                    val token = json.getString("access_token")
                    prefs.edit().apply {
                        putString(Constants.PREF_SERVER_URL, serverUrl)
                        putString(Constants.PREF_CAMERA_NAME, cameraName)
                        putString(Constants.PREF_AUTH_TOKEN, token)
                        putString("username", username)
                        apply()
                    }
                    showStatus("Angemeldet! Registriere Kamera...", false)
                    registerCamera(token, cameraName)
                } catch (e: Exception) {
                    showError("Login fehlgeschlagen: ${e.message}")
                    resetButton()
                }
            }

            override fun onError(error: String) {
                val friendlyError = when {
                    error.contains("Unable to resolve host") -> "Server nicht erreichbar"
                    error.contains("Connection refused") -> "Server antwortet nicht"
                    error.contains("timeout", ignoreCase = true) -> "Verbindung zu lange"
                    error.contains("401") -> "Falscher Benutzer oder Passwort"
                    else -> "Fehler: $error"
                }
                showError(friendlyError)
                resetButton()
            }
        })
    }

    private fun showError(message: String) {
        showStatus(message, true)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun resetButton() {
        runOnUiThread {
            connectButton.isEnabled = true
            connectButton.text = "Mit Server anmelden"
        }
    }

    private fun registerCamera(token: String, cameraName: String) {
        apiClient.listCameras(token, object : ApiClient.ApiCallback {
            override fun onSuccess(response: String) {
                try {
                    val array = JSONArray(response)
                    var foundCameraId: String? = null
                    for (i in 0 until array.length()) {
                        val cam = array.getJSONObject(i)
                        if (cam.optString("name") == cameraName) {
                            foundCameraId = cam.getString("id")
                            break
                        }
                    }
                    if (foundCameraId != null) {
                        prefs.edit().putString(Constants.PREF_CAMERA_ID, foundCameraId).apply()
                        val serverUrl = prefs.getString(Constants.PREF_SERVER_URL, "") ?: ""
                        startCameraActivity(token, cameraName, foundCameraId, serverUrl)
                    } else {
                        createNewCamera(token, cameraName)
                    }
                } catch (e: Exception) {
                    createNewCamera(token, cameraName)
                }
            }

            override fun onError(error: String) {
                createNewCamera(token, cameraName)
            }
        })
    }

    private fun createNewCamera(token: String, cameraName: String) {
        apiClient.registerCamera(token, cameraName, object : ApiClient.ApiCallback {
            override fun onSuccess(response: String) {
                try {
                    val json = JSONObject(response)
                    val cameraId = json.getString("id")
                    prefs.edit().putString(Constants.PREF_CAMERA_ID, cameraId).apply()
                    val serverUrl = prefs.getString(Constants.PREF_SERVER_URL, "") ?: ""
                    startCameraActivity(token, cameraName, cameraId, serverUrl)
                } catch (e: Exception) {
                    showError("Kamera-Registrierung fehlgeschlagen: ${e.message}")
                    resetButton()
                }
            }

            override fun onError(error: String) {
                showError("Kamera-Registrierung fehlgeschlagen: $error")
                resetButton()
            }
        })
    }

    private fun startCameraActivity(token: String, cameraName: String, cameraId: String, serverUrl: String) {
        val intent = Intent(this, CameraActivity::class.java).apply {
            putExtra("token", token)
            putExtra("camera_name", cameraName)
            putExtra("camera_id", cameraId)
            putExtra("server_url", serverUrl)
        }
        startActivity(intent)
        finish()
    }
}
