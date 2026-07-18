package com.smartcampro.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.wifi.WifiManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import com.smartcampro.app.R
import com.smartcampro.app.camera.CameraManager
import com.smartcampro.app.detection.MotionDetector
import com.smartcampro.app.network.ApiClient
import com.smartcampro.app.network.WebSocketClient
import com.smartcampro.app.services.StreamingService
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class CameraActivity : AppCompatActivity(), MotionDetector.Listener {

    private lateinit var cameraManager: CameraManager
    private lateinit var motionDetector: MotionDetector
    private lateinit var webSocketClient: WebSocketClient
    private lateinit var apiClient: ApiClient

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var batteryText: TextView
    private lateinit var fpsText: TextView
    private lateinit var switchCameraButton: Button
    private lateinit var screenshotButton: Button
    private lateinit var recordButton: Button
    private lateinit var stopButton: Button

    private var token = ""
    private var cameraName = ""
    private var cameraId = ""
    private var serverUrl = ""
    private var isConnected = false
    private var isRecording = false
    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()

    private val handler = Handler(Looper.getMainLooper())
    private val statusUpdateRunnable = object : Runnable {
        override fun run() {
            if (isConnected) {
                updateStatus()
                handler.postDelayed(this, 10000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        token = intent.getStringExtra("token") ?: ""
        cameraName = intent.getStringExtra("camera_name") ?: "Camera"
        cameraId = intent.getStringExtra("camera_id") ?: ""
        serverUrl = intent.getStringExtra("server_url") ?: ""

        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)
        batteryText = findViewById(R.id.batteryText)
        fpsText = findViewById(R.id.fpsText)
        switchCameraButton = findViewById(R.id.switchCameraButton)
        screenshotButton = findViewById(R.id.screenshotButton)
        recordButton = findViewById(R.id.recordButton)
        stopButton = findViewById(R.id.stopButton)

        if (cameraId.isEmpty()) {
            Toast.makeText(this, "Fehler: Keine Kamera-ID vorhanden", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        cameraManager = CameraManager(this)
        motionDetector = MotionDetector()
        motionDetector.setListener(this)
        motionDetector.setSensitivity(0.5f)

        webSocketClient = WebSocketClient(serverUrl, cameraId, token)
        apiClient = ApiClient(serverUrl)

        setupButtons()
        startStreaming()
    }

    private fun setupButtons() {
        switchCameraButton.setOnClickListener {
            cameraManager.switchCamera()
            startCameraPreview()
        }
        screenshotButton.setOnClickListener { takeScreenshot() }
        recordButton.setOnClickListener { toggleRecording() }
        stopButton.setOnClickListener { stopStreaming() }
    }

    private fun startStreaming() {
        statusText.text = "Kamera startet..."
        isConnected = true

        webSocketClient.connect(object : WebSocketClient.Listener {
            override fun onConnected() {
                runOnUiThread {
                    statusText.text = "● Live"
                    statusText.setTextColor(getColor(android.R.color.holo_green_light))
                }
            }
            override fun onDisconnected() {
                runOnUiThread {
                    statusText.text = "● Getrennt"
                    statusText.setTextColor(getColor(android.R.color.holo_red_light))
                    isConnected = false
                }
            }
            override fun onMessage(message: JSONObject) {
                handleWebSocketMessage(message)
            }
            override fun onError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@CameraActivity, "WS: $error", Toast.LENGTH_SHORT).show()
                }
            }
        })

        startCameraPreview()
        handler.postDelayed(statusUpdateRunnable, 10000)

        try {
            val serviceIntent = Intent(this, StreamingService::class.java)
            startForegroundService(serviceIntent)
        } catch (_: Exception) {}
    }

    private fun startCameraPreview() {
        cameraManager.setFrameCallback { bitmap -> processFrame(bitmap) }
        cameraManager.startCamera(this, previewView) {
            runOnUiThread {
                Toast.makeText(this, "Kamera bereit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processFrame(bitmap: Bitmap) {
        frameCount++
        val now = System.currentTimeMillis()
        if (now - lastFpsTime >= 1000) {
            val fps = frameCount
            frameCount = 0
            lastFpsTime = now
            runOnUiThread { fpsText.text = "FPS: $fps" }
        }

        motionDetector.processFrame(bitmap)

        if (isConnected) {
            val base64 = cameraManager.bitmapToBase64(bitmap)
            webSocketClient.sendFrame(base64)
        }
    }

    override fun onMotionDetected(confidence: Float) {
        runOnUiThread {
            Toast.makeText(this, "Bewegung erkannt! (${(confidence * 100).toInt()}%)", Toast.LENGTH_SHORT).show()
        }
        webSocketClient.sendMotionDetected(confidence)
        apiClient.sendMotionEvent(token, cameraId, "motion", confidence,
            object : ApiClient.ApiCallback {
                override fun onSuccess(response: JSONObject) {}
                override fun onError(error: String) {}
            }
        )
        cameraManager.takeScreenshot { bitmap -> bitmap?.let { saveScreenshot(it) } }
    }

    private fun takeScreenshot() {
        cameraManager.takeScreenshot { bitmap ->
            bitmap?.let {
                saveScreenshot(it)
                runOnUiThread { Toast.makeText(this, "Screenshot gespeichert", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun toggleRecording() {
        isRecording = !isRecording
        runOnUiThread {
            recordButton.text = if (isRecording) "Stop" else "Aufnahme"
            recordButton.setBackgroundColor(
                if (isRecording) getColor(android.R.color.holo_red_light)
                else getColor(android.R.color.holo_blue_dark)
            )
        }
        if (isRecording) {
            Toast.makeText(this, "Aufnahme gestartet", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Aufnahme gespeichert", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveScreenshot(bitmap: Bitmap) {
        try {
            val dir = File(filesDir, "screenshots").also { it.mkdirs() }
            val file = File(dir, "screenshot_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        } catch (_: Exception) {}
    }

    private fun getBatteryLevel(): Int {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun getWifiSignal(): Int {
        val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val info = wm.connectionInfo
        val level = WifiManager.calculateSignalLevel(info.rssi, 5)
        return (level * 100) / 4
    }

    private fun updateStatus() {
        val battery = getBatteryLevel()
        val wifi = getWifiSignal()
        webSocketClient.sendStatus("streaming", battery, wifi)
        apiClient.updateCameraStatus(token, cameraId, "streaming", battery, wifi,
            object : ApiClient.ApiCallback {
                override fun onSuccess(response: JSONObject) {}
                override fun onError(error: String) {}
            }
        )
        runOnUiThread { batteryText.text = "Batterie: $battery%" }
    }

    private fun handleWebSocketMessage(message: JSONObject) {
        when (message.optString("type", "")) {
            "command" -> when (message.optString("command", "")) {
                "stop" -> stopStreaming()
                "switch_camera" -> {
                    cameraManager.switchCamera()
                    startCameraPreview()
                }
            }
        }
    }

    private fun stopStreaming() {
        isConnected = false
        handler.removeCallbacks(statusUpdateRunnable)
        webSocketClient.disconnect()
        cameraManager.shutdown()
        try { stopService(Intent(this, StreamingService::class.java)) } catch (_: Exception) {}
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isConnected) stopStreaming()
    }
}
