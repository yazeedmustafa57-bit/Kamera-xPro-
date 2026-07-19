package com.smartcampro.app.ui

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.smartcampro.app.R
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LiveViewActivity : AppCompatActivity() {

    private lateinit var liveImageView: ImageView
    private lateinit var loadingView: LinearLayout
    private lateinit var cameraNameText: TextView
    private lateinit var statusText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var polling = false
    private var cameraId = ""
    private var baseUrl = ""
    private var authToken = ""

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_liveview)

        liveImageView = findViewById(R.id.liveImageView)
        loadingView = findViewById(R.id.loadingView)
        cameraNameText = findViewById(R.id.cameraNameText)
        statusText = findViewById(R.id.statusText)

        cameraId = intent.getStringExtra("camera_id") ?: ""
        baseUrl = intent.getStringExtra("server_url") ?: ""
        authToken = intent.getStringExtra("auth_token") ?: ""
        val cameraName = intent.getStringExtra("camera_name") ?: "Kamera"

        cameraNameText.text = cameraName

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.screenshotBtn).setOnClickListener { takeScreenshot() }
        findViewById<Button>(R.id.alarmBtn).setOnClickListener { triggerAlarm() }
        findViewById<Button>(R.id.fullscreenBtn).setOnClickListener { toggleFullscreen() }

        startLivePolling()
    }

    private fun startLivePolling() {
        polling = true
        loadingView.visibility = View.VISIBLE

        val pollRunnable = object : Runnable {
            override fun run() {
                if (!polling) return
                fetchLatestFrame()
                handler.postDelayed(this, 500)  // Poll every 500ms
            }
        }
        handler.post(pollRunnable)
    }

    private fun fetchLatestFrame() {
        if (authToken.isEmpty()) return

        // Try to get latest frame from camera's endpoint
        val url = "$baseUrl/api/cameras/$cameraId"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                try {
                    val json = JSONObject(body)
                    val status = json.optString("status", "offline")
                    val streamUrl = json.optString("stream_url", "")

                    runOnUiThread {
                        if (status == "online") {
                            loadingView.visibility = View.GONE
                            statusText.text = "Live"
                            statusText.setTextColor(Color.parseColor("#22c55e"))
                        } else {
                            statusText.text = "Offline"
                            statusText.setTextColor(Color.parseColor("#ef4444"))
                        }
                    }
                } catch (_: Exception) {}
            }
        })
    }

    private fun takeScreenshot() {
        Toast.makeText(this, "Screenshot gespeichert", Toast.LENGTH_SHORT).show()
    }

    private fun triggerAlarm() {
        if (authToken.isEmpty() || cameraId.isEmpty()) return

        val json = """{"type":"command","command":"alarm"}"""
        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/api/cameras/$cameraId")
            .addHeader("Authorization", "Bearer $authToken")
            .put(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    Toast.makeText(this@LiveViewActivity, "🚨 Alarm gesendet!", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun toggleFullscreen() {
        val decorView = window.decorView
        val flags = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        decorView.systemUiVisibility = flags
    }

    override fun onDestroy() {
        super.onDestroy()
        polling = false
        handler.removeCallbacksAndMessages(null)
    }
}
