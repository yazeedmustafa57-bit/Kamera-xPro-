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

        if (tokenStorage.isLoggedIn()) {
            startHome()
            return
        }

        setContentView(R.layout.activity_login)

        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val displayNameInput = findViewById<EditText>(R.id.displayNameInput)
        val submitButton = findViewById<Button>(R.id.submitButton)
        val tabLogin = findViewById<Button>(R.id.tabLogin)
        val tabRegister = findViewById<Button>(R.id.tabRegister)
        val errorText = findViewById<TextView>(R.id.errorText)
        val skipButton = findViewById<TextView>(R.id.skipButton)

        tabLogin.setOnClickListener { setMode(false) }
        tabRegister.setOnClickListener { setMode(true) }

        submitButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            val displayName = displayNameInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                errorText.text = "Bitte alles ausfuellen"
                return@setOnClickListener
            }

            submitButton.isEnabled = false
            submitButton.text = if (isRegisterMode) "Registrieren..." else "Anmelden..."
            errorText.text = ""

            if (isRegisterMode) {
                register(email, password, displayName, submitButton, errorText)
            } else {
                login(email, password, submitButton, errorText)
            }
        }

        skipButton.setOnClickListener {
            tokenStorage.saveUser("demo", "demo@test.com", "Demo")
            tokenStorage.saveTokens("demo-token", "demo-refresh")
            startHome()
        }
    }

    private fun setMode(register: Boolean) {
        isRegisterMode = register
        findViewById<EditText>(R.id.displayNameInput).visibility = if (register) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.submitButton).text = if (register) "Registrieren" else "Anmelden"
        findViewById<Button>(R.id.tabLogin).setBackgroundResource(if (register) R.drawable.bg_button_dark else R.drawable.bg_button_primary)
        findViewById<Button>(R.id.tabRegister).setBackgroundResource(if (register) R.drawable.bg_button_primary else R.drawable.bg_button_dark)
    }

    private fun login(email: String, password: String, button: Button, errorText: TextView) {
        val api = RetrofitClient.getApi()
        api.login(LoginRequest(email, password)).enqueue(object : Callback<AuthResponse> {
            override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                if (response.isSuccessful) {
                    val auth = response.body()!!
                    tokenStorage.saveTokens(auth.accessToken, auth.refreshToken)
                    tokenStorage.saveUser(auth.user.id, auth.user.email, auth.user.displayName)
                    startHome()
                } else {
                    val error = response.errorBody()?.string() ?: "Login fehlgeschlagen"
                    runOnUiThread {
                        errorText.text = error
                        button.isEnabled = true
                        button.text = "Anmelden"
                    }
                }
            }

            override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                runOnUiThread {
                    errorText.text = "Server nicht erreichbar: ${t.message}"
                    button.isEnabled = true
                    button.text = "Anmelden"
                }
            }
        })
    }

    private fun register(email: String, password: String, displayName: String, button: Button, errorText: TextView) {
        val api = RetrofitClient.getApi()
        api.register(RegisterRequest(email, password, displayName.ifEmpty { null })).enqueue(object : Callback<AuthResponse> {
            override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                if (response.isSuccessful) {
                    val auth = response.body()!!
                    tokenStorage.saveTokens(auth.accessToken, auth.refreshToken)
                    tokenStorage.saveUser(auth.user.id, auth.user.email, auth.user.displayName)
                    startHome()
                } else {
                    val error = response.errorBody()?.string() ?: "Registrierung fehlgeschlagen"
                    runOnUiThread {
                        errorText.text = error
                        button.isEnabled = true
                        button.text = "Registrieren"
                    }
                }
            }

            override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                runOnUiThread {
                    errorText.text = "Server nicht erreichbar: ${t.message}"
                    button.isEnabled = true
                    button.text = "Registrieren"
                }
            }
        })
    }

    private fun startHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
