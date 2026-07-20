package com.smartcampro.app.ui

import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.BatteryManager
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.smartcampro.app.R
import com.smartcampro.app.data.api.*
import com.smartcampro.app.data.local.TokenStorage
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
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
    private var frameCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        ts = TokenStorage(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        val serverUrl = ts.getServerUrl() ?: ""
        val token = ts.getAccessToken() ?: ""
        if (serverUrl.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "Kein Server", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        RetrofitClient.setBaseUrl(serverUrl)
        updateBattery()
        loadOrCreateCamera(serverUrl, token)

        findViewById<Button>(R.id.switchCameraButton).setOnClickListener { switchCamera() }
        findViewById<Button>(R.id.flashButton).setOnClickListener { toggleFlash() }
        findViewById<Button>(R.id.alarmButton).setOnClickListener { triggerAlarm() }
        findViewById<Button>(R.id.pairButton).setOnClickListener { showQRCode() }
        findViewById<Button>(R.id.stopButton).setOnClickListener { stop() }

        val handler = android.os.Handler(mainLooper)
        handler.post(object : Runnable {
            override fun run() { updateBattery(); handler.postDelayed(this, 30000) }
        })

        requestPermissions()
    }

    private fun requestPermissions() {
        val perms = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            perms.add(android.Manifest.permission.CAMERA)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            perms.add(android.Manifest.permission.RECORD_AUDIO)
        if (perms.isNotEmpty())
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)
        else
            startCamera()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) startCamera()
    }

    private fun loadOrCreateCamera(serverUrl: String, token: String) {
        RetrofitClient.getApi().listCameras("Bearer $token").enqueue(object : Callback<List<CameraResponse>> {
            override fun onResponse(call: Call<List<CameraResponse>>, response: Response<List<CameraResponse>>) {
                if (response.isSuccessful) {
                    val cams = response.body() ?: emptyList()
                    if (cams.isNotEmpty()) {
                        cameraId = cams[0].id
                        runOnUiThread { Toast.makeText(this@CameraActivity, "Verbunden: ${cams[0].name}", Toast.LENGTH_SHORT).show() }
                        connectSocket(serverUrl, token)
                    } else {
                        createCamera(serverUrl, token)
                    }
                } else {
                    createCamera(serverUrl, token)
                }
            }
            override fun onFailure(call: Call<List<CameraResponse>>, t: Throwable) { createCamera(serverUrl, token) }
        })
    }

    private fun createCamera(serverUrl: String, token: String) {
        RetrofitClient.getApi().createCamera("Bearer $token", CameraCreate("Kamera", "Android")).enqueue(object : Callback<CameraResponse> {
            override fun onResponse(call: Call<CameraResponse>, response: Response<CameraResponse>) {
                if (response.isSuccessful) {
                    cameraId = response.body()!!.id
                    connectSocket(serverUrl, token)
                    runOnUiThread { Toast.makeText(this@CameraActivity, "Kamera erstellt!", Toast.LENGTH_SHORT).show() }
                }
            }
            override fun onFailure(call: Call<CameraResponse>, t: Throwable) {}
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

            socket?.connect()
        } catch (e: Exception) { Log.e("Camera", "Socket error", e) }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            bindCameraWithAnalysis()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraWithAnalysis() {
        val provider = cameraProvider ?: return
        val previewView = findViewById<PreviewView>(R.id.previewView)

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            if (viewerCount > 0 && socket?.connected() == true) {
                sendFrame(imageProxy)
            } else {
                imageProxy.close()
            }
        }

        val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, selector, preview, imageAnalysis)
        } catch (e: Exception) {
            Log.e("Camera", "Bind failed", e)
            // Fallback without analysis
            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, selector, preview)
            } catch (_: Exception) {}
        }
    }

    private fun sendFrame(imageProxy: ImageProxy) {
        try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            // Convert to JPEG
            val yuvImage = YuvImage(bytes, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 50, out)
            val jpegBytes = out.toByteArray()

            // Compress more if too large
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            val scaled = Bitmap.createScaledBitmap(bitmap, 480, 360, true)
            val out2 = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 40, out2)
            val finalBytes = out2.toByteArray()

            val base64 = Base64.encodeToString(finalBytes, Base64.NO_WRAP)

            socket?.emit("camera:frame", JSONObject().apply {
                put("cameraId", cameraId)
                put("frame", base64)
            })

            frameCount++
            if (frameCount % 30 == 0) {
                runOnUiThread {
                    findViewById<TextView>(R.id.viewersText).text = "Zuschauer: $viewerCount | FPS: ~5"
                }
            }

            bitmap.recycle()
            scaled.recycle()
        } catch (e: Exception) {
            Log.e("Camera", "Frame error", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun switchCamera() {
        useFrontCamera = !useFrontCamera
        bindCameraWithAnalysis()
        Toast.makeText(this, if (useFrontCamera) "Frontkamera" else "Rueckkamera", Toast.LENGTH_SHORT).show()
    }

    private fun toggleFlash() {
        try {
            val torchOn = camera?.cameraInfo?.torchState?.value != 1
            camera?.cameraControl?.enableTorch(torchOn)
            Toast.makeText(this, if (torchOn) "Licht AN" else "Licht AUS", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) { Toast.makeText(this, "Blitz nicht verfuegbar", Toast.LENGTH_SHORT).show() }
    }

    private fun triggerAlarm() {
        Toast.makeText(this, "🚨 ALARM!", Toast.LENGTH_SHORT).show()
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        try {
            camera?.cameraControl?.enableTorch(true)
            val h = android.os.Handler(mainLooper)
            h.postDelayed({ camera?.cameraControl?.enableTorch(false) }, 200)
            h.postDelayed({ camera?.cameraControl?.enableTorch(true) }, 400)
            h.postDelayed({ camera?.cameraControl?.enableTorch(false) }, 600)
        } catch (_: Exception) {}
        socket?.emit("camera:alarm", JSONObject().apply { put("cameraId", cameraId); put("message", "ALARM!") })
    }

    private fun showQRCode() {
        if (cameraId.isEmpty()) { Toast.makeText(this, "Kamera wird verbunden...", Toast.LENGTH_SHORT).show(); return }
        val serverUrl = ts.getServerUrl() ?: return
        val token = ts.getAccessToken() ?: return

        val qrData = JSONObject().apply {
            put("server", serverUrl)
            put("camera", cameraId)
            put("token", token)
        }.toString()

        val qrBitmap = generateQRCode(qrData, 600)

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(48, 48, 48, 48); setBackgroundColor(Color.parseColor("#1e293b"))
        }
        dialogView.addView(TextView(this).apply { text = "QR-Code scannen"; setTextColor(Color.WHITE); textSize = 20f; setPadding(0, 0, 0, 24); gravity = Gravity.CENTER })
        dialogView.addView(ImageView(this).apply { setImageBitmap(qrBitmap); setPadding(32, 32, 32, 32) })
        dialogView.addView(TextView(this).apply { text = "Oeffne Zuschauer-App\nund druecke 'QR Scan'"; setTextColor(Color.parseColor("#94a3b8")); textSize = 16f; gravity = Gravity.CENTER; setPadding(0, 16, 0, 0) })

        AlertDialog.Builder(this).setView(dialogView).setPositiveButton("Schliessen", null).show()
    }

    private fun generateQRCode(data: String, size: Int): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size)
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
        return bitmap
    }

    private fun updateBattery() {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        intent?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            batteryLevel = if (scale > 0) (level * 100 / scale) else 0
            runOnUiThread { findViewById<TextView>(R.id.batteryText).text = "Batterie: $batteryLevel%" }
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
