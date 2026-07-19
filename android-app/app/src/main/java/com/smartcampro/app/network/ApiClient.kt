package com.smartcampro.app.network

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class ApiClient(private var baseUrl: String) {

    interface ApiCallback {
        fun onSuccess(response: String)
        fun onError(error: String)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun login(username: String, password: String, callback: ApiCallback) {
        val json = """{"username":"$username","password":"$password"}"""
        post("$baseUrl/api/auth/login", json, callback)
    }

    fun register(username: String, email: String, password: String, callback: ApiCallback) {
        val json = """{"username":"$username","email":"$email","password":"$password"}"""
        post("$baseUrl/api/auth/register", json, callback)
    }

    fun getMe(token: String, callback: ApiCallback) {
        get("$baseUrl/api/auth/me", token, callback)
    }

    fun listCameras(token: String, callback: ApiCallback) {
        get("$baseUrl/api/cameras/", token, callback)
    }

    fun registerCamera(token: String, cameraName: String, callback: ApiCallback) {
        val json = """{"name":"$cameraName","location":"Android Device"}"""
        postWithAuth("$baseUrl/api/cameras/", token, json, callback)
    }

    fun updateCameraStatus(token: String, cameraId: String, status: String, battery: Int, wifi: Int, callback: ApiCallback) {
        val json = """{"status":"$status","battery":$battery,"wifi_signal":$wifi}"""
        putWithAuth("$baseUrl/api/cameras/$cameraId", token, json, callback)
    }

    fun sendMotionEvent(token: String, cameraId: String, type: String, confidence: Float, callback: ApiCallback) {
        val json = """{"type":"$type","confidence":"$confidence","description":"Motion detected"}"""
        postWithAuth("$baseUrl/api/events/", token, json, callback)
    }

    fun getSubscriptionPlans(callback: ApiCallback) {
        get("$baseUrl/api/auth/subscription/plans", "", callback)
    }

    fun upgradeSubscription(token: String, plan: String, callback: ApiCallback) {
        val json = """{"plan":"$plan"}"""
        postWithAuth("$baseUrl/api/auth/subscription/upgrade", token, json, callback)
    }

    // HTTP methods
    private fun post(url: String, json: String, callback: ApiCallback) {
        val body = json.toRequestBody(JSON)
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e.message ?: "Verbindungsfehler")
            }
            override fun onResponse(call: Call, response: Response) {
                val resp = response.body?.string() ?: ""
                if (response.isSuccessful) callback.onSuccess(resp)
                else callback.onError("HTTP ${response.code}: $resp")
            }
        })
    }

    private fun get(url: String, token: String, callback: ApiCallback) {
        val builder = Request.Builder().url(url).get()
        if (token.isNotEmpty()) builder.addHeader("Authorization", "Bearer $token")
        client.newCall(builder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e.message ?: "Verbindungsfehler")
            }
            override fun onResponse(call: Call, response: Response) {
                val resp = response.body?.string() ?: ""
                if (response.isSuccessful) callback.onSuccess(resp)
                else callback.onError("HTTP ${response.code}: $resp")
            }
        })
    }

    private fun postWithAuth(url: String, token: String, json: String, callback: ApiCallback) {
        val body = json.toRequestBody(JSON)
        val request = Request.Builder().url(url).post(body)
            .addHeader("Authorization", "Bearer $token")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e.message ?: "Verbindungsfehler")
            }
            override fun onResponse(call: Call, response: Response) {
                val resp = response.body?.string() ?: ""
                if (response.isSuccessful) callback.onSuccess(resp)
                else callback.onError("HTTP ${response.code}: $resp")
            }
        })
    }

    private fun putWithAuth(url: String, token: String, json: String, callback: ApiCallback) {
        val body = json.toRequestBody(JSON)
        val request = Request.Builder().url(url).put(body)
            .addHeader("Authorization", "Bearer $token")
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e.message ?: "Verbindungsfehler")
            }
            override fun onResponse(call: Call, response: Response) {
                val resp = response.body?.string() ?: ""
                if (response.isSuccessful) callback.onSuccess(resp)
                else callback.onError("HTTP ${response.code}: $resp")
            }
        })
    }
}
