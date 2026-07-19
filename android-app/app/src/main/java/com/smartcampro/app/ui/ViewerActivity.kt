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
import com.smartcampro.app.data.api.*
import com.smartcampro.app.data.local.TokenStorage
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ViewerActivity : AppCompatActivity() {
    private lateinit var ts: TokenStorage
    private lateinit var cameraList: LinearLayout
    private lateinit var emptyState: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadCameras()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)
        ts = TokenStorage(this)
        cameraList = findViewById(R.id.cameraList)
        emptyState = findViewById(R.id.emptyState)
        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
        loadCameras()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun loadCameras() {
        val token = ts.getAccessToken() ?: return
        val serverUrl = ts.getServerUrl() ?: return
        RetrofitClient.setBaseUrl(serverUrl)
        RetrofitClient.getApi().listCameras("Bearer $token").enqueue(object : Callback<List<CameraResponse>> {
            override fun onResponse(call: Call<List<CameraResponse>>, response: Response<List<CameraResponse>>) {
                if (response.isSuccessful) {
                    val cams = response.body() ?: emptyList()
                    runOnUiThread {
                        if (cams.isEmpty()) {
                            emptyState.visibility = View.VISIBLE
                            cameraList.visibility = View.GONE
                            return@runOnUiThread
                        }
                        emptyState.visibility = View.GONE
                        cameraList.visibility = View.VISIBLE
                        val onlineCount = cams.count { it.status == "online" }
                        findViewById<TextView>(R.id.connectionStatus).text = "$onlineCount/${cams.size} Online"
                        cameraList.removeAllViews()
                        cams.forEach { cam -> cameraList.addView(createCard(cam)) }
                    }
                }
            }
            override fun onFailure(call: Call<List<CameraResponse>>, t: Throwable) {
                runOnUiThread {
                    findViewById<TextView>(R.id.connectionStatus).text = "Offline"
                    findViewById<TextView>(R.id.connectionStatus).setTextColor(Color.parseColor("#ef4444"))
                }
            }
        })
    }

    private fun createCard(cam: CameraResponse): LinearLayout {
        val isOnline = cam.status == "online"
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
            background = getDrawable(R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
        }

        // Header row
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(12, 12)
            background = getDrawable(if (isOnline) R.drawable.bg_status_online else R.drawable.bg_status_offline)
        })
        header.addView(TextView(this).apply {
            text = cam.name
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(12, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(this).apply {
            text = if (isOnline) "● Live" else "● Offline"
            setTextColor(if (isOnline) Color.parseColor("#22c55e") else Color.parseColor("#64748b"))
            textSize = 13f
        })
        card.addView(header)

        // Device info
        card.addView(TextView(this).apply {
            text = "Gerät: ${cam.deviceModel ?: "Unbekannt"}"
            setTextColor(Color.parseColor("#64748b"))
            textSize = 12f
            setPadding(24, 8, 0, 0)
        })

        // Button row
        if (isOnline) {
            card.addView(Button(this).apply {
                text = "Live anschauen"
                setTextColor(Color.WHITE)
                background = getDrawable(R.drawable.bg_button_green)
                textSize = 14f
                minimumWidth = 0
                minimumHeight = 0
                setPadding(32, 14, 32, 14)
                setOnClickListener {
                    startActivity(Intent(this@ViewerActivity, LiveViewActivity::class.java).apply {
                        putExtra("camera_id", cam.id)
                        putExtra("camera_name", cam.name)
                    })
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 12 }
            })
        } else {
            card.addView(TextView(this).apply {
                text = "Kamera ist offline — wird automatisch aktualisiert"
                setTextColor(Color.parseColor("#475569"))
                textSize = 12f
                setPadding(0, 12, 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
        }

        return card
    }
}
