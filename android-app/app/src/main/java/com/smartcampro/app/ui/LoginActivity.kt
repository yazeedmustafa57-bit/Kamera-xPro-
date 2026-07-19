package com.smartcampro.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.smartcampro.app.R
import com.smartcampro.app.data.api.*
import com.smartcampro.app.data.local.TokenStorage
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {
    private lateinit var tokenStorage: TokenStorage
    private var isRegisterMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenStorage = TokenStorage(this)

        // Set base URL from saved server URL
        val savedUrl = tokenStorage.getServerUrl()
        if (!savedUrl.isNullOrEmpty()) {
            RetrofitClient.setBaseUrl(savedUrl)
        }

        if (tokenStorage.isLoggedIn()) { startHome(); return }
        setContentView(R.layout.activity_login)

        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val displayNameInput = findViewById<EditText>(R.id.displayNameInput)
        val serverUrlInput = findViewById<EditText>(R.id.serverUrlInput)
        val submitButton = findViewById<Button>(R.id.submitButton)
        val errorText = findViewById<TextView>(R.id.errorText)

        // Pre-fill server URL
        serverUrlInput.setText(savedUrl ?: "https://staffing-hundred-tree-try.trycloudflare.com")

        findViewById<Button>(R.id.tabLogin).setOnClickListener {
            isRegisterMode = false
            displayNameInput.visibility = View.GONE
            submitButton.text = "Anmelden"
            errorText.text = ""
        }
        findViewById<Button>(R.id.tabRegister).setOnClickListener {
            isRegisterMode = true
            displayNameInput.visibility = View.VISIBLE
            submitButton.text = "Registrieren"
            errorText.text = ""
        }

        submitButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            val serverUrl = serverUrlInput.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) { errorText.text = "Bitte alles ausfuellen"; return@setOnClickListener }
            if (serverUrl.isEmpty()) { errorText.text = "Server-URL eingeben!"; return@setOnClickListener }

            // Save and set server URL
            tokenStorage.saveServerUrl(serverUrl)
            RetrofitClient.setBaseUrl(serverUrl)

            submitButton.isEnabled = false
            errorText.text = ""
            if (isRegisterMode) {
                val name = displayNameInput.text.toString().trim()
                RetrofitClient.getApi().register(RegisterRequest(email, password, name.ifEmpty { null })).enqueue(object : Callback<AuthResponse> {
                    override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                        if (response.isSuccessful) { saveAndGo(response.body()!!) }
                        else { runOnUiThread { errorText.text = response.errorBody()?.string() ?: "Fehler"; submitButton.isEnabled = true; submitButton.text = "Registrieren" } }
                    }
                    override fun onFailure(call: Call<AuthResponse>, t: Throwable) { runOnUiThread { errorText.text = "Server nicht erreichbar: ${t.message}"; submitButton.isEnabled = true } }
                })
            } else {
                RetrofitClient.getApi().login(LoginRequest(email, password)).enqueue(object : Callback<AuthResponse> {
                    override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                        if (response.isSuccessful) { saveAndGo(response.body()!!) }
                        else { runOnUiThread { errorText.text = "Falsche Anmeldedaten"; submitButton.isEnabled = true; submitButton.text = "Anmelden" } }
                    }
                    override fun onFailure(call: Call<AuthResponse>, t: Throwable) { runOnUiThread { errorText.text = "Server nicht erreichbar: ${t.message}"; submitButton.isEnabled = true } }
                })
            }
        }

        findViewById<TextView>(R.id.skipButton).setOnClickListener {
            tokenStorage.saveUser("demo", "demo@test.com", "Demo")
            tokenStorage.saveTokens("demo-token", "demo-refresh")
            startHome()
        }
    }

    private fun saveAndGo(auth: AuthResponse) {
        tokenStorage.saveTokens(auth.accessToken, auth.refreshToken)
        tokenStorage.saveUser(auth.user.id, auth.user.email, auth.user.displayName)
        startHome()
    }

    private fun startHome() { startActivity(Intent(this, HomeActivity::class.java)); finish() }
}
