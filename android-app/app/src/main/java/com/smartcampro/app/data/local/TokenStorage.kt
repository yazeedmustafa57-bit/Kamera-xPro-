package com.smartcampro.app.data.local
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class TokenStorage(context: Context) {
    private val key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val prefs = EncryptedSharedPreferences.create("smartcam_secure", key, context, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun saveTokens(at: String, rt: String) { prefs.edit().putString("access_token", at).putString("refresh_token", rt).apply() }
    fun getAccessToken(): String? = prefs.getString("access_token", null)
    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)
    fun saveUser(id: String, email: String, name: String?) { prefs.edit().putString("user_id", id).putString("user_email", email).putString("user_name", name).apply() }
    fun getUserId(): String? = prefs.getString("user_id", null)
    fun getUserEmail(): String? = prefs.getString("user_email", null)
    fun getDisplayName(): String? = prefs.getString("user_name", null)
    fun saveServerUrl(url: String) { prefs.edit().putString("server_url", url).apply() }
    fun getServerUrl(): String? = prefs.getString("server_url", null)
    fun isLoggedIn(): Boolean = getAccessToken() != null
    fun clearAll() { prefs.edit().clear().apply() }
}
