package com.smartcampro.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.smartcampro.app.R
import com.smartcampro.app.network.ApiClient
import com.smartcampro.app.utils.Constants
import com.smartcampro.app.utils.UserManager
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var userManager: UserManager
    private var isCloudMode = true
    private var cloudApiUrl = "https://smartcampro-api.onrender.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userManager = UserManager(this)

        if (!userManager.hasUsers()) {
            userManager.createUser("admin", "Admin123!", "admin")
        }

        if (userManager.isLoggedIn()) {
            startMainActivity()
            return
        }

        setContentView(R.layout.activity_login)

        val loginUsername = findViewById<EditText>(R.id.loginUsername)
        val loginPassword = findViewById<EditText>(R.id.loginPassword)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val loginError = findViewById<TextView>(R.id.loginError)
        val skipLoginButton = findViewById<Button>(R.id.skipLoginButton)

        loginButton.setOnClickListener {
            val username = loginUsername.text.toString().trim()
            val password = loginPassword.text.toString()

            if (username.isEmpty() || password.isEmpty()) {
                loginError.text = "Benutzername und Passwort eingeben"
                return@setOnClickListener
            }

            loginButton.isEnabled = false
            loginButton.text = "Anmelden..."
            loginError.text = ""

            // Versuche Cloud-Login zuerst
            val cloudApi = ApiClient(cloudApiUrl)
            cloudApi.login(username, password, object : ApiClient.ApiCallback {
                override fun onSuccess(response: String) {
                    try {
                        val json = JSONObject(response)
                        val token = json.getString("access_token")
                        val user = json.getJSONObject("user")

                        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit().apply {
                            putString(Constants.PREF_AUTH_TOKEN, token)
                            putString(Constants.PREF_LOGGED_IN_USER, user.getString("username"))
                            putString(Constants.PREF_USER_SUBSCRIPTION, user.getString("subscription"))
                            putBoolean(Constants.PREF_CLOUD_MODE, true)
                            putString(Constants.PREF_SERVER_URL, cloudApiUrl)
                            apply()
                        }

                        userManager.setLoggedInUser(user.getString("username"))
                        runOnUiThread {
                            Toast.makeText(this@LoginActivity, "Cloud-Anmeldung erfolgreich!", Toast.LENGTH_SHORT).show()
                            startMainActivity()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            loginError.text = "Cloud-Anmeldung fehlgeschlagen"
                            loginButton.isEnabled = true
                            loginButton.text = "☁️ Mit Cloud anmelden"
                        }
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        loginError.text = "Cloud nicht erreichbar. Lokale Anmeldung..."
                        loginButton.isEnabled = true
                        loginButton.text = "☁️ Mit Cloud anmelden"
                    }
                    // Fallback: lokale Anmeldung
                    localLogin(username, password)
                }
            })
        }

        skipLoginButton.setOnClickListener {
            userManager.setLoggedInUser("admin")
            getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit().apply {
                putBoolean(Constants.PREF_CLOUD_MODE, false)
                apply()
            }
            startMainActivity()
        }
    }

    private fun localLogin(username: String, password: String) {
        if (userManager.validateUser(username, password)) {
            userManager.setLoggedInUser(username)
            getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit().apply {
                putBoolean(Constants.PREF_CLOUD_MODE, false)
                apply()
            }
            startMainActivity()
        }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
