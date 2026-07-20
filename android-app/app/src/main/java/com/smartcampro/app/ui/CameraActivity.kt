package com.smartcampro.app.ui

import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.ImageReader
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
import java.nio.ByteBuffer
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
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var frameTimer: android.os.Handler? = null
    private var isStreaming = false
    private var imageReader: ImageReader? = null

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

        frameTimer = android.os.Handler(mainLooper)
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
                updateBattery()
            }

            socket?.on("watcher:joined") {
                viewerCount++
                runOnUiThread {
                    findViewById<TextView>(R.id.viewersText).text = "Zuschauer: $viewerCount"
                }
                startStreaming()
            }

            socket?.on("watcher:left") {
                viewerCount = (viewerCount - 1).coerceAtLeast(0)
                runOnUiThread {
                    findViewById<TextView>(R.id.viewersText).text = "Zuschauer: $viewerCount"
                }
                if (viewerCount == 0) stopStreaming()
            }

            socket?.on("remote:flash") { args ->
                try {
                    val data = args[0] as JSONObject
                    val on = data.optBoolean("on", true)
                    runOnUiThread {
                        try {
                            camera?.cameraControl?.enableTorch(on)
                            Toast.makeText(this@CameraActivity,
                                if (on) "Licht AN" else "Licht AUS", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }

            socket?.on("remote:switch") {
                runOnUiThread { switchCamera() }
            }

            socket?.on("remote:alarm") {
                runOnUiThread {
                    triggerAlarmLocal()
                    Toast.makeText(this@CameraActivity, "ALARM empfangen!", Toast.LENGTH_SHORT).show()
                }
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                runOnUiThread {
                    findViewById<TextView>(R.id.statusText).text = "● Getrennt"
                    findViewById<TextView>(R.id.statusText).setTextColor(Color.parseColor("#ef4444"))
                    stopStreaming()
                }
            }

            socket?.connect()
        } catch (e: Exception) { Log.e("Camera", "Socket error", e) }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val previewView = findViewById<PreviewView>(R.id.previewView)
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetResolution(android.util.Size(1280, 720))
            .build()

        val selector = if (useFrontCamera)
            CameraSelector.DEFAULT_FRONT_CAMERA
        else
            CameraSelector.DEFAULT_BACK_CAMERA

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, selector, preview, imageCapture)
        } catch (e: Exception) {
            Log.e("Camera", "Bind failed: ${e.message}")
            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, selector, preview)
            } catch (_: Exception) {}
        }
    }

    private fun startStreaming() {
        if (isStreaming) return
        isStreaming = true
        frameTimer?.postDelayed(frameRunnable, 200)
    }

    private fun stopStreaming() {
        isStreaming = false
        frameTimer?.removeCallbacks(frameRunnable)
    }

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!isStreaming || socket?.connected() != true || viewerCount <= 0) return
            captureAndSendFrame()
            frameTimer?.postDelayed(this, 150) // ~7 FPS
        }
    }

    private fun captureAndSendFrame() {
        val capture = imageCapture ?: return
        try {
            capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    try {
                        val jpegBytes = yuv420ToJpeg(imageProxy)
                        if (jpegBytes != null && jpegBytes.isNotEmpty()) {
                            val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                            socket?.emit("camera:frame", JSONObject().apply {
                                put("cameraId", cameraId)
                                put("frame", b64)
                            })
                        }
                    } catch (e: Exception) {
                        Log.e("Camera", "Frame send error: ${e.message}")
                    } finally {
                        imageProxy.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("Camera", "Capture error: ${exception.message}")
                }
            })
        } catch (e: Exception) {
            Log.e("Camera", "Capture failed: ${e.message}")
        }
    }

    /**
     * Properly convert YUV_420_888 ImageProxy to JPEG byte array.
     * This is the correct way - the previous BitmapFactory approach produced
     * green screens because it tried to decode YUV data as JPEG.
     */
    private fun yuv420ToJpeg(imageProxy: ImageProxy): ByteArray? {
        try {
            val image: Image = imageProxy.image ?: return null
            val width = image.width
            val height = image.height

            val yBuffer: ByteBuffer = image.planes[0].buffer
            val uBuffer: ByteBuffer = image.planes[1].buffer
            val vBuffer: ByteBuffer = image.planes[2].buffer

            val yRowStride = image.planes[0].rowStride
            val uvRowStride = image.planes[1].rowStride
            val uvPixelStride = image.planes[1].pixelStride

            // Build NV21 byte array: Y plane + interleaved V,U planes
            val nv21Size = width * height + (width * height / 2)
            val nv21 = ByteArray(nv21Size)

            // Copy Y plane
            var pos = 0
            if (yRowStride == width) {
                // No padding, direct copy
                yBuffer.position(0)
                yBuffer.get(nv21, 0, width * height)
                pos = width * height
            } else {
                // Row by row copy (handles padding)
                for (row in 0 until height) {
                    yBuffer.position(row * yRowStride)
                    yBuffer.get(nv21, pos, width)
                    pos += width
                }
            }

            // Interleave V and U planes for NV21 format
            for (row in 0 until height / 2) {
                for (col in 0 until width / 2) {
                    val uvIndex = row * uvRowStride + col * uvPixelStride
                    nv21[pos++] = vBuffer.get(uvIndex)
                    nv21[pos++] = uBuffer.get(uvIndex)
                }
            }

            // Convert NV21 to JPEG
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 60, out)
            return out.toByteArray()
        } catch (e: Exception) {
            Log.e("Camera", "YUV conversion error: ${e.message}")
            return null
        }
    }

    private fun switchCamera() {
        useFrontCamera = !useFrontCamera
        bindCamera()
        Toast.makeText(this,
            if (useFrontCamera) "Frontkamera" else "Rückkamera",
            Toast.LENGTH_SHORT).show()
    }

    private fun toggleFlash() {
        try {
            val on = camera?.cameraInfo?.torchState?.value != 1
            camera?.cameraControl?.enableTorch(on)
            Toast.makeText(this,
                if (on) "Licht AN" else "Licht AUS",
                Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun triggerAlarm() {
        triggerAlarmLocal()
        socket?.emit("camera:alarm", JSONObject().apply {
            put("cameraId", cameraId)
            put("message", "ALARM!")
        })
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
        if (cameraId.isEmpty()) return
        val qrData = JSONObject().apply {
            put("server", ts.getServerUrl() ?: "")
            put("camera", cameraId)
            put("token", ts.getAccessToken() ?: "")
        }.toString()
        val qrBitmap = generateQRCode(qrData, 500)
        val dv = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.parseColor("#1e293b"))
        }
        dv.addView(TextView(this).apply {
            text = "QR-Code scannen"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })
        dv.addView(ImageView(this).apply { setImageBitmap(qrBitmap) })
        AlertDialog.Builder(this).setView(dv).setPositiveButton("OK", null).show()
    }

    private fun generateQRCode(data: String, size: Int): Bitmap {
        val bm = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size)
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size)
            for (y in 0 until size)
                b.setPixel(x, y, if (bm[x, y]) Color.BLACK else Color.WHITE)
        return b
    }

    private fun updateBattery() {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        i?.let {
            val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            batteryLevel = if (s > 0) (l * 100 / s) else 0
            runOnUiThread {
                findViewById<TextView>(R.id.batteryText).text = "Batterie: $batteryLevel%"
            }
            if (socket?.connected() == true && cameraId.isNotEmpty()) {
                socket?.emit("camera:battery", JSONObject().apply {
                    put("cameraId", cameraId)
                    put("level", batteryLevel)
                })
            }
        }
    }

    private fun stop() {
        stopStreaming()
        socket?.emit("camera:leave", cameraId)
        socket?.disconnect()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
        socket?.disconnect()
        cameraExecutor.shutdown()
    }
}
