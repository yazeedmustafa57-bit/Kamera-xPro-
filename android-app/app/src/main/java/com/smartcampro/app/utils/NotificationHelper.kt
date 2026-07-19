package com.smartcampro.app.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.smartcampro.app.R

class NotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "smartcam_alerts"
        private const val CHANNEL_NAME = "SmartCam Alarme"
        private const val CHANNEL_DESC = "Bewegungs- und Alarmbenachrichtigungen"
    }

    private var notificationId = 100

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                setShowBadge(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun sendAlert(title: String, message: String, intentClass: Class<*>? = null) {
        val intent = if (intentClass != null) {
            Intent(context, intentClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else null

        val pendingIntent = if (intent != null) {
            PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 100, 300))
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId++, notification)
        } catch (e: SecurityException) {
            // Keine POST_NOTIFICATIONS permission
        }
    }

    fun sendMotionAlert(message: String, intentClass: Class<*>? = null) {
        sendAlert("🚶 Bewegung erkannt", message, intentClass)
    }

    fun sendAlarmAlert(message: String, intentClass: Class<*>? = null) {
        sendAlert("🚨 ALARM", message, intentClass)
    }

    fun sendRecordingAlert(message: String, intentClass: Class<*>? = null) {
        sendAlert("🎬 Aufnahme", message, intentClass)
    }
}
