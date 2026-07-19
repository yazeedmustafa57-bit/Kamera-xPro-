package com.smartcampro.app.recording

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class EventManager(private val context: Context) {

    data class Event(
        val id: String,
        val type: String,      // "motion", "alarm", "recording", "photo"
        val timestamp: Long,
        val message: String,
        val screenshotPath: String? = null,
        val recordingId: String? = null
    )

    private val eventsFile = File(context.filesDir, "events.json")
    private val events = mutableListOf<Event>()

    var onNewEvent: ((Event) -> Unit)? = null

    init {
        loadEvents()
    }

    fun addEvent(type: String, message: String, screenshotPath: String? = null, recordingId: String? = null) {
        val event = Event(
            id = "evt_${System.currentTimeMillis()}",
            type = type,
            timestamp = System.currentTimeMillis(),
            message = message,
            screenshotPath = screenshotPath,
            recordingId = recordingId
        )
        events.add(0, event)
        saveEvents()
        onNewEvent?.invoke(event)
        Log.d("EventManager", "Event: $type - $message")
    }

    fun getEvents(): List<Event> = events.toList()

    fun getRecentEvents(count: Int): List<Event> = events.take(count)

    fun clearEvents() {
        events.clear()
        saveEvents()
    }

    fun deleteEvent(id: String) {
        events.removeAll { it.id == id }
        saveEvents()
    }

    private fun saveEvents() {
        try {
            val array = JSONArray()
            events.forEach { event ->
                val obj = JSONObject().apply {
                    put("id", event.id)
                    put("type", event.type)
                    put("timestamp", event.timestamp)
                    put("message", event.message)
                    put("screenshotPath", event.screenshotPath ?: "")
                    put("recordingId", event.recordingId ?: "")
                }
                array.put(obj)
            }
            eventsFile.writeText(array.toString(2))
        } catch (e: Exception) {
            Log.e("EventManager", "Save events error", e)
        }
    }

    private fun loadEvents() {
        try {
            if (!eventsFile.exists()) return
            val array = JSONArray(eventsFile.readText())
            events.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                events.add(
                    Event(
                        id = obj.getString("id"),
                        type = obj.getString("type"),
                        timestamp = obj.getLong("timestamp"),
                        message = obj.getString("message"),
                        screenshotPath = obj.optString("screenshotPath", "").ifEmpty { null },
                        recordingId = obj.optString("recordingId", "").ifEmpty { null }
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("EventManager", "Load events error", e)
        }
    }
}
