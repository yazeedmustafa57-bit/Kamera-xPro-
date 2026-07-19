package com.smartcampro.app.utils

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

class UserManager(context: Context) {

    data class User(
        val username: String,
        val passwordHash: String,
        val role: String = "admin",
        val createdAt: Long = System.currentTimeMillis()
    )

    private val prefs: SharedPreferences = context.getSharedPreferences("smartcam_users", Context.MODE_PRIVATE)

    fun createUser(username: String, password: String, role: String = "admin"): Boolean {
        if (prefs.contains("user_${username}_hash")) return false
        val hash = hashPassword(password)
        prefs.edit().apply {
            putString("user_${username}_hash", hash)
            putString("user_${username}_role", role)
            putLong("user_${username}_created", System.currentTimeMillis())
            putBoolean("has_users", true)
            apply()
        }
        return true
    }

    fun validateUser(username: String, password: String): Boolean {
        val storedHash = prefs.getString("user_${username}_hash", null) ?: return false
        return hashPassword(password) == storedHash
    }

    fun changePassword(username: String, newPassword: String): Boolean {
        if (!userExists(username)) return false
        prefs.edit().apply {
            putString("user_${username}_hash", hashPassword(newPassword))
            apply()
        }
        return true
    }

    fun deleteUser(username: String): Boolean {
        if (!userExists(username)) return false
        prefs.edit().apply {
            remove("user_${username}_hash")
            remove("user_${username}_role")
            remove("user_${username}_created")
            apply()
        }
        return true
    }

    fun userExists(username: String): Boolean {
        return prefs.contains("user_${username}_hash")
    }

    fun hasUsers(): Boolean {
        return prefs.getBoolean("has_users", false)
    }

    fun getUserRole(username: String): String {
        return prefs.getString("user_${username}_role", "user") ?: "user"
    }

    fun isAdmin(username: String): Boolean {
        return getUserRole(username) == "admin"
    }

    fun getLoggedInUser(): String? {
        return prefs.getString("logged_in_user", null)
    }

    fun setLoggedInUser(username: String) {
        prefs.edit().putString("logged_in_user", username).apply()
    }

    fun logout() {
        prefs.edit().remove("logged_in_user").apply()
    }

    fun isLoggedIn(): Boolean {
        return prefs.getString("logged_in_user", null) != null
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(password.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
