package com.smartcampro.app.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.*

data class RegisterRequest(val email: String, val password: String, @SerializedName("display_name") val displayName: String? = null)
data class LoginRequest(val email: String, val password: String)
data class RefreshRequest(@SerializedName("refresh_token") val refreshToken: String)
data class AuthResponse(val user: UserResponse, @SerializedName("access_token") val accessToken: String, @SerializedName("refresh_token") val refreshToken: String)
data class UserResponse(val id: String, val email: String, @SerializedName("display_name") val displayName: String?, @SerializedName("subscription_tier") val subscriptionTier: String?, @SerializedName("created_at") val createdAt: String?)
data class CameraCreate(val name: String, @SerializedName("device_model") val deviceModel: String? = "Android")
data class CameraResponse(val id: String, @SerializedName("owner_id") val ownerId: String, val name: String, @SerializedName("device_model") val deviceModel: String?, val status: String?, @SerializedName("last_seen") val lastSeen: String?, @SerializedName("pairing_code") val pairingCode: String?, @SerializedName("created_at") val createdAt: String?)
data class CameraUpdate(val name: String? = null, val status: String? = null)
data class PairRequest(@SerializedName("pairing_code") val pairingCode: String)
data class EventResponse(val id: String, @SerializedName("camera_id") val cameraId: String, val type: String, @SerializedName("thumbnail_url") val thumbnailUrl: String?, @SerializedName("created_at") val createdAt: String?, @SerializedName("camera_name") val cameraName: String?)
data class EventCreate(@SerializedName("camera_id") val cameraId: String, val type: String, @SerializedName("thumbnail_url") val thumbnailUrl: String? = null)

interface ApiService {
    @POST("api/auth/register") fun register(@Body request: RegisterRequest): Call<AuthResponse>
    @POST("api/auth/login") fun login(@Body request: LoginRequest): Call<AuthResponse>
    @POST("api/auth/refresh") fun refreshToken(@Body request: RefreshRequest): Call<AuthResponse>
    @GET("api/auth/me") fun getMe(@Header("Authorization") token: String): Call<UserResponse>
    @GET("api/cameras/") fun listCameras(@Header("Authorization") token: String): Call<List<CameraResponse>>
    @POST("api/cameras/") fun createCamera(@Header("Authorization") token: String, @Body camera: CameraCreate): Call<CameraResponse>
    @GET("api/cameras/{id}") fun getCamera(@Header("Authorization") token: String, @Path("id") id: String): Call<CameraResponse>
    @PUT("api/cameras/{id}") fun updateCamera(@Header("Authorization") token: String, @Path("id") id: String, @Body update: CameraUpdate): Call<CameraResponse>
    @DELETE("api/cameras/{id}") fun deleteCamera(@Header("Authorization") token: String, @Path("id") id: String): Call<Unit>
    @POST("api/cameras/pair") fun pairCamera(@Header("Authorization") token: String, @Body request: PairRequest): Call<CameraResponse>
    @GET("api/events/") fun listEvents(@Header("Authorization") token: String): Call<List<EventResponse>>
    @POST("api/events/") fun createEvent(@Header("Authorization") token: String, @Body event: EventCreate): Call<EventResponse>
}
