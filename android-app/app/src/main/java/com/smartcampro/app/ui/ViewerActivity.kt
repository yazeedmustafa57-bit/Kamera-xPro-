package com.smartcampro.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.smartcampro.app.R
import com.smartcampro.app.data.api.*
import com.smartcampro.app.data.local.TokenStorage
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ViewerActivity : AppCompatActivity() {
    private lateinit var ts: TokenStorage
    private lateinit var cameraList: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var connectionStatus: TextView
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
        connectionStatus = findViewById(R.id.connectionStatus)
        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }

        val headerLayout = findViewById<LinearLayout>(R.id.headerLayout)
        val scanQrBtn = Button(this).apply {
            text = "QR Scan"
            setTextColor(Color.WHITE)
            background = getDrawable(R.drawable.bg_button_primary)
            textSize = 12f
            minimumWidth = 0; minimumHeight = 0
            setPadding(16, 8, 16, 8)
            setOnClickListener { startQRScanner() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 48).apply { marginStart = 8 }
        }
        headerLayout?.addView(scanQrBtn)

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
                        connectionStatus.text = "$onlineCount/${cams.size} Online"
                        cameraList.removeAllViews()
                        cams.forEach { cam -> cameraList.addView(createCard(cam)) }
                    }
                }
            }
            override fun onFailure(call: Call<List<CameraResponse>>, t: Throwable) {
                runOnUiThread { connectionStatus.text = "Offline"; connectionStatus.setTextColor(Color.parseColor("#ef4444")) }
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
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(12, 12); background = getDrawable(if (isOnline) R.drawable.bg_status_online else R.drawable.bg_status_offline) })
        header.addView(TextView(this).apply { text = cam.name; setTextColor(Color.WHITE); textSize = 16f; setPadding(12, 0, 0, 0); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        header.addView(TextView(this).apply { text = if (isOnline) "● Live" else "● Offline"; setTextColor(if (isOnline) Color.parseColor("#22c55e") else Color.parseColor("#64748b")); textSize = 13f })
        card.addView(header)
        if (isOnline) {
            card.addView(Button(this).apply {
                text = "Live anschauen"
                setTextColor(Color.WHITE)
                background = getDrawable(R.drawable.bg_button_green)
                textSize = 14f; minimumWidth = 0; minimumHeight = 0; setPadding(32, 14, 32, 14)
                setOnClickListener {
                    startActivity(Intent(this@ViewerActivity, LiveViewActivity::class.java).apply {
                        putExtra("camera_id", cam.id); putExtra("camera_name", cam.name)
                    })
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 12 }
            })
        }
        return card
    }

    private fun startQRScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 200)
            return
        }
        openQRScanner()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openQRScanner()
        }
    }

    private fun openQRScanner() {
        val scannerView = DecoratedBarcodeView(this)
        scannerView.setStatusText("QR-Code scannen")

        val dialog = AlertDialog.Builder(this)
            .setTitle("Kamera-QR scannen")
            .setView(scannerView)
            .setNegativeButton("Abbrechen") { _, _ -> scannerView.pause() }
            .create()
        dialog.setCancelable(false)
        dialog.show()

        scannerView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                result?.text?.let { qrText ->
                    runOnUiThread {
                        scannerView.pause()
                        dialog.dismiss()
                        handleQRCode(qrText)
                    }
                }
            }
        })
        scannerView.resume()
    }

    private fun handleQRCode(qrText: String) {
        try {
            val data = JSONObject(qrText)
            val serverUrl = data.getString("server")
            val cameraId = data.getString("camera")
            val token = data.getString("token")

            ts.saveServerUrl(serverUrl)
            ts.saveTokens(token, "")
            RetrofitClient.setBaseUrl(serverUrl)

            RetrofitClient.getApi().getCamera("Bearer $token", cameraId).enqueue(object : Callback<CameraResponse> {
                override fun onResponse(call: Call<CameraResponse>, response: Response<CameraResponse>) {
                    if (response.isSuccessful) {
                        val cam = response.body()!!
                        runOnUiThread {
                            AlertDialog.Builder(this@ViewerActivity)
                                .setTitle("Kamera gefunden!")
                                .setMessage("${cam.name}\n${if (cam.status == "online") "● Live" else "● Offline"}")
                                .setPositiveButton("Verbinden") { _, _ ->
                                    startActivity(Intent(this@ViewerActivity, LiveViewActivity::class.java).apply {
                                        putExtra("camera_id", cam.id); putExtra("camera_name", cam.name)
                                    })
                                }
                                .setNegativeButton("Abbrechen", null)
                                .show()
                        }
                    } else {
                        runOnUiThread { Toast.makeText(this@ViewerActivity, "Kamera nicht gefunden", Toast.LENGTH_SHORT).show() }
                    }
                }
                override fun onFailure(call: Call<CameraResponse>, t: Throwable) {
                    runOnUiThread { Toast.makeText(this@ViewerActivity, "Fehler: ${t.message}", Toast.LENGTH_SHORT).show() }
                }
            })
        } catch (e: Exception) {
            Toast.makeText(this, "Ungueltiger QR-Code", Toast.LENGTH_SHORT).show()
        }
    }
}
