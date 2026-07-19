package com.smartcampro.app.alarm

import android.content.Context
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import kotlin.math.PI
import kotlin.math.sin

class AlarmManager(private val context: Context) {

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var alarmVolume = 70 // 0-100
    private var vibrationEnabled = true
    private var screenFlashEnabled = true

    var onAlarmStateChanged: ((Boolean) -> Unit)? = null

    fun setVolume(volume: Int) {
        alarmVolume = volume.coerceIn(0, 100)
    }

    fun setVibration(enabled: Boolean) {
        vibrationEnabled = enabled
    }

    fun setScreenFlash(enabled: Boolean) {
        screenFlashEnabled = enabled
    }

    fun startAlarm(durationMs: Long = 5000) {
        if (isPlaying) return
        isPlaying = true
        onAlarmStateChanged?.invoke(true)

        // Sirene starten
        Thread {
            try {
                playSiren(durationMs)
            } catch (e: Exception) {
                Log.e("AlarmManager", "Siren error", e)
            }
        }.start()

        // Vibration starten
        if (vibrationEnabled) {
            startVibration()
        }

        // Bildschirm-Blinken
        if (screenFlashEnabled) {
            startScreenFlash()
        }

        // Auto-Stop nach Dauer
        Thread {
            try { Thread.sleep(durationMs) } catch (_: InterruptedException) {}
            stopAlarm()
        }.start()
    }

    fun stopAlarm() {
        isPlaying = false
        onAlarmStateChanged?.invoke(false)
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e("AlarmManager", "Stop audio error", e)
        }
        stopVibration()
    }

    fun isAlarmActive(): Boolean = isPlaying

    private fun playSiren(durationMs: Long) {
        val sampleRate = 22050
        val duration = durationMs / 1000.0
        val numSamples = (sampleRate * duration).toInt()
        val samples = ShortArray(numSamples)

        val amplitude = (Short.MAX_VALUE * alarmVolume / 100).toShort()

        // Sirene: aufsteigende und absteigende Frequenz
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val cycle = t % 2.0  // 2-second cycle
            val freq: Double = if (cycle < 1.0) {
                // Aufsteigend: 400Hz -> 1200Hz
                400.0 + 800.0 * cycle
            } else {
                // Absteigend: 1200Hz -> 400Hz
                1200.0 - 800.0 * (cycle - 1.0)
            }
            val value = sin(2.0 * PI * freq * t) * amplitude
            samples[i] = value.toInt().toShort()
        }

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(bufferSize, numSamples * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack?.write(samples, 0, numSamples)
        audioTrack?.play()
    }

    private fun startVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 200, 100, 200, 100, 200),
                    intArrayOf(0, 255, 0, 255, 0, 255),
                    0
                )
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                @Suppress("DEPRECATION")
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0, 200, 100, 200, 100, 200),
                    intArrayOf(0, 255, 0, 255, 0, 255),
                    0
                )
                vibrator.vibrate(effect)
            }
        } catch (e: Exception) {
            Log.e("AlarmManager", "Vibration error", e)
        }
    }

    private fun stopVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.cancel()
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.cancel()
            }
        } catch (e: Exception) {
            Log.e("AlarmManager", "Vibration stop error", e)
        }
    }

    private fun startScreenFlash() {
        try {
            val window = (context as? android.app.Activity)?.window ?: return
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Exception) {}
    }
}
