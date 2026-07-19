package com.smartcampro.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager as Camera2Manager
import android.util.Base64
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private var analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var frameCallback: ((Bitmap) -> Unit)? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    // Torch / LED
    private var isTorchOn = false
    var torchListener: ((Boolean) -> Unit)? = null

    fun setFrameCallback(callback: (Bitmap) -> Unit) {
        this.frameCallback = callback
    }

    fun isTorchEnabled(): Boolean = isTorchOn

    fun toggleTorch() {
        isTorchOn = !isTorchOn
        try {
            camera?.cameraInfo?.let { info ->
                val hasFlash = info.torchState?.value != null
                if (hasFlash) {
                    camera?.cameraControl?.enableTorch(isTorchOn)
                }
            }
        } catch (e: Exception) {
            Log.e("CameraManager", "Torch toggle failed", e)
            isTorchOn = !isTorchOn
        }
        torchListener?.invoke(isTorchOn)
    }

    fun setTorch(on: Boolean) {
        if (isTorchOn == on) return
        isTorchOn = on
        try {
            camera?.cameraControl?.enableTorch(isTorchOn)
        } catch (e: Exception) {
            Log.e("CameraManager", "Torch set failed", e)
            isTorchOn = !on
        }
        torchListener?.invoke(isTorchOn)
    }

    fun flashTorch(times: Int = 3, intervalMs: Long = 200) {
        Thread {
            for (i in 0 until times) {
                setTorch(true)
                try { Thread.sleep(intervalMs) } catch (_: InterruptedException) { break }
                setTorch(false)
                if (i < times - 1) {
                    try { Thread.sleep(intervalMs) } catch (_: InterruptedException) { break }
                }
            }
        }.start()
    }

    fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        // Torch bei Frontkamera ausschalten
        if (lensFacing == CameraSelector.LENS_FACING_FRONT && isTorchOn) {
            setTorch(false)
        }
    }

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onReady: () -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(lifecycleOwner, previewView)
            onReady()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val provider = cameraProvider ?: return

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    processFrame(imageProxy)
                    imageProxy.close()
                }
            }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalysis
            )
        } catch (e: Exception) {
            Log.e("CameraManager", "Camera binding failed", e)
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            bitmap?.let { frameCallback?.invoke(it) }
        } catch (e: Exception) {
            Log.e("CameraManager", "Frame processing error", e)
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val nv21 = yuv420888ToNv21(imageProxy)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 80, out)
            val bytes = out.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e("CameraManager", "YUV conversion failed", e)
            null
        }
    }

    private fun yuv420888ToNv21(imageProxy: ImageProxy): ByteArray {
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

        return nv21
    }

    fun takeScreenshot(callback: (Bitmap?) -> Unit) {
        val imageCapture = imageCapture ?: run {
            callback(null)
            return
        }

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val bitmap = imageProxyToBitmap(imageProxy)
                    imageProxy.close()
                    callback(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraManager", "Screenshot failed", exception)
                    callback(null)
                }
            }
        )
    }

    fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    fun stopCamera() {
        // Torch aus bei Stop
        if (isTorchOn) {
            try { camera?.cameraControl?.enableTorch(false) } catch (_: Exception) {}
            isTorchOn = false
        }
        cameraProvider?.unbindAll()
        camera = null
    }

    fun shutdown() {
        stopCamera()
        analysisExecutor.shutdown()
    }
}
