package com.smartcampro.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.smartcampro.app.R
import com.smartcampro.app.utils.Constants
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ViewerActivity : AppCompatActivity() {

    private lateinit var cameraList: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var connectionStatus: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var polling = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class CameraDevice(
        var id: String,
        var name: String,
        var status: String,
        var battery: Int,
        var wifi: Int,
        var latestFrame: Bitmap? = null
    )

    private val cameras = mutableListOf<CameraDevice>()
    private var baseUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        cameraList = findViewById(R.id.cameraList)
        emptyState = findViewById(R.id.emptyState)
        connectionStatus = findViewById(R.id.connectionStatus)

        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        baseUrl = prefs.getString("cloud_server", "") ?: ""
        val token = prefs.getString(Constants.PREF_AUTH_TOKEN, "") ?: ""

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }

        if (baseUrl.isEmpty() || token.isEmpty()) {
            connectionStatus.text = "🔴 Kein Server"
            showEmptyState()
            return
        }

        connectionStatus.text = "🟡 Verbinde..."
        loadCameras(token)
    }

    private fun loadCameras(token: String) {
        val request = Request.Builder()
            .url("$baseUrl/api/cameras/")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    connectionStatus.text = "🔴 Offline"
                    showEmptyState()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: "[]"
                try {
                    val array = org.json.JSONArray(body)
                    cameras.clear()
                    for (i in 0 until array.length()) {
                        val cam = array.getJSONObject(i)
                        cameras.add(
                            CameraDevice(
                                id = cam.getString("id"),
                                name = cam.getString("name"),
                                status = cam.optString("status", "offline"),
                                battery = cam.optInt("battery", 100),
                                wifi = cam.optInt("wifi_signal", 100)
                            )
                        )
                    }
                    runOnUiThread {
                        if (cameras.isEmpty()) {
                            showEmptyState()
                            connectionStatus.text = "🟢 Verbunden"
                        } else {
                            connectionStatus.text = "🟢 ${cameras.size} Kamera(s)"
                            showCameras()
                            startPolling(token)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { showEmptyState() }
                }
            }
        })
    }

    private fun showEmptyState() {
        emptyState.visibility = View.VISIBLE
        cameraList.visibility = View.GONE
    }

    private fun showCameras() {
        emptyState.visibility = View.GONE
        cameraList.visibility = View.VISIBLE
        cameraList.removeAllViews()

        cameras.forEach { camera ->
            val card = createCameraCard(camera)
            cameraList.addView(card)
        }
    }

    private fun createCameraCard(camera: CameraDevice): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.parseColor("#1e293b"))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 8
            layoutParams = lp
        }

        // Camera name and status
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val statusDot = TextView(this).apply {
            text = if (camera.status == "online") "🟢" else "🔴"
            textSize = 16f
            setPadding(0, 0, 8, 0)
        }

        val nameText = TextView(this).apply {
            text = camera.name
            setTextColor(Color.WHITE)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val batteryText = TextView(this).apply {
            text = "🔋${camera.battery}%"
            setTextColor(Color.parseColor("#94a3b8"))
            textSize = 12f
        }

        headerRow.addView(statusDot)
        headerRow.addView(nameText)
        headerRow.addView(batteryText)
        card.addView(headerRow)

        // Status text
        val statusText = TextView(this).apply {
            text = if (camera.status == "online") "Live - Tippen zum Anschauen" else "Offline"
            setTextColor(if (camera.status == "online") Color.parseColor("#22c55e") else Color.parseColor("#64748b"))
            textSize = 12f
            setPadding(0, 4, 0, 8)
        }
        card.addView(statusText)

        // Action buttons
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        if (camera.status == "online") {
            val viewBtn = Button(this).apply {
                text = "▶ Live anschauen"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#22c55e"))
                textSize = 13f
                minimumWidth = 0
                minimumHeight = 0
                setPadding(32, 12, 32, 12)
                setOnClickListener {
                    openLiveView(camera)
                }
            }
            buttonRow.addView(viewBtn)
        }

        card.addView(buttonRow)
        return card
    }

    private fun openLiveView(camera: CameraDevice) {
        // Try to get live frame from server via polling
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val token = prefs.getString(Constants.PREF_AUTH_TOKEN, "") ?: ""

        val intent = Intent(this, LiveViewActivity::class.java).apply {
            putExtra("camera_id", camera.id)
            putExtra("camera_name", camera.name)
            putExtra("server_url", baseUrl)
            putExtra("auth_token", token)
        }
        startActivity(intent)
    }

    private fun startPolling(token: String) {
        if (polling) return
        polling = true

        val pollRunnable = object : Runnable {
            override fun run() {
                if (!polling) return
                refreshCameras(token)
                handler.postDelayed(this, 10000)
            }
        }
        handler.post(pollRunnable)
    }

    private fun refreshCameras(token: String) {
        val request = Request.Builder()
            .url("$baseUrl/api/cameras/")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: "[]"
                try {
                    val array = org.json.JSONArray(body)
                    for (i in 0 until array.length()) {
                        val cam = array.getJSONObject(i)
                        val id = cam.getString("id")
                        val existing = cameras.find { it.id == id }
                        if (existing != null) {
                            existing.status = cam.optString("status", "offline")
                            existing.battery = cam.optInt("battery", 100)
                            existing.wifi = cam.optInt("wifi_signal", 100)
                        }
                    }
                    runOnUiThread { showCameras() }
                } catch (_: Exception) {}
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        polling = false
        handler.removeCallbacksAndMessages(null)
    }
}
