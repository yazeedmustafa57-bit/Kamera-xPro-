package com.smartcampro.app.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.smartcampro.app.R
import com.smartcampro.app.recording.EventManager
import java.text.SimpleDateFormat
import java.util.*

class EventsActivity : AppCompatActivity() {

    private lateinit var eventManager: EventManager
    private lateinit var eventsList: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var statsMotions: TextView
    private lateinit var statsAlarms: TextView
    private lateinit var statsPhotos: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_events)

        eventManager = EventManager(this)

        val backButton = findViewById<Button>(R.id.backButton)
        val clearAllButton = findViewById<Button>(R.id.clearAllButton)
        eventsList = findViewById(R.id.eventsList)
        emptyText = findViewById(R.id.emptyText)
        statsMotions = findViewById(R.id.statsMotions)
        statsAlarms = findViewById(R.id.statsAlarms)
        statsPhotos = findViewById(R.id.statsPhotos)

        backButton.setOnClickListener { finish() }
        clearAllButton.setOnClickListener { confirmClearAll() }

        loadEvents()
    }

    override fun onResume() {
        super.onResume()
        loadEvents()
    }

    private fun loadEvents() {
        eventsList.removeAllViews()
        val events = eventManager.getEvents()

        if (events.isEmpty()) {
            eventsList.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
            return
        }

        eventsList.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        var motionCount = 0
        var alarmCount = 0
        var photoCount = 0

        events.forEach { event ->
            when (event.type) {
                "motion" -> motionCount++
                "alarm" -> alarmCount++
                "photo" -> photoCount++
            }

            val sdf = SimpleDateFormat("dd.MM.yy HH:mm:ss", Locale.getDefault())
            val dateStr = sdf.format(Date(event.timestamp))

            val icon = when (event.type) {
                "motion" -> "🚶"
                "alarm" -> "🚨"
                "photo" -> "📸"
                "recording" -> "🎬"
                else -> "📌"
            }

            val color = when (event.type) {
                "motion" -> "#f59e0b"
                "alarm" -> "#ef4444"
                "photo" -> "#3b82f6"
                "recording" -> "#22c55e"
                else -> "#94a3b8"
            }

            val borderColor = Color.parseColor(color)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 14, 16, 14)
                setBackgroundColor(Color.parseColor("#1e293b"))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 6
                layoutParams = lp

                // Border left with color
                setOnLongClickListener {
                    AlertDialog.Builder(this@EventsActivity)
                        .setTitle("Ereignis löschen")
                        .setMessage("\"${event.message}\" löschen?")
                        .setPositiveButton("Ja") { _, _ ->
                            eventManager.deleteEvent(event.id)
                            loadEvents()
                        }
                        .setNegativeButton("Abbrechen", null)
                        .show()
                    true
                }

                addView(LinearLayout(this@EventsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(TextView(this@EventsActivity).apply {
                        text = "$icon "
                        textSize = 16f
                    })
                    addView(TextView(this@EventsActivity).apply {
                        text = event.message
                        setTextColor(Color.WHITE)
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                })

                addView(TextView(this@EventsActivity).apply {
                    text = dateStr
                    setTextColor(Color.parseColor("#64748b"))
                    textSize = 11f
                    setPadding(0, 4, 0, 0)
                })
            }
            eventsList.addView(card)
        }

        statsMotions.text = "🚶 $motionCount Bewegungen"
        statsAlarms.text = "🚨 $alarmCount Alarme"
        statsPhotos.text = "📸 $photoCount Fotos"
    }

    private fun confirmClearAll() {
        if (eventManager.getEvents().isEmpty()) {
            Toast.makeText(this, "Keine Ereignisse", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("⚠️ Alle löschen")
            .setMessage("Alle Ereignisse unwiderruflich löschen?")
            .setPositiveButton("Ja") { _, _ ->
                eventManager.clearEvents()
                loadEvents()
                Toast.makeText(this, "Alle Ereignisse gelöscht", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }
}
