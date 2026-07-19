package com.smartcampro.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class TokenStorage(context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val sharedPreferences = EncryptedSharedPreferences.create(
        "smartcam_secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveTokens(accessToken: String, refreshToken: String) {
        sharedPreferences.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .apply()
    }

    fun getAccessToken(): String? = sharedPreferences.getString("access_token", null)
    fun getRefreshToken(): String? = sharedPreferences.getString("refresh_token", null)

    fun saveUser(id: String, email: String, displayName: String?) {
        sharedPreferences.edit()
            .putString("user_id", id)
            .putString("user_email", email)
            .putString("user_display_name", displayName)
            .apply()
    }

    fun getUserId(): String? = sharedPreferences.getString("user_id", null)
    fun getUserEmail(): String? = sharedPreferences.getString("user_email", null)
    fun getDisplayName(): String? = sharedPreferences.getString("user_display_name", null)

    fun isLoggedIn(): Boolean = getAccessToken() != null

    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
}
