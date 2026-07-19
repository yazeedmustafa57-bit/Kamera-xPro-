package com.smartcampro.app.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.smartcampro.app.R
import com.smartcampro.app.utils.Constants
import okhttp3.*
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

class ViewerActivity : AppCompatActivity() {

    private lateinit var cameraList: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var connectionStatus: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var polling = false
    private var baseUrl = ""
    private var authToken = ""

    data class CameraDevice(
        var id: String,
        var name: String,
        var status: String,
        var battery: Int,
        var wifi: Int
    )

    private val cameras = mutableListOf<CameraDevice>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        cameraList = findViewById(R.id.cameraList)
        emptyState = findViewById(R.id.emptyState)
        connectionStatus = findViewById(R.id.connectionStatus)

        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        baseUrl = prefs.getString("cloud_server", "") ?: ""
        authToken = prefs.getString(Constants.PREF_AUTH_TOKEN, "") ?: ""

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }

        if (baseUrl.isEmpty() || authToken.isEmpty()) {
            connectionStatus.text = "🔴 Kein Server"
            connectionStatus.setTextColor(Color.parseColor("#ef4444"))
            showEmptyState()
            return
        }

        connectionStatus.text = "🟡 Verbinde..."
        loadCameras()
    }

    private fun loadCameras() {
        val request = Request.Builder()
            .url("$baseUrl/api/cameras/")
            .addHeader("Authorization", "Bearer $authToken")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    connectionStatus.text = "🔴 Offline"
                    connectionStatus.setTextColor(Color.parseColor("#ef4444"))
                    showEmptyState()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: "[]"
                try {
                    val array = JSONArray(body)
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
                        } else {
                            connectionStatus.text = "🟢 ${cameras.size} Kamera(s)"
                            connectionStatus.setTextColor(Color.parseColor("#22c55e"))
                            showCameras()
                            startPolling()
                        }
                    }
                } catch (_: Exception) {
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
            cameraList.addView(createCameraCard(camera))
        }
    }

    private fun createCameraCard(camera: CameraDevice): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
            background = getDrawable(R.drawable.bg_card)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 12
            layoutParams = lp
        }

        // Header row
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Status indicator
        val statusBg = if (camera.status == "online") R.drawable.bg_status_online else R.drawable.bg_status_offline
        val indicator = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(10, 10)
            background = getDrawable(statusBg)
            setPadding(0, 0, 10, 0)
        }

        val nameText = TextView(this).apply {
            text = camera.name
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(10, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val statusLabel = TextView(this).apply {
            text = if (camera.status == "online") "Live" else "Offline"
            setTextColor(if (camera.status == "online") Color.parseColor("#22c55e") else Color.parseColor("#64748b"))
            textSize = 12f
        }

        header.addView(indicator)
        header.addView(nameText)
        header.addView(statusLabel)
        card.addView(header)

        // Info row
        val infoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 12)
        }

        infoRow.addView(TextView(this).apply {
            text = "🔋 ${camera.battery}%"
            setTextColor(Color.parseColor("#94a3b8"))
            textSize = 12f
            setPadding(0, 0, 16, 0)
        })

        infoRow.addView(TextView(this).apply {
            text = "📶 ${camera.wifi}%"
            setTextColor(Color.parseColor("#94a3b8"))
            textSize = 12f
        })

        card.addView(infoRow)

        // Action button
        if (camera.status == "online") {
            val viewBtn = Button(this).apply {
                text = "▶ Live anschauen"
                setTextColor(Color.WHITE)
                background = getDrawable(R.drawable.bg_button_green)
                textSize = 14f
                minimumWidth = 0
                minimumHeight = 0
                setPadding(32, 14, 32, 14)
                setOnClickListener { openLiveView(camera) }
            }
            card.addView(viewBtn)
        } else {
            card.addView(TextView(this).apply {
                text = "Kamera ist offline"
                setTextColor(Color.parseColor("#475569"))
                textSize = 12f
                setPadding(0, 4, 0, 0)
            })
        }

        return card
    }

    private fun openLiveView(camera: CameraDevice) {
        startActivity(Intent(this, LiveViewActivity::class.java).apply {
            putExtra("camera_id", camera.id)
            putExtra("camera_name", camera.name)
            putExtra("server_url", baseUrl)
            putExtra("auth_token", authToken)
        })
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!polling) return
                refreshCameras()
                handler.postDelayed(this, 10000)
            }
        }, 10000)
    }

    private fun refreshCameras() {
        val request = Request.Builder()
            .url("$baseUrl/api/cameras/")
            .addHeader("Authorization", "Bearer $authToken")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                try {
                    val array = JSONArray(body)
                    for (i in 0 until array.length()) {
                        val cam = array.getJSONObject(i)
                        val id = cam.getString("id")
                        cameras.find { it.id == id }?.apply {
                            status = cam.optString("status", "offline")
                            battery = cam.optInt("battery", 100)
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
