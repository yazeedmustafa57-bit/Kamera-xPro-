package com.smartcampro.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.smartcampro.app.R
import com.smartcampro.app.data.local.TokenStorage

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        val ts = TokenStorage(this)
        findViewById<TextView>(R.id.userInfoText).text = "Eingeloggt: ${ts.getDisplayName() ?: ts.getUserEmail()}"
        findViewById<Button>(R.id.startCameraButton).setOnClickListener { startActivity(Intent(this, CameraActivity::class.java)) }
        findViewById<Button>(R.id.startViewerButton).setOnClickListener { startActivity(Intent(this, ViewerActivity::class.java)) }
        findViewById<Button>(R.id.logoutButton).setOnClickListener { ts.clearAll(); startActivity(Intent(this, LoginActivity::class.java)); finish() }
    }
}
