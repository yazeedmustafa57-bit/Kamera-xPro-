package com.smartcampro.app.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)
        ts = TokenStorage(this)
        cameraList = findViewById(R.id.cameraList)
        emptyState = findViewById(R.id.emptyState)
        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
        loadCameras()
    }

    private fun loadCameras() {
        val token = ts.getAccessToken() ?: return
        RetrofitClient.getApi().listCameras("Bearer $token").enqueue(object : Callback<List<CameraResponse>> {
            override fun onResponse(call: Call<List<CameraResponse>>, response: Response<List<CameraResponse>>) {
                if (response.isSuccessful) {
                    val cams = response.body() ?: emptyList()
                    runOnUiThread {
                        if (cams.isEmpty()) { emptyState.visibility = View.VISIBLE; cameraList.visibility = View.GONE; return@runOnUiThread }
                        emptyState.visibility = View.GONE; cameraList.visibility = View.VISIBLE
                        findViewById<TextView>(R.id.connectionStatus).text = "${cams.size} Kamera(s) gefunden"
                        cameraList.removeAllViews()
                        cams.forEach { cam -> cameraList.addView(createCard(cam)) }
                    }
                }
            }
            override fun onFailure(call: Call<List<CameraResponse>>, t: Throwable) { runOnUiThread { findViewById<TextView>(R.id.connectionStatus).text = "Offline - Server nicht erreichbar" } }
        })
    }

    private fun createCard(cam: CameraResponse): LinearLayout {
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 16, 20, 16); background = getDrawable(R.drawable.bg_card); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 } }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(10, 10); background = getDrawable(if (cam.status == "online") R.drawable.bg_status_online else R.drawable.bg_status_offline) })
        header.addView(TextView(this).apply { text = cam.name; setTextColor(Color.WHITE); textSize = 16f; setPadding(10, 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        header.addView(TextView(this).apply { text = if (cam.status == "online") "Live" else "Offline"; setTextColor(if (cam.status == "online") Color.parseColor("#22c55e") else Color.parseColor("#64748b")); textSize = 12f })
        card.addView(header)
        if (cam.status == "online") {
            card.addView(Button(this).apply { text = "Live anschauen"; setTextColor(Color.WHITE); background = getDrawable(R.drawable.bg_button_green); textSize = 14f; minimumWidth = 0; minimumHeight = 0; setPadding(32, 14, 32, 14); setOnClickListener { startActivity(Intent(this@ViewerActivity, LiveViewActivity::class.java).apply { putExtra("camera_id", cam.id); putExtra("camera_name", cam.name) }) }; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 12 } })
        }
        return card
    }
}
