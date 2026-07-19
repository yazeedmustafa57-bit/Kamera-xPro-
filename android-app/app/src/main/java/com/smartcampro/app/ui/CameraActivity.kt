package com.smartcampro.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.wifi.WifiManager
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import com.smartcampro.app.R
import com.smartcampro.app.camera.CameraManager
import com.smartcampro.app.detection.MotionDetector
import com.smartcampro.app.network.ApiClient
import com.smartcampro.app.network.WebSocketClient
import com.smartcampro.app.server.LocalStreamingServer
import com.smartcampro.app.services.StreamingService
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.common.BitMatrix
import android.graphics.Color as AndroidColor
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class CameraActivity : AppCompatActivity(), MotionDetector.Listener {

    private lateinit var cameraManager: CameraManager
    private lateinit var motionDetector: MotionDetector
    private var webSocketClient: WebSocketClient? = null
    private var apiClient: ApiClient? = null
    private var localServer: LocalStreamingServer? = null

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var batteryText: TextView
    private lateinit var fpsText: TextView
    private lateinit var switchCameraButton: Button
    private lateinit var screenshotButton: Button
    private lateinit var recordButton: Button
    private lateinit var qrButton: Button
    private lateinit var stopButton: Button
    private lateinit var qrOverlay: LinearLayout
    private lateinit var qrCodeImage: ImageView
    private lateinit var qrUrlText: TextView
    private lateinit var qrStatusText: TextView

    private var token = ""
    private var cameraName = ""
    private var cameraId = ""
    private var serverUrl = ""
    private var isStandalone = false
    private var isConnected = false
    private var isRecording = false
    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()
    private var motionCount = 0

    private val handler = Handler(Looper.getMainLooper())
    private val statusUpdateRunnable = object : Runnable {
        override fun run() {
            if (isConnected && !isStandalone) {
                updateServerStatus()
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
        isStandalone = token == "standalone"

        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)
        batteryText = findViewById(R.id.batteryText)
        fpsText = findViewById(R.id.fpsText)
        switchCameraButton = findViewById(R.id.switchCameraButton)
        screenshotButton = findViewById(R.id.screenshotButton)
        recordButton = findViewById(R.id.recordButton)
        qrButton = findViewById(R.id.qrButton)
        stopButton = findViewById(R.id.stopButton)
        qrOverlay = findViewById(R.id.qrOverlay)
        qrCodeImage = findViewById(R.id.qrCodeImage)
        qrUrlText = findViewById(R.id.qrUrlText)
        qrStatusText = findViewById(R.id.qrStatusText)

        cameraManager = CameraManager(this)
        motionDetector = MotionDetector()
        motionDetector.setListener(this)
        motionDetector.setSensitivity(0.5f)

        if (!isStandalone) {
            webSocketClient = WebSocketClient(serverUrl, cameraId, token)
            apiClient = ApiClient(serverUrl)
        }

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
        qrButton.setOnClickListener { toggleQrOverlay() }
        stopButton.setOnClickListener { stopStreaming() }

        // QR-Overlay schliessen bei Tippen
        qrOverlay.setOnClickListener { toggleQrOverlay() }
    }

    private fun toggleQrOverlay() {
        if (qrOverlay.visibility == View.VISIBLE) {
            qrOverlay.visibility = View.GONE
        } else {
            showQrCode()
            qrOverlay.visibility = View.VISIBLE
        }
    }

    private fun showQrCode() {
        val url = localServer?.getUrl() ?: return
        qrUrlText.text = url
        qrStatusText.text = "Server läuft auf Port 8080"

        try {
            val qrWriter = QRCodeWriter()
            val bitMatrix: BitMatrix = qrWriter.encode(url, BarcodeFormat.QR_CODE, 400, 400)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
                }
            }
            qrCodeImage.setImageBitmap(bmp)
        } catch (e: Exception) {
            Toast.makeText(this, "QR-Code Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startStreaming() {
        if (isStandalone) {
            statusText.text = "● Kamera aktiv"
            statusText.setTextColor(getColor(android.R.color.holo_green_light))

            // LocalStreamingServer starten
            localServer = LocalStreamingServer(port = 8080) { msg ->
                Log.d("CameraActivity", "Server: $msg")
            }
            localServer?.start()

            val url = localServer?.getUrl() ?: "unbekannt"
            Toast.makeText(this, "Zuschauer: $url", Toast.LENGTH_LONG).show()

            isConnected = true
        } else {
            statusText.text = "Verbinde mit Server..."
            isConnected = true
            webSocketClient?.connect(object : WebSocketClient.Listener {
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
                override fun onMessage(message: org.json.JSONObject) {
                    handleWebSocketMessage(message)
                }
                override fun onError(error: String) {
                    runOnUiThread {
                        Toast.makeText(this@CameraActivity, "WS: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }

        startCameraPreview()
        startStatusUpdates()
    }

    private fun startStatusUpdates() {
        handler.post(statusUpdateRunnable)
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

        // An LocalStreamingServer senden (Zuschauer-Modus)
        if (isStandalone) {
            localServer?.pushFrame(bitmap)
            localServer?.updateStatus(
                getBatteryLevel(),
                getWifiSignal(),
                cameraName,
                motionCount
            )
        }

        // An WebSocket senden (Server-Modus)
        if (isConnected && !isStandalone) {
            val base64 = cameraManager.bitmapToBase64(bitmap)
            webSocketClient?.sendFrame(base64)
        }
    }

    override fun onMotionDetected(confidence: Float) {
        motionCount++
        runOnUiThread {
            Toast.makeText(this, "Bewegung #${motionCount}! (${(confidence * 100).toInt()}%)", Toast.LENGTH_SHORT).show()
            statusText.text = "● Bewegung #${motionCount}"
            statusText.setTextColor(getColor(android.R.color.holo_orange_light))
        }

        if (!isStandalone) {
            webSocketClient?.sendMotionDetected(confidence)
            apiClient?.sendMotionEvent(token, cameraId, "motion", confidence,
                object : ApiClient.ApiCallback {
                    override fun onSuccess(response: String) {}
                    override fun onError(error: String) {}
                }
            )
        }

        // Screenshot bei Bewegung speichern
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
            recordButton.text = if (isRecording) "⏹" else "🔴"
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
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(dir, "screenshot_${timestamp}.jpg")
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
        @Suppress("DEPRECATION")
        val info = wm.connectionInfo
        val level = WifiManager.calculateSignalLevel(info.rssi, 5)
        return (level * 100) / 4
    }

    private fun updateServerStatus() {
        val battery = getBatteryLevel()
        val wifi = getWifiSignal()
        webSocketClient?.sendStatus("streaming", battery, wifi)
        apiClient?.updateCameraStatus(token, cameraId, "streaming", battery, wifi,
            object : ApiClient.ApiCallback {
                override fun onSuccess(response: String) {}
                override fun onError(error: String) {}
            }
        )
        runOnUiThread { batteryText.text = "🔋 $battery%" }
    }

    private fun handleWebSocketMessage(message: org.json.JSONObject) {
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

        // Server stoppen
        localServer?.stop()
        localServer = null

        webSocketClient?.disconnect()
        cameraManager.shutdown()
        try { stopService(Intent(this, StreamingService::class.java)) } catch (_: Exception) {}
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isConnected) stopStreaming()
    }
}
