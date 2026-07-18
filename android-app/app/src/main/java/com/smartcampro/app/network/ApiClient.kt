package com.smartcampro.app.network

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiClient(private val baseUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    interface ApiCallback {
        fun onSuccess(response: String)
        fun onError(error: String)
    }

    fun login(username: String, password: String, callback: ApiCallback) {
        val json = JSONObject().apply {
            put("username", username)
            put("password", password)
        }
        postJson("/api/auth/login", json, callback)
    }

    fun registerCamera(token: String, cameraName: String, callback: ApiCallback) {
        val json = JSONObject().apply {
            put("name", cameraName)
        }
        postJson("/api/cameras/", json, token, callback)
    }

    fun listCameras(token: String, callback: ApiCallback) {
        get("/api/cameras/", token, callback)
    }

    fun updateCameraStatus(
        token: String,
        cameraId: String,
        status: String,
        battery: Int,
        wifiSignal: Int,
        callback: ApiCallback
    ) {
        val json = JSONObject().apply {
            put("status", status)
            put("battery", battery)
            put("wifi_signal", wifiSignal)
        }
        putJson("/api/cameras/$cameraId", json, token, callback)
    }

    fun sendMotionEvent(
        token: String,
        cameraId: String,
        type: String,
        confidence: Float,
        callback: ApiCallback
    ) {
        val json = JSONObject().apply {
            put("type", type)
            put("confidence", confidence.toString())
        }
        postJson("/api/events/create?camera_id=$cameraId&event_type=$type&confidence=$confidence", json, token, callback)
    }

    private fun get(path: String, token: String?, callback: ApiCallback) {
        val requestBuilder = Request.Builder()
            .url("$baseUrl$path")
            .get()
        token?.let { requestBuilder.header("Authorization", "Bearer $it") }

        client.newCall(requestBuilder.build()).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        callback.onSuccess(responseBody)
                    } else {
                        val errorMsg = try {
                            val errBody = JSONObject(responseBody ?: "{}")
                            errBody.optString("detail", "HTTP ${response.code}")
                        } catch (e: Exception) {
                            "HTTP ${response.code}: ${response.message}"
                        }
                        callback.onError(errorMsg)
                    }
                } catch (e: Exception) {
                    callback.onError(e.message ?: "Unknown error")
                }
            }
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e.message ?: "Connection failed")
            }
        })
    }

    private fun postJson(path: String, json: JSONObject, callback: ApiCallback) {
        postJson(path, json, null, callback)
    }

    private fun postJson(path: String, json: JSONObject, token: String?, callback: ApiCallback) {
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder()
            .url("$baseUrl$path")
            .post(body)
        token?.let { requestBuilder.header("Authorization", "Bearer $it") }

        client.newCall(requestBuilder.build()).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        callback.onSuccess(responseBody)
                    } else {
                        val errorMsg = try {
                            val errBody = JSONObject(responseBody ?: "{}")
                            errBody.optString("detail", "HTTP ${response.code}")
                        } catch (e: Exception) {
                            "HTTP ${response.code}: ${response.message}"
                        }
                        callback.onError(errorMsg)
                    }
                } catch (e: Exception) {
                    callback.onError(e.message ?: "Unknown error")
                }
            }
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e.message ?: "Connection failed")
            }
        })
    }

    private fun putJson(path: String, json: JSONObject, token: String?, callback: ApiCallback) {
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder()
            .url("$baseUrl$path")
            .put(body)
        token?.let { requestBuilder.header("Authorization", "Bearer $it") }

        client.newCall(requestBuilder.build()).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        callback.onSuccess(responseBody)
                    } else {
                        callback.onError("HTTP ${response.code}")
                    }
                } catch (e: Exception) {
                    callback.onError(e.message ?: "Unknown error")
                }
            }
            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e.message ?: "Connection failed")
            }
        })
    }
}
