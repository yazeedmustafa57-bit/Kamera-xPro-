package com.smartcampro.app.ui

import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.BatteryManager
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
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {
    private lateinit var ts: TokenStorage
    private var camera: Camera? = null
    private var socket: Socket? = null
    private var cameraId = ""
    private var viewerCount = 0
    private var useFrontCamera = false
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService
    private var batteryLevel = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        ts = TokenStorage(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        val serverUrl = ts.getServerUrl() ?: ""
        val token = ts.getAccessToken() ?: ""
        if (serverUrl.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "Kein Server oder nicht eingeloggt", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        RetrofitClient.setBaseUrl(serverUrl)
        updateBattery()
        registerCamera(serverUrl, token)

        findViewById<Button>(R.id.switchCameraButton).setOnClickListener { switchCamera() }
        findViewById<Button>(R.id.flashButton).setOnClickListener { toggleFlash() }
        findViewById<Button>(R.id.alarmButton).setOnClickListener { triggerAlarm() }
        findViewById<Button>(R.id.pairButton).setOnClickListener { showPairingCode() }
        findViewById<Button>(R.id.stopButton).setOnClickListener { stop() }

        // Update battery every 30 seconds
        val handler = android.os.Handler(mainLooper)
        val batteryRunnable = object : Runnable {
            override fun run() {
                updateBattery()
                handler.postDelayed(this, 30000)
            }
        }
        handler.post(batteryRunnable)

        requestPermissions()
    }

    private fun requestPermissions() {
        val perms = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            perms.add(android.Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            perms.add(android.Manifest.permission.RECORD_AUDIO)
        }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)
        } else {
            startCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            startCamera()
        }
    }

    private fun registerCamera(serverUrl: String, token: String) {
        RetrofitClient.getApi().createCamera("Bearer $token", CameraCreate("Kamera", "Android")).enqueue(object : Callback<CameraResponse> {
            override fun onResponse(call: Call<CameraResponse>, response: Response<CameraResponse>) {
                if (response.isSuccessful) {
                    cameraId = response.body()!!.id
                    connectSocket(serverUrl, token)
                    runOnUiThread { Toast.makeText(this@CameraActivity, "Kamera registriert!", Toast.LENGTH_SHORT).show() }
                }
            }
            override fun onFailure(call: Call<CameraResponse>, t: Throwable) {
                runOnUiThread { Toast.makeText(this@CameraActivity, "Registrierung fehlgeschlagen: ${t.message}", Toast.LENGTH_SHORT).show() }
            }
        })
    }

    private fun connectSocket(serverUrl: String, token: String) {
        try {
            val options = IO.Options.builder()
                .setAuth(mapOf("token" to token))
                .setReconnection(true)
                .setReconnectionAttempts(10)
                .setReconnectionDelay(2000)
                .build()
            socket = IO.socket(serverUrl, options)

            socket?.on(Socket.EVENT_CONNECT) {
                runOnUiThread {
                    findViewById<TextView>(R.id.statusText).text = "● Live"
                    findViewById<TextView>(R.id.statusText).setTextColor(Color.parseColor("#22c55e"))
                }
                socket?.emit("camera:join", cameraId)
            }

            socket?.on("watcher:joined") {
                runOnUiThread {
                    viewerCount++
                    findViewById<TextView>(R.id.viewersText).text = "Zuschauer: $viewerCount"
                }
            }

            socket?.on("watcher:left") {
                runOnUiThread {
                    viewerCount = (viewerCount - 1).coerceAtLeast(0)
                    findViewById<TextView>(R.id.viewersText).text = "Zuschauer: $viewerCount"
                }
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                runOnUiThread {
                    findViewById<TextView>(R.id.statusText).text = "● Getrennt"
                    findViewById<TextView>(R.id.statusText).setTextColor(Color.parseColor("#ef4444"))
                }
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e("Camera", "Socket error: ${args[0]}")
            }

            socket?.connect()
        } catch (e: Exception) {
            Log.e("Camera", "Socket error", e)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val previewView = findViewById<PreviewView>(R.id.previewView)
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val cameraSelector = if (useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, cameraSelector, preview)
        } catch (e: Exception) {
            Log.e("Camera", "Bind failed", e)
            Toast.makeText(this, "Kamera-Bindung fehlgeschlagen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchCamera() {
        useFrontCamera = !useFrontCamera
        bindCamera()
        val camName = if (useFrontCamera) "Frontkamera" else "Rueckkamera"
        Toast.makeText(this, "Gewechselt: $camName", Toast.LENGTH_SHORT).show()
    }

    private fun toggleFlash() {
        try {
            val torchState = camera?.cameraInfo?.torchState?.value
            val newState = torchState != 1
            camera?.cameraControl?.enableTorch(newState)
            Toast.makeText(this, if (newState) "Licht AN" else "Licht AUS", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Blitz nicht verfügbar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerAlarm() {
        // Local feedback
        Toast.makeText(this, "🚨 ALARM GESCHICKT!", Toast.LENGTH_SHORT).show()

        // Vibrate
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))

        // Flash alarm
        try {
            camera?.cameraControl?.enableTorch(true)
            android.os.Handler(mainLooper).postDelayed({
                camera?.cameraControl?.enableTorch(false)
            }, 200)
            android.os.Handler(mainLooper).postDelayed({
                camera?.cameraControl?.enableTorch(true)
            }, 400)
            android.os.Handler(mainLooper).postDelayed({
                camera?.cameraControl?.enableTorch(false)
            }, 600)
        } catch (_: Exception) {}

        // Send to server
        socket?.emit("camera:alarm", JSONObject().apply {
            put("cameraId", cameraId)
            put("message", "ALARM - Bewegung erkannt!")
        })
    }

    private fun showPairingCode() {
        val token = ts.getAccessToken() ?: return
        RetrofitClient.getApi().getCamera("Bearer $token", cameraId).enqueue(object : Callback<CameraResponse> {
            override fun onResponse(call: Call<CameraResponse>, response: Response<CameraResponse>) {
                response.body()?.let { cam ->
                    runOnUiThread {
                        AlertDialog.Builder(this@CameraActivity)
                            .setTitle("Pairing-Code")
                            .setMessage("Kamera: ${cam.name}\n\nCode: ${cam.pairingCode}\n\nDiesen Code auf dem Zuschauer-Geraet eingeben.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
            override fun onFailure(call: Call<CameraResponse>, t: Throwable) {}
        })
    }

    private fun updateBattery() {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryIntent?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            batteryLevel = if (scale > 0) (level * 100 / scale) else 0
            runOnUiThread {
                findViewById<TextView>(R.id.batteryText).text = "Batterie: $batteryLevel%"
            }
        }
    }

    private fun stop() {
        socket?.emit("camera:leave", cameraId)
        socket?.disconnect()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        socket?.disconnect()
        cameraExecutor.shutdown()
    }
}
