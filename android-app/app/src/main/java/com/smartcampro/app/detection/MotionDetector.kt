package com.smartcampro.app.detection

import android.graphics.Bitmap
import android.graphics.Color

class MotionDetector {

    private var previousFrame: Bitmap? = null
    private var sensitivity = 0.5f
    private var listener: Listener? = null

    interface Listener {
        fun onMotionDetected(confidence: Float)
    }

    fun setSensitivity(value: Float) {
        sensitivity = value.coerceIn(0.1f, 1.0f)
    }

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun processFrame(frame: Bitmap) {
        val prev = previousFrame
        previousFrame = frame.copy(frame.config, false)

        if (prev == null) return
        if (prev.width != frame.width || prev.height != frame.height) return

        val confidence = compareFrames(prev, frame)

        if (confidence > sensitivity) {
            listener?.onMotionDetected(confidence)
        }
    }

    private fun compareFrames(frame1: Bitmap, frame2: Bitmap): Float {
        val width = frame1.width
        val height = frame1.height
        val pixels1 = IntArray(width * height)
        val pixels2 = IntArray(width * height)

        frame1.getPixels(pixels1, 0, width, 0, 0, width, height)
        frame2.getPixels(pixels2, 0, width, 0, 0, width, height)

        var diffCount = 0
        val totalPixels = width * height
        val threshold = 30

        for (i in 0 until totalPixels) {
            val r1 = Color.red(pixels1[i])
            val g1 = Color.green(pixels1[i])
            val b1 = Color.blue(pixels1[i])

            val r2 = Color.red(pixels2[i])
            val g2 = Color.green(pixels2[i])
            val b2 = Color.blue(pixels2[i])

            val diff = Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2)
            if (diff > threshold) diffCount++
        }

        return diffCount.toFloat() / totalPixels
    }

    fun reset() {
        previousFrame = null
    }
}
