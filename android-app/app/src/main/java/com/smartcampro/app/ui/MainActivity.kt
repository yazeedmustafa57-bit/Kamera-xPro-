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
        if (!savedUrl.isNullOrEmpty()) {
            serverUrlInput.setText(savedUrl)
        } else {
            serverUrlInput.hint = "z.B. http://192.168.1.100:8000"
        }
        cameraNameInput.setText(prefs.getString(Constants.PREF_CAMERA_NAME, "Kamera 1"))
        usernameInput.setText(prefs.getString("username", ""))

        connectButton.setOnClickListener { connect() }

        showStatus("Server-URL eingeben und anmelden", false)
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
            statusText.setTextColor(
                if (isError) Color.parseColor("#ef4444")
                else Color.parseColor("#94a3b8")
            )
        }
    }

    private fun showConnecting() {
        runOnUiThread {
            statusText.text = "Verbinde mit Server..."
            statusText.setTextColor(Color.parseColor("#f59e0b"))
            connectButton.isEnabled = false
            connectButton.text = "Verbinde..."
        }
    }

    private fun showSuccess(message: String) {
        runOnUiThread {
            statusText.text = message
            statusText.setTextColor(Color.parseColor("#22c55e"))
        }
    }

    private fun showError(error: String) {
        runOnUiThread {
            statusText.text = "Fehler: $error"
            statusText.setTextColor(Color.parseColor("#ef4444"))
            connectButton.isEnabled = true
            connectButton.text = "Anmelden"
        }
    }

    private fun connect() {
        val serverUrl = serverUrlInput.text.toString().trim()
        val cameraName = cameraNameInput.text.toString().trim()
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "Bitte Server-URL eingeben", Toast.LENGTH_SHORT).show()
            return
        }
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            Toast.makeText(this, "URL muss mit http:// oder https:// beginnen", Toast.LENGTH_SHORT).show()
            return
        }
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Bitte Benutzername und Passwort eingeben", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 6) {
            Toast.makeText(this, "Passwort muss mindestens 6 Zeichen lang sein", Toast.LENGTH_SHORT).show()
            return
        }

        showConnecting()

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

                showSuccess("Angemeldet! Kamera wird registriert...")

                // Step 2: Register camera with backend to get UUID
                registerCamera(token, cameraName, serverUrl)
            }

            override fun onError(error: String) {
                val friendlyError = when {
                    error.contains("Unable to resolve host") ->
                        "Server nicht erreichbar. Pruefe die IP-Adresse."
                    error.contains("Connection refused") ->
                        "Server antwortet nicht. Ist der Server gestartet?"
                    error.contains("timeout", ignoreCase = true) ->
                        "Verbindung zu lange. Pruefe IP und Netzwerk."
                    error.contains("401") ->
                        "Falscher Benutzername oder Passwort."
                    error.contains("403") ->
                        "Zugang verweigert. Account deaktiviert?"
                    error.contains("ConnectException") ->
                        "Verbindung moeglich. Server IP pruefen."
                    else -> "Verbindung fehlgeschlagen: $error"
                }
                showError(friendlyError)
                Toast.makeText(this@MainActivity, friendlyError, Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun registerCamera(token: String, cameraName: String, serverUrl: String) {
        // First try to find existing camera with same name
        apiClient.listCameras(token, object : ApiClient.ApiCallback {
            override fun onSuccess(response: org.json.JSONObject) {
                // Response is a JSON array of cameras
                try {
                    val camerasArray = response.toString()
                    // Try to find camera with matching name in the list
                    // Since response might be an array, parse it
                    val array = org.json.JSONArray(camerasArray)
                    var foundCameraId: String? = null
                    for (i in 0 until array.length()) {
                        val cam = array.getJSONObject(i)
                        if (cam.optString("name") == cameraName) {
                            foundCameraId = cam.getString("id")
                            break
                        }
                    }
                    if (foundCameraId != null) {
                        // Camera exists, use its UUID
                        prefs.edit().putString(Constants.PREF_CAMERA_ID, foundCameraId).apply()
                        showSuccess("Kamera verbunden! Starte Streaming...")
                        startCameraActivity(token, cameraName, foundCameraId, serverUrl)
                    } else {
                        // Camera not found, create new one
                        createNewCamera(token, cameraName, serverUrl)
                    }
                } catch (e: Exception) {
                    // Response is a single object, create new camera
                    createNewCamera(token, cameraName, serverUrl)
                }
            }

            override fun onError(error: String) {
                // List failed, try creating camera
                createNewCamera(token, cameraName, serverUrl)
            }
        })
    }

    private fun createNewCamera(token: String, cameraName: String, serverUrl: String) {
        apiClient.registerCamera(token, cameraName, object : ApiClient.ApiCallback {
            override fun onSuccess(response: org.json.JSONObject) {
                val cameraId = response.getString("id")
                prefs.edit().putString(Constants.PREF_CAMERA_ID, cameraId).apply()
                showSuccess("Kamera registriert! Starte Streaming...")
                startCameraActivity(token, cameraName, cameraId, serverUrl)
            }

            override fun onError(error: String) {
                showError("Kamera-Registrierung fehlgeschlagen: $error")
                Toast.makeText(this@MainActivity, "Kamera konnte nicht registriert werden: $error", Toast.LENGTH_LONG).show()
                connectButton.isEnabled = true
                connectButton.text = "Anmelden"
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
