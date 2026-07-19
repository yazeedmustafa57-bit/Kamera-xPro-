package com.smartcampro.app.data.api
import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.*

data class RegisterRequest(val email: String, val password: String, @SerializedName("display_name") val displayName: String? = null)
data class LoginRequest(val email: String, val password: String)
data class AuthResponse(val user: UserResponse, val accessToken: String, val refreshToken: String)
data class UserResponse(val id: String, val email: String, @SerializedName("display_name") val displayName: String?, @SerializedName("subscription_tier") val subscriptionTier: String?)
data class CameraCreate(val name: String, @SerializedName("device_model") val deviceModel: String? = "Android")
data class CameraResponse(val id: String, @SerializedName("owner_id") val ownerId: String, val name: String, val status: String?, @SerializedName("pairing_code") val pairingCode: String?, @SerializedName("device_model") val deviceModel: String? = null, @SerializedName("created_at") val createdAt: String?)

interface ApiService {
    @POST("api/auth/register") fun register(@Body request: RegisterRequest): Call<AuthResponse>
    @POST("api/auth/login") fun login(@Body request: LoginRequest): Call<AuthResponse>
    @GET("api/cameras/") fun listCameras(@Header("Authorization") token: String): Call<List<CameraResponse>>
    @POST("api/cameras/") fun createCamera(@Header("Authorization") token: String, @Body camera: CameraCreate): Call<CameraResponse>
    @GET("api/cameras/{id}") fun getCamera(@Header("Authorization") token: String, @Path("id") id: String): Call<CameraResponse>
}
