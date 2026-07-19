package com.smartcampro.app.ui

import android.content.Intent
import android.content.SharedPreferences
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
import com.smartcampro.app.recording.EventManager
import com.smartcampro.app.recording.RecordingManager
import com.smartcampro.app.server.LocalStreamingServer
import com.smartcampro.app.services.StreamingService
import com.smartcampro.app.utils.Constants
import com.smartcampro.app.utils.NotificationHelper
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
    private lateinit var alarmManager: AlarmManager
    private lateinit var recordingManager: RecordingManager
    private lateinit var eventManager: EventManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var prefs: SharedPreferences
    private var webSocketClient: WebSocketClient? = null
    private var apiClient: ApiClient? = null
    private var localServer: LocalStreamingServer? = null

    // UI Elements
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
    private lateinit var qrButton: Button
    private lateinit var settingsButton: Button
    private lateinit var stopButton: Button
    private lateinit var qrOverlay: LinearLayout
    private lateinit var qrCodeImage: ImageView
    private lateinit var qrUrlText: TextView
    private lateinit var qrStatusText: TextView
    private lateinit var settingsOverlay: LinearLayout

    // Settings
    private lateinit var flashToggle: Switch
    private lateinit var autoFlashToggle: Switch
    private lateinit var nightOnlyToggle: Switch
    private lateinit var alarmToggle: Switch
    private lateinit var alarmBlinkToggle: Switch
    private lateinit var vibrationToggle: Switch
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var autoRecordToggle: Switch
    private lateinit var sensitivitySeekBar: SeekBar
    private lateinit var closeSettingsButton: Button

    // State
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
    private var alarmBlinkEnabled = true
    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()
    private var motionCount = 0
    private var lastRecordingFrameTime = 0L
    private val recordingFrameIntervalMs = 200L

    private val handler = Handler(Looper.getMainLooper())
    private val statusUpdateRunnable = object : Runnable {
        override fun run() {
            if (isConnected && !isStandalone) {
                updateServerStatus()
                handler.postDelayed(this, 10000)
            }
        }
    }

    private var recordingStartTime = 0L
    private val recordingTimerRunnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                val elapsed = System.currentTimeMillis() - recordingStartTime
                val secs = (elapsed / 1000).toInt()
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

        prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        token = intent.getStringExtra("token") ?: ""
        cameraName = intent.getStringExtra("camera_name") ?: "Camera"
        cameraId = intent.getStringExtra("camera_id") ?: ""
        serverUrl = intent.getStringExtra("server_url") ?: ""
        isStandalone = token == "standalone"

        initViews()
        initManagers()
        loadSettings()
        setupButtons()
        setupSettings()
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
        qrButton = findViewById(R.id.qrButton)
        settingsButton = findViewById(R.id.settingsButton)
        stopButton = findViewById(R.id.stopButton)
        qrOverlay = findViewById(R.id.qrOverlay)
        qrCodeImage = findViewById(R.id.qrCodeImage)
        qrUrlText = findViewById(R.id.qrUrlText)
        qrStatusText = findViewById(R.id.qrStatusText)
        settingsOverlay = findViewById(R.id.settingsOverlay)

        flashToggle = findViewById(R.id.flashToggle)
        autoFlashToggle = findViewById(R.id.autoFlashToggle)
        nightOnlyToggle = findViewById(R.id.nightOnlyToggle)
        alarmToggle = findViewById(R.id.alarmToggle)
        alarmBlinkToggle = findViewById(R.id.alarmBlinkToggle)
        vibrationToggle = findViewById(R.id.vibrationToggle)
        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        autoRecordToggle = findViewById(R.id.autoRecordToggle)
        sensitivitySeekBar = findViewById(R.id.sensitivitySeekBar)
        closeSettingsButton = findViewById(R.id.closeSettingsButton)
    }

    private fun initManagers() {
        cameraManager = CameraManager(this)
        motionDetector = MotionDetector()
        motionDetector.setListener(this)

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

        if (!isStandalone) {
            webSocketClient = WebSocketClient(serverUrl, cameraId, token)
            apiClient = ApiClient(serverUrl)
        }
    }

    private fun loadSettings() {
        val sensitivity = prefs.getInt("sensitivity", 50)
        val volume = prefs.getInt("alarm_volume", 70)
        val nightMode = prefs.getBoolean("night_mode", true)
        val autoAlarmNight = prefs.getBoolean("auto_alarm_night", true)
        val autoFlashNight = prefs.getBoolean("auto_flash_night", true)
        val alarmOnMotionPref = prefs.getBoolean("alarm_on_motion", false)

        motionDetector.setSensitivity((sensitivity.coerceIn(5, 100)) / 110f)
        alarmManager.setVolume(volume)
        nightOnlyFlash = nightMode
        alarmOnMotion = alarmOnMotionPref
        autoFlashOnMotion = autoFlashNight

        sensitivitySeekBar.progress = sensitivity
        volumeSeekBar.progress = volume
        nightOnlyToggle.isChecked = nightMode
        
        autoFlashToggle.isChecked = autoFlashNight
        alarmToggle.isChecked = alarmOnMotionPref
    }

    private fun setupButtons() {
        switchCameraButton.setOnClickListener {
            cameraManager.switchCamera()
            startCameraPreview()
        }
        screenshotButton.setOnClickListener { takeScreenshot() }
        recordButton.setOnClickListener { toggleRecording() }
        flashButton.setOnClickListener { toggleFlash() }
        alarmButton.setOnClickListener { triggerAlarm() }
        qrButton.setOnClickListener { toggleQrOverlay() }
        settingsButton.setOnClickListener { toggleSettings() }
        stopButton.setOnClickListener { stopStreaming() }
        qrOverlay.setOnClickListener { toggleQrOverlay() }
        closeSettingsButton.setOnClickListener { saveAndCloseSettings() }
    }

    private fun setupSettings() {
        flashToggle.setOnCheckedChangeListener { _, isChecked ->
            cameraManager.setTorch(isChecked)
            flashButton.setBackgroundColor(if (isChecked) getColor(android.R.color.holo_orange_light) else Color.parseColor("#475569"))
        }
        autoFlashToggle.setOnCheckedChangeListener { _, isChecked -> autoFlashOnMotion = isChecked }
        nightOnlyToggle.setOnCheckedChangeListener { _, isChecked -> nightOnlyFlash = isChecked }
        alarmToggle.setOnCheckedChangeListener { _, isChecked -> alarmOnMotion = isChecked }
        alarmBlinkToggle.setOnCheckedChangeListener { _, isChecked -> alarmBlinkEnabled = isChecked }
        vibrationToggle.setOnCheckedChangeListener { _, isChecked -> alarmManager.setVibration(isChecked) }

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                alarmManager.setVolume(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        autoRecordToggle.setOnCheckedChangeListener { _, isChecked -> autoRecordOnMotion = isChecked }

        sensitivitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                motionDetector.setSensitivity((progress.coerceIn(5, 100)) / 110f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun saveAndCloseSettings() {
        prefs.edit().apply {
            putInt("sensitivity", sensitivitySeekBar.progress.coerceIn(5, 100))
            putInt("alarm_volume", volumeSeekBar.progress.coerceIn(0, 100))
            putBoolean("night_mode", nightOnlyToggle.isChecked)
            putBoolean("auto_alarm_night", alarmToggle.isChecked)
            putBoolean("auto_flash_night", autoFlashToggle.isChecked)
            putBoolean("alarm_on_motion", alarmToggle.isChecked)
            apply()
        }
        settingsOverlay.visibility = View.GONE
    }

    private fun toggleFlash() {
        cameraManager.toggleTorch()
        val isOn = cameraManager.isTorchEnabled()
        flashButton.setBackgroundColor(if (isOn) getColor(android.R.color.holo_orange_light) else Color.parseColor("#475569"))
        flashToggle.isChecked = isOn
    }

    private fun triggerAlarm() {
        if (alarmManager.isAlarmActive()) {
            alarmManager.stopAlarm()
            stopAlarmFlash()
        } else {
            alarmManager.startAlarm(5000)
            if (alarmBlinkEnabled) startAlarmFlash()
            cameraManager.takeScreenshot { bitmap ->
                bitmap?.let {
                    val path = saveScreenshot(it)
                    eventManager.addEvent("alarm", "Alarm ausgeloest", path)
                }
            }
            notificationHelper.sendAlarmAlert("Alarm ausgeloest!", CameraActivity::class.java)
            Toast.makeText(this, "🚨 ALARM!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startAlarmFlash() {
        alarmFlashActive = true
        handler.post(alarmFlashRunnable)
    }

    private fun stopAlarmFlash() {
        alarmFlashActive = false
        handler.removeCallbacks(alarmFlashRunnable)
        alarmFlashOverlay.visibility = View.GONE
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
        qrStatusText.text = "Server laeuft auf Port 8080"
        try {
            val qrWriter = QRCodeWriter()
            val bitMatrix: BitMatrix = qrWriter.encode(url, BarcodeFormat.QR_CODE, 400, 400)
            val bmp = Bitmap.createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.RGB_565)
            for (x in 0 until bitMatrix.width) {
                for (y in 0 until bitMatrix.height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
                }
            }
            qrCodeImage.setImageBitmap(bmp)
        } catch (e: Exception) {
            Toast.makeText(this, "QR-Code Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleSettings() {
        settingsOverlay.visibility = if (settingsOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun isNightTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 18 || hour < 6
    }

    private fun startStreaming() {
        if (isStandalone) {
            statusText.text = "● Kamera aktiv"
            statusText.setTextColor(getColor(android.R.color.holo_green_light))
            localServer = LocalStreamingServer(port = 8080) { msg -> Log.d("CameraActivity", "Server: $msg") }
            localServer?.start()
            Toast.makeText(this, "Zuschauer: ${localServer?.getUrl()}", Toast.LENGTH_LONG).show()
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
                override fun onMessage(message: org.json.JSONObject) { handleWebSocketMessage(message) }
                override fun onError(error: String) {
                    runOnUiThread { Toast.makeText(this@CameraActivity, "WS: $error", Toast.LENGTH_SHORT).show() }
                }
            })
        }
        startCameraPreview()
        startStatusUpdates()
    }

    private fun startStatusUpdates() { handler.post(statusUpdateRunnable) }

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

        if (isStandalone) {
            localServer?.pushFrame(bitmap)
            localServer?.updateStatus(getBatteryLevel(), getWifiSignal(), cameraName, motionCount)
        }

        if (isConnected && !isStandalone) {
            webSocketClient?.sendFrame(cameraManager.bitmapToBase64(bitmap))
        }

        if (isRecording && (now - lastRecordingFrameTime >= recordingFrameIntervalMs)) {
            recordingManager.addFrame(bitmap)
            lastRecordingFrameTime = now
        }
    }

    override fun onMotionDetected(confidence: Float) {
        motionCount++
        val pct = (confidence * 100).toInt()
        runOnUiThread {
            Toast.makeText(this, "Bewegung #$motionCount! ($pct%)", Toast.LENGTH_SHORT).show()
            statusText.text = "● Bewegung #$motionCount"
            statusText.setTextColor(getColor(android.R.color.holo_orange_light))
        }

        if (!isStandalone) {
            webSocketClient?.sendMotionDetected(confidence)
            apiClient?.sendMotionEvent(token, cameraId, "motion", confidence, object : ApiClient.ApiCallback {
                override fun onSuccess(response: String) {}
                override fun onError(error: String) {}
            })
        }

        // Screenshot + Event
        cameraManager.takeScreenshot { bitmap ->
            bitmap?.let {
                val path = saveScreenshot(it)
                eventManager.addEvent("motion", "Bewegung erkannt ($pct%)", path)
            }
        }

        // Android Notification
        notificationHelper.sendMotionAlert("Bewegung #$motionCount auf $cameraName ($pct%)", CameraActivity::class.java)

        // Auto-Aufnahme
        if (autoRecordOnMotion && !isRecording) {
            runOnUiThread {
                toggleRecording()
                handler.postDelayed({ if (isRecording) toggleRecording() }, 10000)
            }
        }

        // Auto-Taschenlampe
        if (autoFlashOnMotion && (!nightOnlyFlash || isNightTime()) && !cameraManager.isTorchEnabled()) {
            cameraManager.flashTorch(3, 200)
        }

        // Auto-Alarm
        if (alarmOnMotion && !alarmManager.isAlarmActive()) {
            runOnUiThread {
                alarmManager.startAlarm(5000)
                if (alarmBlinkEnabled) startAlarmFlash()
            }
        }

        // Notification an Zuschauer
        localServer?.addNotification("Bewegung erkannt!", "motion")
    }

    private fun takeScreenshot() {
        cameraManager.takeScreenshot { bitmap ->
            bitmap?.let {
                val path = saveScreenshot(it)
                eventManager.addEvent("photo", "Screenshot erstellt", path)
                runOnUiThread { Toast.makeText(this, "Screenshot gespeichert", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun toggleRecording() {
        if (isRecording) {
            recordingManager.stopRecording()
            Toast.makeText(this, "Aufnahme gespeichert", Toast.LENGTH_SHORT).show()
        } else {
            recordingManager.startRecording(isAutomatic = false)
            Toast.makeText(this, "Aufnahme gestartet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveScreenshot(bitmap: Bitmap): String? {
        return try {
            val dir = File(filesDir, "screenshots").also { it.mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(dir, "screenshot_${timestamp}.jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            file.absolutePath
        } catch (e: Exception) { null }
    }

    private fun getBatteryLevel(): Int {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun getWifiSignal(): Int {
        val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION") val info = wm.connectionInfo
        val level = WifiManager.calculateSignalLevel(info.rssi, 5)
        return (level * 100) / 4
    }

    private fun updateServerStatus() {
        val battery = getBatteryLevel()
        val wifi = getWifiSignal()
        webSocketClient?.sendStatus("streaming", battery, wifi)
        apiClient?.updateCameraStatus(token, cameraId, "streaming", battery, wifi, object : ApiClient.ApiCallback {
            override fun onSuccess(response: String) {}
            override fun onError(error: String) {}
        })
        runOnUiThread { batteryText.text = "🔋 $battery%" }
    }

    private fun handleWebSocketMessage(message: org.json.JSONObject) {
        when (message.optString("type", "")) {
            "command" -> when (message.optString("command", "")) {
                "stop" -> stopStreaming()
                "switch_camera" -> { cameraManager.switchCamera(); startCameraPreview() }
                "flash_on" -> cameraManager.setTorch(true)
                "flash_off" -> cameraManager.setTorch(false)
                "alarm" -> { alarmManager.startAlarm(5000); if (alarmBlinkEnabled) startAlarmFlash() }
                "record_start" -> { if (!isRecording) toggleRecording() }
                "record_stop" -> { if (isRecording) toggleRecording() }
            }
        }
    }

    private fun stopStreaming() {
        isConnected = false
        handler.removeCallbacks(statusUpdateRunnable)
        handler.removeCallbacks(recordingTimerRunnable)
        stopAlarmFlash()
        if (isRecording) recordingManager.stopRecording()
        alarmManager.stopAlarm()
        localServer?.stop(); localServer = null
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
