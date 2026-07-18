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
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var apiClient: ApiClient

    private lateinit var serverUrlInput: EditText
    private lateinit var cameraNameInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var connectButton: Button
    private lateinit var standaloneButton: Button
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

        // Standalone-Button hinzufuegen (falls er nicht im Layout ist)
        try {
            standaloneButton = findViewById(R.id.standaloneButton)
        } catch (e: Exception) {
            // Button nicht im Layout - erstelle ihn dynamisch
            standaloneButton = Button(this).apply {
                text = "📷 Kamera starten (ohne Server)"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#22c55e"))
                textSize = 14f
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    140
                )
                params.topMargin = 16
                layoutParams = params
            }
            // Finde den LinearLayout und fuege Button hinzu
            val parent = connectButton.parent as? LinearLayout
            parent?.addView(standaloneButton, parent.indexOfChild(connectButton) + 1)
        }

        val savedUrl = prefs.getString(Constants.PREF_SERVER_URL, "")
        if (!savedUrl.isNullOrEmpty()) {
            serverUrlInput.setText(savedUrl)
        } else {
            serverUrlInput.hint = "z.B. http://192.168.1.100:8000"
        }
        cameraNameInput.setText(prefs.getString(Constants.PREF_CAMERA_NAME, "Kamera 1"))
        usernameInput.setText(prefs.getString("username", ""))

        connectButton.setOnClickListener { connect() }
        standaloneButton.setOnClickListener { startStandalone() }

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

    private fun startStandalone() {
        val cameraName = cameraNameInput.text.toString().trim().ifEmpty { "Kamera 1" }
        showSuccess("Kamera-Modus gestartet!")
        
        val intent = Intent(this, CameraActivity::class.java).apply {
            putExtra("token", "standalone")
            putExtra("camera_name", cameraName)
            putExtra("camera_id", "standalone")
            putExtra("server_url", "")
        }
        startActivity(intent)
        finish()
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

                    showSuccess("Angemeldet! Kamera wird registriert...")
                    registerCamera(token, cameraName)

                } catch (e: Exception) {
                    showError("Login fehlgeschlagen: ${e.message}")
                    runOnUiThread {
                        connectButton.isEnabled = true
                        connectButton.text = "Anmelden"
                    }
                }
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
                    else -> "Verbindung fehlgeschlagen: $error"
                }
                showError(friendlyError)
                Toast.makeText(this@MainActivity, friendlyError, Toast.LENGTH_LONG).show()
            }
        })
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
                        showSuccess("Kamera verbunden! Starte Streaming...")
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
                    showSuccess("Kamera registriert! Starte Streaming...")
                    val serverUrl = prefs.getString(Constants.PREF_SERVER_URL, "") ?: ""
                    startCameraActivity(token, cameraName, cameraId, serverUrl)
                } catch (e: Exception) {
                    showError("Kamera-Registrierung fehlgeschlagen: ${e.message}")
                    runOnUiThread {
                        connectButton.isEnabled = true
                        connectButton.text = "Anmelden"
                    }
                }
            }

            override fun onError(error: String) {
                showError("Kamera-Registrierung fehlgeschlagen: $error")
                runOnUiThread {
                    connectButton.isEnabled = true
                    connectButton.text = "Anmelden"
                }
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
