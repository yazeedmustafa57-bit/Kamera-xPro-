package com.smartcampro.app.webrtc

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URISyntaxException

class SignalingClient(
    private val serverUrl: String,
    private val token: String,
    private val listener: Listener
) {
    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onOffer(offer: JSONObject, watcherId: String)
        fun onAnswer(answer: JSONObject, watcherId: String)
        fun onIceCandidate(candidate: JSONObject, watcherId: String)
        fun onCameraStatus(cameraId: String, status: String)
        fun onWatcherJoined(watcherId: String)
        fun onWatcherLeft(watcherId: String)
        fun onError(error: String)
    }

    private var socket: Socket? = null

    fun connect() {
        try {
            val options = IO.Options.builder()
                .setAuth(mapOf("token" to token))
                .build()

            socket = IO.socket(serverUrl, options)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d("Signaling", "Connected to server")
                listener.onConnected()
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d("Signaling", "Disconnected from server")
                listener.onDisconnected()
            }

            socket?.on("webrtc:offer") { args ->
                val data = args[0] as JSONObject
                listener.onOffer(data.getJSONObject("offer"), data.getString("watcherId"))
            }

            socket?.on("webrtc:answer") { args ->
                val data = args[0] as JSONObject
                listener.onAnswer(data.getJSONObject("answer"), data.getString("watcherId"))
            }

            socket?.on("webrtc:ice-candidate") { args ->
                val data = args[0] as JSONObject
                listener.onIceCandidate(data.getJSONObject("candidate"), data.optString("watcherId", ""))
            }

            socket?.on("camera:status") { args ->
                val data = args[0] as JSONObject
                listener.onCameraStatus(data.getString("cameraId"), data.getString("status"))
            }

            socket?.on("watcher:joined") { args ->
                val data = args[0] as JSONObject
                listener.onWatcherJoined(data.getString("watcherId"))
            }

            socket?.on("watcher:left") { args ->
                val data = args[0] as JSONObject
                listener.onWatcherLeft(data.getString("watcherId"))
            }

            socket?.on("error") { args ->
                val data = args[0] as JSONObject
                listener.onError(data.getString("message"))
            }

            socket?.connect()
        } catch (e: URISyntaxException) {
            listener.onError("Server-URL ungueltig: ${e.message}")
        }
    }

    fun joinCameraRoom(cameraId: String) {
        socket?.emit("camera:join", cameraId)
    }

    fun joinWatcherRoom(cameraId: String) {
        socket?.emit("watcher:join", cameraId)
    }

    fun sendOffer(offer: JSONObject, cameraId: String) {
        val data = JSONObject().apply {
            put("offer", offer)
            put("cameraId", cameraId)
        }
        socket?.emit("webrtc:offer", data)
    }

    fun sendAnswer(answer: JSONObject, cameraId: String, watcherId: String) {
        val data = JSONObject().apply {
            put("answer", answer)
            put("cameraId", cameraId)
            put("watcherId", watcherId)
        }
        socket?.emit("webrtc:answer", data)
    }

    fun sendIceCandidate(candidate: JSONObject, cameraId: String, targetCamera: Boolean = false, watcherId: String = "") {
        val data = JSONObject().apply {
            put("candidate", candidate)
            put("cameraId", cameraId)
            put("targetCamera", targetCamera)
            put("watcherId", watcherId)
        }
        socket?.emit("webrtc:ice-candidate", data)
    }

    fun sendMotionEvent(cameraId: String, type: String = "motion") {
        val data = JSONObject().apply {
            put("cameraId", cameraId)
            put("type", type)
        }
        socket?.emit("camera:motion", data)
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
    }
}
