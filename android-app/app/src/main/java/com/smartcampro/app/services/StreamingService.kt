package com.smartcampro.app.services

import android.app.*
import android.content.Intent
import android.os.BatteryManager
import android.os.IBinder
import android.net.wifi.WifiManager
import androidx.core.app.NotificationCompat
import com.smartcampro.app.R
import com.smartcampro.app.ui.MainActivity

class StreamingService : Service() {

    companion object {
        const val CHANNEL_ID = "SmartCamChannel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Camera is streaming...")
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun updateNotification(status: String) {
        val notification = buildNotification(status)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    fun getWifiSignal(): Int {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val info = wifiManager.connectionInfo
        val level = WifiManager.calculateSignalLevel(info.rssi, 5)
        return (level * 100) / 4
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SmartCam Streaming",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Camera streaming notification"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SmartCam Pro")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
