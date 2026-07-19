package com.smartcampro.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.smartcampro.app.R
import com.smartcampro.app.utils.Constants
import com.smartcampro.app.utils.UserManager

class HomeActivity : AppCompatActivity() {

    private lateinit var userManager: UserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        userManager = UserManager(this)

        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val username = userManager.getLoggedInUser() ?: "Gast"
        val userInfoText = findViewById<TextView>(R.id.userInfoText)
        userInfoText.text = "👤 $username"

        findViewById<Button>(R.id.startCameraButton).setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java).apply {
                putExtra("token", "standalone")
                putExtra("camera_name", "Kamera $username")
                putExtra("camera_id", "")
                putExtra("server_url", prefs.getString("cloud_server", "") ?: "")
            }
            startActivity(intent)
        }

        findViewById<Button>(R.id.startViewerButton).setOnClickListener {
            startActivity(Intent(this, ViewerActivity::class.java))
        }

        findViewById<Button>(R.id.logoutButton).setOnClickListener {
            userManager.logout()
            prefs.edit().clear().apply()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
