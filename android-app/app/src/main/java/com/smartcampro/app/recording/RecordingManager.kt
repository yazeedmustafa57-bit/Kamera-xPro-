package com.smartcampro.app.recording

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class RecordingManager(private val context: Context) {

    data class Recording(
        val id: String,
        val folder: File,
        val startTime: Long,
        var endTime: Long = 0,
        var frameCount: Int = 0,
        var isAutomatic: Boolean = false
    )

    private var currentRecording: Recording? = null
    private val baseDir = File(context.filesDir, "recordings")

    var onRecordingStateChanged: ((Boolean) -> Unit)? = null
    var onRecordingSaved: ((Recording) -> Unit)? = null

    fun startRecording(isAutomatic: Boolean = false): String {
        if (currentRecording != null) stopRecording()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val id = if (isAutomatic) "auto_$timestamp" else "manual_$timestamp"
        val folder = File(baseDir, id).also { it.mkdirs() }

        currentRecording = Recording(
            id = id,
            folder = folder,
            startTime = System.currentTimeMillis(),
            isAutomatic = isAutomatic
        )

        onRecordingStateChanged?.invoke(true)
        Log.d("RecordingManager", "Recording started: $id")
        return id
    }

    fun addFrame(bitmap: Bitmap) {
        val recording = currentRecording ?: return
        try {
            val frameNum = String.format("%04d", recording.frameCount)
            val file = File(recording.folder, "frame_$frameNum.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            recording.frameCount++
        } catch (e: Exception) {
            Log.e("RecordingManager", "Frame save error", e)
        }
    }

    fun stopRecording(): Recording? {
        val recording = currentRecording ?: return null
        recording.endTime = System.currentTimeMillis()
        currentRecording = null

        onRecordingStateChanged?.invoke(false)
        onRecordingSaved?.invoke(recording)

        Log.d("RecordingManager", "Recording stopped: ${recording.id}, ${recording.frameCount} frames")
        return recording
    }

    fun isRecording(): Boolean = currentRecording != null

    fun getAllRecordings(): List<Recording> {
        val recordings = mutableListOf<Recording>()
        if (baseDir.exists()) {
            baseDir.listFiles()?.sortedByDescending { it.lastModified() }?.forEach { folder ->
                val frameCount = folder.listFiles()?.count { it.name.startsWith("frame_") } ?: 0
                if (frameCount > 0) {
                    recordings.add(
                        Recording(
                            id = folder.name,
                            folder = folder,
                            startTime = folder.lastModified(),
                            frameCount = frameCount
                        )
                    )
                }
            }
        }
        return recordings
    }

    fun deleteRecording(id: String): Boolean {
        val folder = File(baseDir, id)
        return if (folder.exists()) {
            folder.deleteRecursively()
        } else false
    }

    fun cleanupOldRecordings(maxCount: Int = 50) {
        val recordings = getAllRecordings()
        if (recordings.size > maxCount) {
            recordings.drop(maxCount).forEach { deleteRecording(it.id) }
        }
    }

    fun getTotalStorageSize(): Long {
        var totalSize = 0L
        if (baseDir.exists()) {
            baseDir.walkTopDown().filter { it.isFile }.forEach { totalSize += it.length() }
        }
        return totalSize
    }

    fun getRecordingFrames(recording: Recording): List<File> {
        return recording.folder.listFiles()
            ?.filter { it.name.startsWith("frame_") }
            ?.sortedBy { it.name }
            ?: emptyList()
    }
}
