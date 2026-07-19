package com.smartcampro.app.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.smartcampro.app.R
import com.smartcampro.app.recording.RecordingManager
import com.smartcampro.app.utils.StorageManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class GalleryActivity : AppCompatActivity() {

    private lateinit var recordingManager: RecordingManager
    private lateinit var storageManager: StorageManager
    private lateinit var recordingsList: LinearLayout
    private lateinit var screenshotsList: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var tabRecordings: Button
    private lateinit var tabScreenshots: Button

    private var showingRecordings = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        recordingManager = RecordingManager(this)
        storageManager = StorageManager(this)

        val backButton = findViewById<Button>(R.id.backButton)
        val deleteAllButton = findViewById<Button>(R.id.deleteAllButton)
        tabRecordings = findViewById(R.id.tabRecordings)
        tabScreenshots = findViewById(R.id.tabScreenshots)
        recordingsList = findViewById(R.id.recordingsList)
        screenshotsList = findViewById(R.id.screenshotsList)
        emptyText = findViewById(R.id.emptyText)

        backButton.setOnClickListener { finish() }
        deleteAllButton.setOnClickListener { confirmDeleteAll() }
        tabRecordings.setOnClickListener { showTab(true) }
        tabScreenshots.setOnClickListener { showTab(false) }

        loadRecordings()
    }

    private fun showTab(recordings: Boolean) {
        showingRecordings = recordings
        tabRecordings.backgroundTintList = if (recordings) getColorStateList(android.R.color.holo_purple) else getColorStateList(R.color.default_button_bg)
        tabRecordings.setTextColor(if (recordings) Color.WHITE else Color.parseColor("#94a3b8"))
        tabScreenshots.backgroundTintList = if (!recordings) getColorStateList(android.R.color.holo_purple) else getColorStateList(R.color.default_button_bg)
        tabScreenshots.setTextColor(if (!recordings) Color.WHITE else Color.parseColor("#94a3b8"))

        if (recordings) loadRecordings() else loadScreenshots()
    }

    private fun loadRecordings() {
        recordingsList.removeAllViews()
        val recordings = recordingManager.getAllRecordings()

        if (recordings.isEmpty()) {
            recordingsList.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
            emptyText.text = "Noch keine Aufnahmen"
            return
        }

        recordingsList.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        recordingsList.addView(createHeader("Speicher: ${storageManager.formatSize(storageManager.getStorageInfo().recordingsSize)}"))

        recordings.forEach { recording ->
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
            val dateStr = sdf.format(Date(recording.startTime))
            val duration = if (recording.endTime > 0) {
                ((recording.endTime - recording.startTime) / 1000).toInt()
            } else 0

            val card = createCard(
                title = "🎬 ${recording.id.replace("auto_", "").replace("manual_", "")}",
                subtitle = "$dateStr | ${recording.frameCount} Frames | ${duration}s | ${if (recording.isAutomatic) "Auto" else "Manuell"}",
                onDelete = {
                    recordingManager.deleteRecording(recording.id)
                    loadRecordings()
                }
            )
            recordingsList.addView(card)
        }
    }

    private fun loadScreenshots() {
        screenshotsList.removeAllViews()
        val dir = File(filesDir, "screenshots")
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (files.isEmpty()) {
            screenshotsList.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
            emptyText.text = "Noch keine Screenshots"
            return
        }

        screenshotsList.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        screenshotsList.addView(createHeader("Speicher: ${storageManager.formatSize(storageManager.getStorageInfo().screenshotsSize)}"))

        files.forEach { file ->
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
            val dateStr = sdf.format(Date(file.lastModified()))
            val sizeStr = storageManager.formatSize(file.length())

            val card = createCard(
                title = "📸 ${file.name}",
                subtitle = "$dateStr | $sizeStr",
                onDelete = {
                    file.delete()
                    loadScreenshots()
                }
            )
            screenshotsList.addView(card)
        }
    }

    private fun createHeader(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#64748b"))
            textSize = 12f
            setPadding(8, 8, 8, 16)
        }
    }

    private fun createCard(title: String, subtitle: String, onDelete: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
            setBackgroundColor(Color.parseColor("#1e293b"))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 8
            layoutParams = params

            addView(TextView(this@GalleryActivity).apply {
                this.text = title
                setTextColor(Color.WHITE)
                textSize = 14f
            })
            addView(TextView(this@GalleryActivity).apply {
                this.text = subtitle
                setTextColor(Color.parseColor("#94a3b8"))
                textSize = 12f
                setPadding(0, 4, 0, 0)
            })

            // Delete button row
            addView(LinearLayout(this@GalleryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 0)
                addView(Button(this@GalleryActivity).apply {
                    text = "🗑️ Löschen"
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#ef4444"))
                    textSize = 11f
                    minimumWidth = 0
                    minimumHeight = 0
                    setPadding(24, 8, 24, 8)
                    setOnClickListener {
                        AlertDialog.Builder(this@GalleryActivity)
                            .setTitle("Löschen")
                            .setMessage("Eintrag löschen?")
                            .setPositiveButton("Ja") { _, _ -> onDelete() }
                            .setNegativeButton("Abbrechen", null)
                            .show()
                    }
                })
            })
        }
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Alles löschen")
            .setMessage("Alle Aufnahmen und Screenshots unwiderruflich löschen?")
            .setPositiveButton("Ja, alles löschen") { _, _ ->
                storageManager.deleteAllData()
                loadRecordings()
                Toast.makeText(this, "Alle Daten gelöscht", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }
}
