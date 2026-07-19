package com.smartcampro.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.smartcampro.app.R
import com.smartcampro.app.utils.Constants
import com.smartcampro.app.utils.UserManager

class LoginActivity : AppCompatActivity() {

    private lateinit var userManager: UserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        userManager = UserManager(this)

        // Erstmalig: Default Admin erstellen
        if (!userManager.hasUsers()) {
            userManager.createUser("admin", "Admin123!", "admin")
        }

        // Bereits eingeloggt?
        if (userManager.isLoggedIn()) {
            startMainActivity()
            return
        }

        setContentView(R.layout.activity_login)

        val usernameInput = findViewById<EditText>(R.id.loginUsername)
        val passwordInput = findViewById<EditText>(R.id.loginPassword)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val loginError = findViewById<TextView>(R.id.loginError)
        val skipLoginButton = findViewById<Button>(R.id.skipLoginButton)

        loginButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (username.isEmpty() || password.isEmpty()) {
                loginError.text = "Benutzername und Passwort eingeben"
                return@setOnClickListener
            }

            if (userManager.validateUser(username, password)) {
                userManager.setLoggedInUser(username)
                startMainActivity()
            } else {
                loginError.text = "Falscher Benutzername oder Passwort"
            }
        }

        skipLoginButton.setOnClickListener {
            userManager.setLoggedInUser("admin")
            startMainActivity()
        }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
