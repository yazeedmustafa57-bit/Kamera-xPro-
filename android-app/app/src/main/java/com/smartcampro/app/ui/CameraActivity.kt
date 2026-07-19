package com.smartcampro.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.wifi.WifiManager
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import com.smartcampro.app.R
import com.smartcampro.app.alarm.AlarmManager
import com.smartcampro.app.camera.CameraManager
import com.smartcampro.app.detection.MotionDetector
import com.smartcampro.app.network.ApiClient
import com.smartcampro.app.network.WebSocketClient
import org.json.JSONObject
import com.smartcampro.app.recording.EventManager
import com.smartcampro.app.recording.RecordingManager
import com.smartcampro.app.services.StreamingService
import com.smartcampro.app.utils.Constants
import com.smartcampro.app.utils.NotificationHelper
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Color as AndroidColor
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class CameraActivity : AppCompatActivity(), MotionDetector.Listener {

    private lateinit var cameraManager: CameraManager
    private lateinit var motionDetector: MotionDetector
    private lateinit var alarmManager: AlarmManager
    private lateinit var recordingManager: RecordingManager
    private lateinit var eventManager: EventManager
    private lateinit var notificationHelper: NotificationHelper
    private var webSocketClient: WebSocketClient? = null
    private var apiClient: ApiClient? = null

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var batteryText: TextView
    private lateinit var fpsText: TextView
    private lateinit var recordingIndicator: LinearLayout
    private lateinit var recordingTimer: TextView
    private lateinit var recordingDot: View
    private lateinit var alarmFlashOverlay: View
    private lateinit var switchCameraButton: Button
    private lateinit var screenshotButton: Button
    private lateinit var recordButton: Button
    private lateinit var flashButton: Button
    private lateinit var alarmButton: Button
    private lateinit var settingsButton: Button
    private lateinit var stopButton: Button
    private lateinit var qrButton: Button
    private lateinit var qrOverlay: LinearLayout
    private lateinit var qrCodeImage: ImageView
    private lateinit var qrUrlText: TextView

    private var token = ""
    private var cameraName = ""
    private var cameraId = ""
    private var serverUrl = ""
    private var isStandalone = false
    private var isConnected = false
    private var isRecording = false
    private var autoRecordOnMotion = true
    private var autoFlashOnMotion = true
    private var nightOnlyFlash = true
    private var alarmOnMotion = false
    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()
    private var motionCount = 0
    private var lastRecordingFrameTime = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val statusUpdateRunnable = object : Runnable {
        override fun run() {
            if (isConnected) updateCameraStatus()
            handler.postDelayed(this, 30000)
        }
    }

    private var recordingStartTime = 0L
    private val recordingTimerRunnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                val secs = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
                recordingTimer.text = String.format("● REC %02d:%02d", secs / 60, secs % 60)
                handler.postDelayed(this, 1000)
            }
        }
    }

    private var alarmFlashActive = false
    private val alarmFlashRunnable = object : Runnable {
        override fun run() {
            if (alarmFlashActive) {
                alarmFlashOverlay.visibility = if (alarmFlashOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                handler.postDelayed(this, 200)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        token = intent.getStringExtra("token") ?: ""
        cameraName = intent.getStringExtra("camera_name") ?: "Kamera"
        cameraId = intent.getStringExtra("camera_id") ?: ""
        serverUrl = intent.getStringExtra("server_url") ?: ""
        isStandalone = token == "standalone"

        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val savedToken = prefs.getString(Constants.PREF_AUTH_TOKEN, "") ?: ""
        if (token == "standalone" && savedToken.isNotEmpty()) token = savedToken

        initViews()
        initManagers()

        if (serverUrl.isNotEmpty()) {
            apiClient = ApiClient(serverUrl)
        }

        setupButtons()
        startStreaming()
    }

    private fun initViews() {
        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)
        batteryText = findViewById(R.id.batteryText)
        fpsText = findViewById(R.id.fpsText)
        recordingIndicator = findViewById(R.id.recordingIndicator)
        recordingTimer = findViewById(R.id.recordingTimer)
        recordingDot = findViewById(R.id.recordingDot)
        alarmFlashOverlay = findViewById(R.id.alarmFlashOverlay)
        switchCameraButton = findViewById(R.id.switchCameraButton)
        screenshotButton = findViewById(R.id.screenshotButton)
        recordButton = findViewById(R.id.recordButton)
        flashButton = findViewById(R.id.flashButton)
        alarmButton = findViewById(R.id.alarmButton)
        settingsButton = findViewById(R.id.settingsButton)
        stopButton = findViewById(R.id.stopButton)
        qrButton = findViewById(R.id.qrButton)
        qrOverlay = findViewById(R.id.qrOverlay)
        qrCodeImage = findViewById(R.id.qrCodeImage)
        qrUrlText = findViewById(R.id.qrUrlText)
    }

    private fun initManagers() {
        cameraManager = CameraManager(this)
        motionDetector = MotionDetector()
        motionDetector.setListener(this)
        motionDetector.setSensitivity(0.5f)

        alarmManager = AlarmManager(this)
        alarmManager.onAlarmStateChanged = { active ->
            runOnUiThread {
                alarmButton.setBackgroundColor(if (active) getColor(android.R.color.holo_red_light) else Color.parseColor("#475569"))
            }
        }

        recordingManager = RecordingManager(this)
        recordingManager.onRecordingStateChanged = { recording ->
            runOnUiThread {
                isRecording = recording
                if (recording) {
                    recordingIndicator.visibility = View.VISIBLE
                    recordingStartTime = System.currentTimeMillis()
                    handler.post(recordingTimerRunnable)
                    recordButton.setBackgroundColor(getColor(android.R.color.holo_red_light))
                } else {
                    recordingIndicator.visibility = View.GONE
                    handler.removeCallbacks(recordingTimerRunnable)
                    recordButton.setBackgroundColor(Color.parseColor("#f59e0b"))
                }
            }
        }

        eventManager = EventManager(this)
        notificationHelper = NotificationHelper(this)
    }

    private fun setupButtons() {
        switchCameraButton.setOnClickListener { cameraManager.switchCamera(); startCameraPreview() }
        screenshotButton.setOnClickListener { takeScreenshot() }
        recordButton.setOnClickListener { toggleRecording() }
        flashButton.setOnClickListener { cameraManager.toggleTorch() }
        alarmButton.setOnClickListener { triggerAlarm() }
        qrButton.setOnClickListener { toggleQrOverlay() }
        settingsButton.setOnClickListener { /* Open settings */ }
        stopButton.setOnClickListener { stopStreaming() }
        qrOverlay.setOnClickListener { qrOverlay.visibility = View.GONE }
    }

    private fun toggleQrOverlay() {
        if (qrOverlay.visibility == View.VISIBLE) {
            qrOverlay.visibility = View.GONE
        } else {
            qrUrlText.text = serverUrl.ifEmpty { "Lokaler Modus" }
            try {
                val qrWriter = QRCodeWriter()
                val url = serverUrl.ifEmpty { "http://localhost:8080" }
                val bitMatrix = qrWriter.encode(url, BarcodeFormat.QR_CODE, 400, 400)
                val bmp = Bitmap.createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.RGB_565)
                for (x in 0 until bitMatrix.width) {
                    for (y in 0 until bitMatrix.height) {
                        bmp.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
                    }
                }
                qrCodeImage.setImageBitmap(bmp)
            } catch (_: Exception) {}
            qrOverlay.visibility = View.VISIBLE
        }
    }

    private fun triggerAlarm() {
        if (alarmManager.isAlarmActive()) {
            alarmManager.stopAlarm()
            alarmFlashActive = false
            handler.removeCallbacks(alarmFlashRunnable)
            alarmFlashOverlay.visibility = View.GONE
        } else {
            alarmManager.startAlarm(5000)
            alarmFlashActive = true
            handler.post(alarmFlashRunnable)
            eventManager.addEvent("alarm", "Alarm ausgeloest")
            notificationHelper.sendAlarmAlert("ALARM auf $cameraName!", CameraActivity::class.java)
            Toast.makeText(this, "🚨 ALARM!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startStreaming() {
        statusText.text = "● Kamera aktiv"
        statusText.setTextColor(getColor(android.R.color.holo_green_light))
        isConnected = true

        // Registriere Kamera auf Server
        if (serverUrl.isNotEmpty() && token.isNotEmpty()) {
            registerCameraOnServer()
        }

        startCameraPreview()
        handler.post(statusUpdateRunnable)
    }

    private fun registerCameraOnServer() {
        val api = ApiClient(serverUrl)
        api.listCameras(token, object : ApiClient.ApiCallback {
            override fun onSuccess(response: String) {
                try {
                    val array = org.json.JSONArray(response)
                    var found = false
                    for (i in 0 until array.length()) {
                        val cam = array.getJSONObject(i)
                        if (cam.optString("name") == cameraName) {
                            cameraId = cam.getString("id")
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        api.registerCamera(token, cameraName, object : ApiClient.ApiCallback {
                            override fun onSuccess(response: String) {
                                try {
                                    val json = JSONObject(response)
                                    cameraId = json.getString("id")
                                    Log.d("CameraActivity", "Camera registered: $cameraId")
                                } catch (_: Exception) {}
                            }
                            override fun onError(error: String) {}
                        })
                    }
                } catch (_: Exception) {}
            }
            override fun onError(error: String) {}
        })
    }

    private fun startCameraPreview() {
        cameraManager.setFrameCallback { bitmap -> processFrame(bitmap) }
        cameraManager.startCamera(this, previewView) {
            runOnUiThread { batteryText.text = "🔋 ${getBatteryLevel()}%" }
        }
    }

    private fun processFrame(bitmap: Bitmap) {
        frameCount++
        val now = System.currentTimeMillis()
        if (now - lastFpsTime >= 1000) {
            val fps = frameCount; frameCount = 0; lastFpsTime = now
            runOnUiThread { fpsText.text = "FPS: $fps" }
        }

        motionDetector.processFrame(bitmap)

        if (isRecording && (now - lastRecordingFrameTime >= 200)) {
            recordingManager.addFrame(bitmap)
            lastRecordingFrameTime = now
        }
    }

    override fun onMotionDetected(confidence: Float) {
        motionCount++
        val pct = (confidence * 100).toInt()

        runOnUiThread {
            statusText.text = "● Bewegung #$motionCount"
            statusText.setTextColor(getColor(android.R.color.holo_orange_light))
        }

        // Event loggen
        cameraManager.takeScreenshot { bitmap ->
            bitmap?.let {
                val path = saveScreenshot(it)
                eventManager.addEvent("motion", "Bewegung erkannt ($pct%)", path)
            }
        }

        notificationHelper.sendMotionAlert("Bewegung #$motionCount auf $cameraName ($pct%)", CameraActivity::class.java)

        if (autoRecordOnMotion && !isRecording) {
            runOnUiThread {
                toggleRecording()
                handler.postDelayed({ if (isRecording) toggleRecording() }, 10000)
            }
        }

        if (autoFlashOnMotion && (!nightOnlyFlash || isNightTime()) && !cameraManager.isTorchEnabled()) {
            cameraManager.flashTorch(3, 200)
        }

        if (alarmOnMotion && !alarmManager.isAlarmActive()) {
            runOnUiThread { alarmManager.startAlarm(5000); alarmFlashActive = true; handler.post(alarmFlashRunnable) }
        }
    }

    private fun takeScreenshot() {
        cameraManager.takeScreenshot { bitmap ->
            bitmap?.let {
                val path = saveScreenshot(it)
                eventManager.addEvent("photo", "Screenshot", path)
                runOnUiThread { Toast.makeText(this, "Screenshot gespeichert", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun toggleRecording() {
        if (isRecording) { recordingManager.stopRecording(); Toast.makeText(this, "Aufnahme gespeichert", Toast.LENGTH_SHORT).show() }
        else { recordingManager.startRecording(); Toast.makeText(this, "Aufnahme gestartet", Toast.LENGTH_SHORT).show() }
    }

    private fun saveScreenshot(bitmap: Bitmap): String? {
        return try {
            val dir = File(filesDir, "screenshots").also { it.mkdirs() }
            val file = File(dir, "screenshot_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            file.absolutePath
        } catch (_: Exception) { null }
    }

    private fun getBatteryLevel(): Int {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun getWifiSignal(): Int {
        val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION") val info = wm.connectionInfo
        return (WifiManager.calculateSignalLevel(info.rssi, 5) * 100) / 4
    }

    private fun updateCameraStatus() {
        if (serverUrl.isEmpty() || token.isEmpty() || cameraId.isEmpty()) return
        apiClient?.updateCameraStatus(token, cameraId, "streaming", getBatteryLevel(), getWifiSignal(), object : ApiClient.ApiCallback {
            override fun onSuccess(response: String) {}
            override fun onError(error: String) {}
        })
    }

    private fun isNightTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 18 || hour < 6
    }

    private fun stopStreaming() {
        isConnected = false
        handler.removeCallbacksAndMessages(null)
        alarmFlashActive = false
        if (isRecording) recordingManager.stopRecording()
        alarmManager.stopAlarm()
        cameraManager.shutdown()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isConnected) stopStreaming()
    }
}
