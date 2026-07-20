package com.smartcampro.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.smartcampro.app.R
import com.smartcampro.app.data.api.RetrofitClient
import com.smartcampro.app.data.local.TokenStorage
import android.widget.EditText

class HomeActivity : AppCompatActivity() {
    private lateinit var ts: TokenStorage
    private lateinit var serverStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        ts = TokenStorage(this)

        // Always set base URL from stored value
        val savedUrl = ts.getServerUrl()
        if (!savedUrl.isNullOrEmpty()) {
            RetrofitClient.setBaseUrl(savedUrl)
        }

        serverStatusText = findViewById(R.id.serverStatusText)
        updateServerStatus()

        val displayName = ts.getDisplayName() ?: ts.getUserEmail() ?: "Gast"
        findViewById<TextView>(R.id.userInfoText).text = "Eingeloggt: $displayName"

        findViewById<Button>(R.id.startCameraButton).setOnClickListener {
            ensureServer { startCamera() }
        }

        findViewById<Button>(R.id.startViewerButton).setOnClickListener {
            ensureServer { startViewer() }
        }

        findViewById<Button>(R.id.serverButton).setOnClickListener { showServerDialog() }
        findViewById<Button>(R.id.logoutButton).setOnClickListener {
            ts.clearAll()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun updateServerStatus() {
        val url = ts.getServerUrl()
        if (url.isNullOrEmpty()) {
            serverStatusText.text = "Kein Server konfiguriert"
            serverStatusText.setTextColor(getColor(android.R.color.holo_orange_light))
        } else {
            serverStatusText.text = "Server: $url"
            serverStatusText.setTextColor(getColor(android.R.color.holo_green_light))
        }
    }

    private fun ensureServer(callback: () -> Unit) {
        val url = ts.getServerUrl()
        if (url.isNullOrEmpty()) {
            showServerDialog(callback)
        } else {
            callback()
        }
    }

    private fun showServerDialog(callback: (() -> Unit)? = null) {
        val input = EditText(this).apply {
            hint = "https://ddr-toner-reno-holmes.trycloudflare.com"
            setText(ts.getServerUrl() ?: "")
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Server-URL eingeben")
            .setMessage("Backend-Server URL eingeben.\n\nCloudflare Tunnel oder lokaler Server.")
            .setView(input)
            .setPositiveButton("Speichern") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    ts.saveServerUrl(url)
                    RetrofitClient.setBaseUrl(url)
                    updateServerStatus()
                    Toast.makeText(this, "Server gespeichert!", Toast.LENGTH_SHORT).show()
                    callback?.invoke()
                } else {
                    Toast.makeText(this, "URL eingeben!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun startCamera() {
        startActivity(Intent(this, CameraActivity::class.java))
    }

    private fun startViewer() {
        startActivity(Intent(this, ViewerActivity::class.java))
    }
}
