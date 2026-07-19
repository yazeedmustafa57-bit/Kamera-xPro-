package com.smartcampro.app.ui

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.smartcampro.app.R
import com.smartcampro.app.utils.Constants
import com.smartcampro.app.utils.StorageManager
import com.smartcampro.app.utils.UserManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var storageManager: StorageManager
    private lateinit var userManager: UserManager
    private lateinit var storageInfoText: TextView
    private lateinit var userListText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        storageManager = StorageManager(this)
        userManager = UserManager(this)

        val backButton = findViewById<Button>(R.id.backButton)
        val sensitivitySeek = findViewById<SeekBar>(R.id.sensitivitySeek)
        val sensitivityLabel = findViewById<TextView>(R.id.sensitivityLabel)
        val nightModeSwitch = findViewById<Switch>(R.id.nightModeSwitch)
        val autoAlarmNightSwitch = findViewById<Switch>(R.id.autoAlarmNightSwitch)
        val autoFlashNightSwitch = findViewById<Switch>(R.id.autoFlashNightSwitch)
        val alarmOnMotionSwitch = findViewById<Switch>(R.id.alarmOnMotionSwitch)
        val volumeSeek = findViewById<SeekBar>(R.id.volumeSeek)
        val volumeLabel = findViewById<TextView>(R.id.volumeLabel)
        val newUsername = findViewById<EditText>(R.id.newUsername)
        val newPassword = findViewById<EditText>(R.id.newPassword)
        val addUserButton = findViewById<Button>(R.id.addUserButton)
        userListText = findViewById(R.id.userListText)
        storageInfoText = findViewById(R.id.storageInfoText)
        val clearScreenshotsBtn = findViewById<Button>(R.id.clearScreenshotsBtn)
        val clearRecordingsBtn = findViewById<Button>(R.id.clearRecordingsBtn)
        val clearAllDataBtn = findViewById<Button>(R.id.clearAllDataBtn)

        backButton.setOnClickListener { finish() }

        // Load saved settings
        sensitivitySeek.progress = prefs.getInt("sensitivity", 50)
        sensitivityLabel.text = "${sensitivitySeek.progress}%"
        nightModeSwitch.isChecked = prefs.getBoolean("night_mode", true)
        autoAlarmNightSwitch.isChecked = prefs.getBoolean("auto_alarm_night", true)
        autoFlashNightSwitch.isChecked = prefs.getBoolean("auto_flash_night", true)
        alarmOnMotionSwitch.isChecked = prefs.getBoolean("alarm_on_motion", false)
        volumeSeek.progress = prefs.getInt("alarm_volume", 70)
        volumeLabel.text = "${volumeSeek.progress}%"

        // Sensitivity
        sensitivitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sensitivityLabel.text = "${progress.coerceIn(5, 100)}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val value = seekBar?.progress?.coerceIn(5, 100) ?: 50
                prefs.edit().putInt("sensitivity", value).apply()
            }
        })

        // Night mode
        nightModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("night_mode", isChecked).apply()
        }

        // Auto alarm night
        autoAlarmNightSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_alarm_night", isChecked).apply()
        }

        // Auto flash night
        autoFlashNightSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_flash_night", isChecked).apply()
        }

        // Alarm on motion
        alarmOnMotionSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("alarm_on_motion", isChecked).apply()
        }

        // Volume
        volumeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                volumeLabel.text = "${progress.coerceIn(0, 100)}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val value = seekBar?.progress?.coerceIn(0, 100) ?: 70
                prefs.edit().putInt("alarm_volume", value).apply()
            }
        })

        // Add user
        addUserButton.setOnClickListener {
            val username = newUsername.text.toString().trim()
            val password = newPassword.text.toString().trim()
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Benutzername und Passwort eingeben", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 4) {
                Toast.makeText(this, "Passwort min. 4 Zeichen", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (userManager.createUser(username, password)) {
                Toast.makeText(this, "Benutzer '$username' erstellt", Toast.LENGTH_SHORT).show()
                newUsername.text.clear()
                newPassword.text.clear()
                updateUserInfo()
            } else {
                Toast.makeText(this, "Benutzer existiert bereits", Toast.LENGTH_SHORT).show()
            }
        }

        // Storage
        clearScreenshotsBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Screenshots löschen?")
                .setPositiveButton("Ja") { _, _ ->
                    storageManager.deleteAllScreenshots()
                    updateStorageInfo()
                    Toast.makeText(this, "Screenshots gelöscht", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        clearRecordingsBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Aufnahmen löschen?")
                .setPositiveButton("Ja") { _, _ ->
                    storageManager.deleteAllRecordings()
                    updateStorageInfo()
                    Toast.makeText(this, "Aufnahmen gelöscht", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        clearAllDataBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("⚠️ Alles löschen?")
                .setMessage("Alle Aufnahmen, Screenshots und Ereignisse löschen?")
                .setPositiveButton("Ja, alles") { _, _ ->
                    storageManager.deleteAllData()
                    updateStorageInfo()
                    Toast.makeText(this, "Alle Daten gelöscht", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        updateStorageInfo()
        updateUserInfo()
    }

    private fun updateStorageInfo() {
        val info = storageManager.getStorageInfo()
        storageInfoText.text = "📸 ${info.screenshotCount} Screenshots (${storageManager.formatSize(info.screenshotsSize)})\n" +
                "🎬 ${info.recordingCount} Aufnahmen, ${info.totalFrames} Frames (${storageManager.formatSize(info.recordingsSize)})\n" +
                "💾 Gesamt: ${storageManager.formatSize(info.totalSize)}"
    }

    private fun updateUserInfo() {
        val currentUser = prefs.getString("username", "admin") ?: "admin"
        val role = userManager.getUserRole(currentUser)
        userListText.text = "👤 Angemeldet: $currentUser ($role)"
    }
}
