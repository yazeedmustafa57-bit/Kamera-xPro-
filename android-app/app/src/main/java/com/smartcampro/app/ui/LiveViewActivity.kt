package com.smartcampro.app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.smartcampro.app.R
import com.smartcampro.app.data.local.TokenStorage
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class LiveViewActivity : AppCompatActivity() {
    private lateinit var tokenStorage: TokenStorage
    private var socket: Socket? = null
    private var cameraId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_liveview)
        tokenStorage = TokenStorage(this)
        cameraId = intent.getStringExtra("camera_id") ?: ""
        val cameraName = intent.getStringExtra("camera_name") ?: "Kamera"

        findViewById<TextView>(R.id.cameraNameText).text = cameraName
        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.alarmBtn).setOnClickListener {
            socket?.emit("webrtc:offer", JSONObject().apply { put("cameraId", cameraId) })
            Toast.makeText(this, "🚨 Alarm gesendet!", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.screenshotBtn).setOnClickListener { Toast.makeText(this, "Screenshot gespeichert", Toast.LENGTH_SHORT).show() }

        val prefs = getSharedPreferences("smartcam_prefs", MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "") ?: ""
        val token = tokenStorage.getAccessToken() ?: ""

        if (serverUrl.isEmpty()) { finish(); return }

        try {
            val options = IO.Options.builder().setAuth(mapOf("token" to token)).build()
            socket = IO.socket(serverUrl, options)
            socket?.on(Socket.EVENT_CONNECT) {
                runOnUiThread { findViewById<TextView>(R.id.statusText).text = "Live" }
                socket?.emit("watcher:join", cameraId)
            }
            socket?.on("camera:status") { args ->
                val data = args[0] as JSONObject
                runOnUiThread { findViewById<TextView>(R.id.statusText).text = if (data.getString("status") == "online") "Live" else "Offline" }
            }
            socket?.connect()
        } catch (e: Exception) { finish() }
    }

    override fun onDestroy() { super.onDestroy(); socket?.disconnect(); socket?.off() }
}
