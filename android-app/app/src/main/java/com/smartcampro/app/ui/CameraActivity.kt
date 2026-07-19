package com.smartcampro.app.ui

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.smartcampro.app.R
import com.smartcampro.app.data.api.*
import com.smartcampro.app.data.local.TokenStorage
import com.smartcampro.app.webrtc.SignalingClient
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class CameraActivity : AppCompatActivity() {
    private lateinit var tokenStorage: TokenStorage
    private var camera: Camera? = null
    private var socket: Socket? = null
    private var cameraId = ""
    private var viewerCount = 0
    private var previewView: PreviewView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        tokenStorage = TokenStorage(this)
        previewView = findViewById(R.id.previewView)

        val prefs = getSharedPreferences("smartcam_prefs", MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "") ?: ""
        val token = tokenStorage.getAccessToken() ?: ""

        if (serverUrl.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "Kein Server konfiguriert", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        registerCamera(serverUrl, token)

        findViewById<Button>(R.id.switchCameraButton).setOnClickListener { switchCamera() }
        findViewById<Button>(R.id.flashButton).setOnClickListener { toggleFlash() }
        findViewById<Button>(R.id.alarmButton).setOnClickListener { triggerAlarm() }
        findViewById<Button>(R.id.pairButton).setOnClickListener { showPairingCode() }
        findViewById<Button>(R.id.stopButton).setOnClickListener { stopCamera() }

        startCamera()
    }

    private fun registerCamera(serverUrl: String, token: String) {
        val api = RetrofitClient.getApi()
        api.createCamera("Bearer $token", CameraCreate("Kamera", "Android")).enqueue(object : Callback<CameraResponse> {
            override fun onResponse(call: Call<CameraResponse>, response: Response<CameraResponse>) {
                if (response.isSuccessful) {
                    val cam = response.body()!!
                    cameraId = cam.id
                    connectSocket(serverUrl, token)
                }
            }
            override fun onFailure(call: Call<CameraResponse>, t: Throwable) {}
        })
    }

    private fun connectSocket(serverUrl: String, token: String) {
        try {
            val options = IO.Options.builder().setAuth(mapOf("token" to token)).build()
            socket = IO.socket(serverUrl, options)

            socket?.on(Socket.EVENT_CONNECT) {
                runOnUiThread {
                    findViewById<TextView>(R.id.statusText).text = "● Live"
                    findViewById<TextView>(R.id.statusText).setTextColor(Color.parseColor("#22c55e"))
                }
                socket?.emit("camera:join", cameraId)
            }

            socket?.on("watcher:joined") { args ->
                val data = args[0] as JSONObject
                runOnUiThread {
                    viewerCount++
                    findViewById<TextView>(R.id.viewersText).text = "👁 $viewerCount"
                }
            }

            socket?.on("watcher:left") { args ->
                runOnUiThread {
                    viewerCount = (viewerCount - 1).coerceAtLeast(0)
                    findViewById<TextView>(R.id.viewersText).text = "👁 $viewerCount"
                }
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                runOnUiThread {
                    findViewById<TextView>(R.id.statusText).text = "● Getrennt"
                    findViewById<TextView>(R.id.statusText).setTextColor(Color.parseColor("#ef4444"))
                }
            }

            socket?.connect()
        } catch (e: Exception) {
            Log.e("Camera", "Socket error", e)
        }
    }

    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 100)
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView?.surfaceProvider) }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            } catch (e: Exception) {
                Log.e("Camera", "Bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun switchCamera() {}
    private fun toggleFlash() {
        try { camera?.cameraControl?.enableTorch(!(camera?.cameraInfo?.torchState?.value == 1)) } catch (_: Exception) {}
    }

    private fun triggerAlarm() {
        Toast.makeText(this, "🚨 ALARM!", Toast.LENGTH_SHORT).show()
        socket?.emit("camera:motion", JSONObject().apply { put("cameraId", cameraId); put("type", "alarm_triggered") })
    }

    private fun showPairingCode() {
        val api = RetrofitClient.getApi()
        val token = tokenStorage.getAccessToken() ?: return
        api.getCamera("Bearer $token", cameraId).enqueue(object : Callback<CameraResponse> {
            override fun onResponse(call: Call<CameraResponse>, response: Response<CameraResponse>) {
                response.body()?.let { cam ->
                    runOnUiThread {
                        AlertDialog.Builder(this@CameraActivity).setTitle("Pairing-Code").setMessage("Code: ${cam.pairingCode}").setPositiveButton("OK", null).show()
                    }
                }
            }
            override fun onFailure(call: Call<CameraResponse>, t: Throwable) {}
        })
    }

    private fun stopCamera() { socket?.disconnect(); finish() }
    override fun onDestroy() { super.onDestroy(); socket?.disconnect() }
}
