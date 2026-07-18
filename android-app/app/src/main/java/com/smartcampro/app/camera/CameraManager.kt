package com.smartcampro.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.media.Image
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

    fun setFrameCallback(callback: (Bitmap) -> Unit) {
        this.frameCallback = callback
    }

    fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
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

    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
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
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        camera = null
    }

    fun shutdown() {
        stopCamera()
        analysisExecutor.shutdown()
    }
}
