package com.smartcampro.app.network

import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketClient(
    private val baseUrl: String,
    private val cameraId: String,
    private val token: String = ""
) {
    private var webSocket: WebSocket? = null
    private var listener: Listener? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onMessage(message: JSONObject)
        fun onError(error: String)
    }

    fun connect(listener: Listener) {
        this.listener = listener
        val wsUrl = buildString {
            append(baseUrl.replace("http", "ws"))
            append("/ws/camera/$cameraId")
            if (token.isNotEmpty()) {
                append("?token=$token")
            }
        }
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onConnected()
                sendStatus("online", 100, 100)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    listener.onMessage(JSONObject(text))
                } catch (e: Exception) {
                    listener.onError("Invalid message: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                listener.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onError(t.message ?: "WebSocket error")
                listener.onDisconnected()
            }
        })
    }

    fun sendStatus(status: String, battery: Int, wifiSignal: Int) {
        val json = JSONObject().apply {
            put("type", "status")
            put("status", status)
            put("battery", battery)
            put("wifi_signal", wifiSignal)
        }
        webSocket?.send(json.toString())
    }

    fun sendMotionDetected(confidence: Float) {
        val json = JSONObject().apply {
            put("type", "motion")
            put("confidence", confidence)
            put("timestamp", System.currentTimeMillis())
        }
        webSocket?.send(json.toString())
    }

    fun sendFrame(base64Frame: String) {
        val json = JSONObject().apply {
            put("type", "frame")
            put("data", base64Frame)
        }
        webSocket?.send(json.toString())
    }

    fun disconnect() {
        webSocket?.close(1000, "Camera disconnecting")
        webSocket = null
    }
}
