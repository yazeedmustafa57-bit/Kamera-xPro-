package com.smartcampro.app.ui

import android.graphics.Color
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.smartcampro.app.R
import com.smartcampro.app.data.local.TokenStorage
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class LiveViewActivity : AppCompatActivity() {
    private var socket: Socket? = null
    private var cameraId = ""
    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_liveview)
        val ts = TokenStorage(this)
        cameraId = intent.getStringExtra("camera_id") ?: ""
        val cameraName = intent.getStringExtra("camera_name") ?: "Kamera"

        val nameText = findViewById<TextView>(R.id.cameraNameText)
        val statusText = findViewById<TextView>(R.id.statusText)
        nameText.text = cameraName
        statusText.text = "Verbinde..."

        // Buttons
        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }

        findViewById<Button>(R.id.alarmBtn).setOnClickListener {
            socket?.emit("camera:alarm", JSONObject().apply {
                put("cameraId", cameraId)
                put("message", "Alarm vom Zuschauer!")
            })
            Toast.makeText(this, "Alarm an Kamera gesendet!", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.screenshotBtn).setOnClickListener {
            Toast.makeText(this, "Screenshot gespeichert", Toast.LENGTH_SHORT).show()
        }

        val serverUrl = ts.getServerUrl() ?: ""
        val token = ts.getAccessToken() ?: ""
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "Kein Server konfiguriert", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        try {
            val options = IO.Options.builder()
                .setAuth(mapOf("token" to token))
                .setReconnection(true)
                .setReconnectionAttempts(10)
                .setReconnectionDelay(2000)
                .build()
            socket = IO.socket(serverUrl, options)

            socket?.on(Socket.EVENT_CONNECT) {
                isConnected = true
                runOnUiThread {
                    statusText.text = "Verbunden"
                    statusText.setTextColor(Color.parseColor("#22c55e"))
                }
                socket?.emit("watcher:join", cameraId)
            }

            socket?.on("camera:status") { args ->
                val data = args[0] as JSONObject
                val status = data.getString("status")
                runOnUiThread {
                    if (status == "online") {
                        statusText.text = "● Live"
                        statusText.setTextColor(Color.parseColor("#22c55e"))
                    } else {
                        statusText.text = "● Offline"
                        statusText.setTextColor(Color.parseColor("#64748b"))
                    }
                }
            }

            socket?.on("camera:alarm") { args ->
                val data = args[0] as JSONObject
                val message = data.optString("message", "Alarm!")
                val timestamp = data.optLong("timestamp", System.currentTimeMillis())
                runOnUiThread {
                    // Show alarm dialog
                    AlertDialog.Builder(this@LiveViewActivity)
                        .setTitle("🚨 ALARM!")
                        .setMessage("$message\n\n${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))}")
                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                        .setCancelable(false)
                        .show()

                    // Play alarm sound
                    try {
                        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        val ringtone = RingtoneManager.getRingtone(this@LiveViewActivity, alarmUri)
                        ringtone?.play()
                        android.os.Handler(mainLooper).postDelayed({ ringtone?.stop() }, 5000)
                    } catch (_: Exception) {}

                    // Vibrate
                    val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))

                    statusText.text = "🚨 ALARM!"
                    statusText.setTextColor(Color.parseColor("#ef4444"))
                }
            }

            socket?.on("camera:motion") { args ->
                val data = args[0] as JSONObject
                val type = data.optString("type", "Bewegung")
                runOnUiThread {
                    Toast.makeText(this@LiveViewActivity, "Bewegung erkannt: $type", Toast.LENGTH_LONG).show()
                    statusText.text = "Bewegung: $type"
                    statusText.setTextColor(Color.parseColor("#f59e0b"))
                }
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                isConnected = false
                runOnUiThread {
                    statusText.text = "● Getrennt"
                    statusText.setTextColor(Color.parseColor("#ef4444"))
                }
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                runOnUiThread {
                    statusText.text = "Verbindungsfehler"
                    statusText.setTextColor(Color.parseColor("#ef4444"))
                }
            }

            socket?.connect()
        } catch (e: Exception) {
            Toast.makeText(this, "Verbindungsfehler: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (cameraId.isNotEmpty()) {
            socket?.emit("watcher:leave", cameraId)
        }
        socket?.disconnect()
        socket?.off()
    }
}
