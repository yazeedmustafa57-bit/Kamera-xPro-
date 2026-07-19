package com.smartcampro.app.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.smartcampro.app.R
import com.smartcampro.app.network.ApiClient
import com.smartcampro.app.utils.Constants
import com.smartcampro.app.utils.UserManager
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var userManager: UserManager
    private lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userManager = UserManager(this)

        if (!userManager.hasUsers()) {
            userManager.createUser("admin", "Admin123!", "admin")
        }

        if (userManager.isLoggedIn()) {
            startHome()
            return
        }

        setContentView(R.layout.activity_login)
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        apiClient = ApiClient(prefs.getString("cloud_server", "") ?: "")

        val loginUsername = findViewById<EditText>(R.id.loginUsername)
        val loginPassword = findViewById<EditText>(R.id.loginPassword)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val loginError = findViewById<TextView>(R.id.loginError)
        val skipLoginButton = findViewById<Button>(R.id.skipLoginButton)

        loginButton.setOnClickListener {
            val username = loginUsername.text.toString().trim()
            val password = loginPassword.text.toString()

            if (username.isEmpty() || password.isEmpty()) {
                loginError.text = "Bitte ausfuellen"
                return@setOnClickListener
            }

            loginButton.isEnabled = false
            loginButton.text = "Anmelden..."
            loginError.text = ""

            // Cloud-Login
            apiClient.login(username, password, object : ApiClient.ApiCallback {
                override fun onSuccess(response: String) {
                    try {
                        val json = JSONObject(response)
                        val token = json.getString("access_token")
                        val user = json.getJSONObject("user")

                        prefs.edit().apply {
                            putString(Constants.PREF_AUTH_TOKEN, token)
                            putString(Constants.PREF_LOGGED_IN_USER, user.getString("username"))
                            putBoolean(Constants.PREF_CLOUD_MODE, true)
                            apply()
                        }

                        userManager.setLoggedInUser(user.getString("username"))
                        runOnUiThread { startHome() }
                    } catch (e: Exception) {
                        runOnUiThread {
                            loginError.text = "Anmeldung fehlgeschlagen"
                            loginButton.isEnabled = true
                            loginButton.text = "Anmelden"
                        }
                    }
                }

                override fun onError(error: String) {
                    // Fallback: lokal
                    if (userManager.validateUser(username, password)) {
                        userManager.setLoggedInUser(username)
                        prefs.edit().putBoolean(Constants.PREF_CLOUD_MODE, false).apply()
                        runOnUiThread { startHome() }
                    } else {
                        runOnUiThread {
                            loginError.text = "Falsches Passwort"
                            loginButton.isEnabled = true
                            loginButton.text = "Anmelden"
                        }
                    }
                }
            })
        }

        skipLoginButton.setOnClickListener {
            userManager.setLoggedInUser("admin")
            prefs.edit().putBoolean(Constants.PREF_CLOUD_MODE, false).apply()
            startHome()
        }
    }

    private fun startHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
