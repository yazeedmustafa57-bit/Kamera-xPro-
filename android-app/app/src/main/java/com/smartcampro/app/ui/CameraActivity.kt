package com.smartcampro.app.ui

import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.os.BatteryManager
import android.graphics.ImageFormat
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
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
        findViewById<Button>(R.id.stopButton).setOnClickListener { stop() }
        findViewById<Button>(R.id.qrButton).setOnClickListener { showQRCode() }

        android.os.Handler(mainLooper).post(object : Runnable {
            override fun run() { updateBattery(); android.os.Handler(mainLooper).postDelayed(this, 30000) }
        })

        requestPermissions()
    }

    private fun requestPermissions() {
        val perms = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            perms.add(android.Manifest.permission.CAMERA)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            perms.add(android.Manifest.permission.RECORD_AUDIO)
        if (perms.isNotEmpty()) ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100) else startCamera()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) startCamera()
    }

    private fun loadOrCreateCamera(serverUrl: String, token: String) {
        RetrofitClient.getApi().listCameras("Bearer $token").enqueue(object : Callback<List<CameraResponse>> {
            override fun onResponse(call: Call<List<CameraResponse>>, response: Response<List<CameraResponse>>) {
                if (response.isSuccessful && response.body().isNullOrEmpty().not()) {
                    cameraId = response.body()!![0].id
                    connectSocket(serverUrl, token)
                } else createCamera(serverUrl, token)
            }
            override fun onFailure(call: Call<List<CameraResponse>>, t: Throwable) { createCamera(serverUrl, token) }
        })
    }

    private fun createCamera(serverUrl: String, token: String) {
        RetrofitClient.getApi().createCamera("Bearer $token", CameraCreate("Kamera", "Android")).enqueue(object : Callback<CameraResponse> {
            override fun onResponse(call: Call<CameraResponse>, response: Response<CameraResponse>) {
                if (response.isSuccessful) { cameraId = response.body()!!.id; connectSocket(serverUrl, token) }
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
                viewerCount++
                runOnUiThread { findViewById<TextView>(R.id.viewersText).text = "Zuschauer: $viewerCount" }
            }

            socket?.on("watcher:left") {
                viewerCount = (viewerCount - 1).coerceAtLeast(0)
                runOnUiThread { findViewById<TextView>(R.id.viewersText).text = "Zuschauer: $viewerCount" }
            }

            // REMOTE COMMANDS from iPhone
            socket?.on("remote:flash") { args ->
                try {
                    val data = args[0] as JSONObject
                    val on = data.optBoolean("on", true)
                    runOnUiThread {
                        try { camera?.cameraControl?.enableTorch(on); Toast.makeText(this, if (on) "Licht AN" else "Licht AUS", Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }

            socket?.on("remote:switch") {
                runOnUiThread { switchCamera(); Toast.makeText(this, "Kamera gewechselt", Toast.LENGTH_SHORT).show() }
            }

            socket?.on("remote:alarm") {
                runOnUiThread { triggerAlarmLocal(); Toast.makeText(this, "ALARM empfangen!", Toast.LENGTH_SHORT).show() }
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
        future.addListener({ cameraProvider = future.get(); bindCamera() }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val previewView = findViewById<PreviewView>(R.id.previewView)
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            if (viewerCount > 0 && socket?.connected() == true && cameraId.isNotEmpty()) {
                try {
                    val bitmap = toBitmap(imageProxy)
                    if (bitmap != null) {
                        val scaled = Bitmap.createScaledBitmap(bitmap, 480, 360, true)
                        val out = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 40, out)
                        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

                        socket?.emit("camera:frame", JSONObject().apply {
                            put("cameraId", cameraId)
                            put("frame", b64)
                        })

                        scaled.recycle()
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    Log.e("Camera", "Frame error: ${e.message}")
                }
            }
            imageProxy.close()
        }

        val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try { provider.unbindAll(); camera = provider.bindToLifecycle(this, selector, preview, imageAnalysis) }
        catch (e: Exception) {
            try { provider.unbindAll(); camera = provider.bindToLifecycle(this, selector, preview) } catch (_: Exception) {}
        }
    }

    private fun toBitmap(imageProxy: ImageProxy): Bitmap? {
        try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            // Try direct JPEG if available
            if (imageProxy.format == ImageFormat.JPEG) {
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }

            // YUV_420_888 to NV21 conversion
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = android.graphics.YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height), 40, out)
            return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        } catch (e: Exception) {
            Log.e("Camera", "toBitmap error: ${e.message}")
            return null
        }
    }

    private fun switchCamera() {
        useFrontCamera = !useFrontCamera
        bindCamera()
    }

    private fun toggleFlash() {
        try {
            val on = camera?.cameraInfo?.torchState?.value != 1
            camera?.cameraControl?.enableTorch(on)
            Toast.makeText(this, if (on) "Licht AN" else "Licht AUS", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) { Toast.makeText(this, "Blitz nicht verfügbar", Toast.LENGTH_SHORT).show() }
    }

    private fun triggerAlarm() {
        triggerAlarmLocal()
        socket?.emit("camera:alarm", JSONObject().apply { put("cameraId", cameraId); put("message", "ALARM!") })
    }

    private fun triggerAlarmLocal() {
        Toast.makeText(this, "ALARM!", Toast.LENGTH_SHORT).show()
        val v = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
        v?.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        try {
            camera?.cameraControl?.enableTorch(true)
            val h = android.os.Handler(mainLooper)
            h.postDelayed({ camera?.cameraControl?.enableTorch(false) }, 200)
            h.postDelayed({ camera?.cameraControl?.enableTorch(true) }, 400)
            h.postDelayed({ camera?.cameraControl?.enableTorch(false) }, 600)
        } catch (_: Exception) {}
    }

    private fun showQRCode() {
        if (cameraId.isEmpty()) { Toast.makeText(this, "Kamera wird verbunden...", Toast.LENGTH_SHORT).show(); return }
        val serverUrl = ts.getServerUrl() ?: return
        val token = ts.getAccessToken() ?: return
        val qrData = JSONObject().apply { put("server", serverUrl); put("camera", cameraId); put("token", token) }.toString()
        val qrBitmap = generateQRCode(qrData, 500)
        val dv = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(32, 32, 32, 32); setBackgroundColor(Color.parseColor("#1e293b")) }
        dv.addView(TextView(this).apply { text = "QR-Code scannen"; setTextColor(Color.WHITE); textSize = 18f; setPadding(0, 0, 0, 16); gravity = Gravity.CENTER })
        dv.addView(ImageView(this).apply { setImageBitmap(qrBitmap) })
        dv.addView(TextView(this).apply { text = "Zuschauer-App -> QR Scan"; setTextColor(Color.parseColor("#94a3b8")); textSize = 14f; gravity = Gravity.CENTER; setPadding(0, 12, 0, 0) })
        AlertDialog.Builder(this).setView(dv).setPositiveButton("OK", null).show()
    }

    private fun generateQRCode(data: String, size: Int): Bitmap {
        val bm = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) bitmap.setPixel(x, y, if (bm[x, y]) Color.BLACK else Color.WHITE)
        return bitmap
    }

    private fun updateBattery() {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        i?.let {
            val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            batteryLevel = if (s > 0) (l * 100 / s) else 0
            runOnUiThread { findViewById<TextView>(R.id.batteryText).text = "Batterie: $batteryLevel%" }
            if (socket?.connected() == true && cameraId.isNotEmpty())
                socket?.emit("camera:battery", JSONObject().apply { put("cameraId", cameraId); put("level", batteryLevel) })
        }
    }

    private fun stop() { socket?.emit("camera:leave", cameraId); socket?.disconnect(); finish() }
    override fun onDestroy() { super.onDestroy(); socket?.disconnect(); cameraExecutor.shutdown() }
}
