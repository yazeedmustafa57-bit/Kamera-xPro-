package com.smartcampro.app.network

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebRTCCient(
    private val baseUrl: String,
    private val cameraId: String,
    private val token: String
) {
    private var webSocket: WebSocket? = null
    private var listener: WebRTCListener? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    interface WebRTCListener {
        fun onConnected()
        fun onDisconnected()
        fun onOffer(sdp: String, peerId: String)
        fun onAnswer(sdp: String, peerId: String)
        fun onIceCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int, peerId: String)
        fun onCameraOffline()
        fun onError(error: String)
    }

    fun connect(listener: WebRTCListener) {
        this.listener = listener
        val wsUrl = baseUrl.replace("http", "ws") +
            "/ws/webrtc/camera/$cameraId?token=$token"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onConnected()
                sendHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    when (msg.optString("type")) {
                        "offer" -> listener.onOffer(
                            msg.getString("sdp"),
                            msg.optString("peer_id", "")
                        )
                        "answer" -> listener.onAnswer(
                            msg.getString("sdp"),
                            msg.optString("peer_id", "")
                        )
                        "ice_candidate" -> {
                            val candidate = msg.getJSONObject("candidate")
                            listener.onIceCandidate(
                                candidate.getString("candidate"),
                                candidate.optString("sdpMid", ""),
                                candidate.optInt("sdpMLineIndex", 0),
                                msg.optString("peer_id", "")
                            )
                        }
                        "camera_offline" -> listener.onCameraOffline()
                        "heartbeat_ack" -> {
                            // Schedule next heartbeat
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                sendHeartbeat()
                            }, 15000)
                        }
                    }
                } catch (e: Exception) {
                    listener.onError("Parse error: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                listener.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onError(t.message ?: "Connection failed")
                listener.onDisconnected()
            }
        })
    }

    fun sendOffer(sdp: String, targetPeerId: String) {
        val json = JSONObject().apply {
            put("type", "offer")
            put("sdp", sdp)
            put("sdp_type", "offer")
            put("target_peer_id", targetPeerId)
        }
        webSocket?.send(json.toString())
    }

    fun sendAnswer(sdp: String, targetPeerId: String) {
        val json = JSONObject().apply {
            put("type", "answer")
            put("sdp", sdp)
            put("sdp_type", "answer")
            put("target_peer_id", targetPeerId)
        }
        webSocket?.send(json.toString())
    }

    fun sendIceCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int, targetPeerId: String) {
        val json = JSONObject().apply {
            put("type", "ice_candidate")
            put("candidate", JSONObject().apply {
                put("candidate", candidate)
                put("sdpMid", sdpMid)
                put("sdpMLineIndex", sdpMLineIndex)
            })
            put("target_peer_id", targetPeerId)
        }
        webSocket?.send(json.toString())
    }

    private fun sendHeartbeat() {
        val json = JSONObject().apply { put("type", "heartbeat") }
        webSocket?.send(json.toString())
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
    }
}
