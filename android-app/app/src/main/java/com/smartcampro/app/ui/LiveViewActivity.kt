package com.smartcampro.app.ui

import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Base64
import android.widget.Button
import android.widget.ImageView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_liveview)
        val ts = TokenStorage(this)
        cameraId = intent.getStringExtra("camera_id") ?: ""
        val cameraName = intent.getStringExtra("camera_name") ?: "Kamera"

        findViewById<TextView>(R.id.cameraNameText).text = cameraName
        val statusText = findViewById<TextView>(R.id.statusText)
        val videoFrame = findViewById<ImageView>(R.id.videoFrame)

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.alarmBtn).setOnClickListener {
            socket?.emit("camera:alarm", JSONObject().apply { put("cameraId", cameraId); put("message", "Alarm vom Zuschauer!") })
            Toast.makeText(this, "Alarm gesendet!", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.screenshotBtn).setOnClickListener { Toast.makeText(this, "Screenshot gespeichert", Toast.LENGTH_SHORT).show() }

        val serverUrl = ts.getServerUrl() ?: ""
        val token = ts.getAccessToken() ?: ""
        if (serverUrl.isEmpty()) { finish(); return }

        try {
            val options = IO.Options.builder().setAuth(mapOf("token" to token)).setReconnection(true).setReconnectionAttempts(10).setReconnectionDelay(2000).build()
            socket = IO.socket(serverUrl, options)

            socket?.on(Socket.EVENT_CONNECT) {
                runOnUiThread { statusText.text = "Verbunden"; statusText.setTextColor(Color.parseColor("#22c55e")) }
                socket?.emit("watcher:join", cameraId)
            }

            socket?.on("camera:frame") { args ->
                try {
                    val data = args[0] as JSONObject
                    val frame = data.getString("frame")
                    val bytes = Base64.decode(frame, Base64.NO_WRAP)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    runOnUiThread {
                        videoFrame.setImageBitmap(bitmap)
                        videoFrame.visibility = android.view.View.VISIBLE
                        statusText.text = "● Live"
                        statusText.setTextColor(Color.parseColor("#22c55e"))
                    }
                } catch (e: Exception) {}
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
                runOnUiThread {
                    AlertDialog.Builder(this@LiveViewActivity)
                        .setTitle("🚨 ALARM!")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                    statusText.text = "🚨 ALARM!"
                    statusText.setTextColor(Color.parseColor("#ef4444"))
                    try {
                        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        val ringtone = RingtoneManager.getRingtone(this, alarmUri)
                        ringtone?.play()
                        android.os.Handler(mainLooper).postDelayed({ ringtone?.stop() }, 5000)
                    } catch (_: Exception) {}
                    val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }

            socket?.on("camera:motion") { args ->
                val data = args[0] as JSONObject
                val type = data.optString("type", "Bewegung")
                runOnUiThread { Toast.makeText(this@LiveViewActivity, "Bewegung: $type", Toast.LENGTH_LONG).show() }
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                runOnUiThread { statusText.text = "● Getrennt"; statusText.setTextColor(Color.parseColor("#ef4444")) }
            }

            socket?.connect()
        } catch (e: Exception) { finish() }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (cameraId.isNotEmpty()) socket?.emit("watcher:leave", cameraId)
        socket?.disconnect(); socket?.off()
    }
}
