package com.smartcampro.app.services

import android.app.*
import android.content.Intent
import android.os.BatteryManager
import android.os.IBinder
import android.net.wifi.WifiManager
import androidx.core.app.NotificationCompat
import com.smartcampro.app.R
import com.smartcampro.app.network.WebRTCCient
import com.smartcampro.app.ui.MainActivity

class WebRTCStreamingService : Service() {

    companion object {
        const val CHANNEL_ID = "WebRTCChannel"
        const val NOTIFICATION_ID = 2001
    }

    private var webrtcClient: WebRTCCient? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = intent?.getStringExtra("token") ?: return START_NOT_STICKY
        val cameraId = intent?.getStringExtra("camera_id") ?: return START_NOT_STICKY
        val serverUrl = intent?.getStringExtra("server_url") ?: return START_NOT_STICKY

        val notification = buildNotification("WebRTC Streaming...")
        startForeground(NOTIFICATION_ID, notification)

        startWebRTC(serverUrl, cameraId, token)

        return START_STICKY
    }

    private fun startWebRTC(serverUrl: String, cameraId: String, token: String) {
        webrtcClient = WebRTCCient(serverUrl, cameraId, token)
        webrtcClient?.connect(object : WebRTCCient.WebRTCListener {
            override fun onConnected() {
                updateNotification("WebRTC Connected")
            }

            override fun onDisconnected() {
                updateNotification("Disconnected")
            }

            override fun onOffer(sdp: String, peerId: String) {
                // Handle incoming offer from dashboard client
                // In production, use org.webrtc.PeerConnectionFactory
                webrtcClient?.sendAnswer("{\"type\":\"answer\",\"sdp\":\"placeholder\"}", peerId)
            }

            override fun onAnswer(sdp: String, peerId: String) {}

            override fun onIceCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int, peerId: String) {}

            override fun onCameraOffline() {
                updateNotification("Stream ended")
            }

            override fun onError(error: String) {
                updateNotification("Error: $error")
            }
        })
    }

    private fun updateNotification(status: String) {
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
            "WebRTC Streaming",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "WebRTC video streaming notification"
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        webrtcClient?.disconnect()
        super.onDestroy()
    }
}
