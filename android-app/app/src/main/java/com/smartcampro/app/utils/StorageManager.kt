package com.smartcampro.app.utils

import android.content.Context
import java.io.File

class StorageManager(private val context: Context) {

    data class StorageInfo(
        val screenshotsSize: Long,
        val recordingsSize: Long,
        val totalSize: Long,
        val screenshotCount: Int,
        val recordingCount: Int,
        val totalFrames: Int
    )

    fun getStorageInfo(): StorageInfo {
        val screenshotsDir = File(context.filesDir, "screenshots")
        val recordingsDir = File(context.filesDir, "recordings")

        val screenshotsSize = if (screenshotsDir.exists()) {
            screenshotsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else 0L

        val recordingsSize = if (recordingsDir.exists()) {
            recordingsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else 0L

        val screenshotCount = if (screenshotsDir.exists()) {
            screenshotsDir.listFiles()?.size ?: 0
        } else 0

        val recordingCount = if (recordingsDir.exists()) {
            recordingsDir.listFiles()?.size ?: 0
        } else 0

        val totalFrames = if (recordingsDir.exists()) {
            recordingsDir.listFiles()?.sumOf { folder ->
                folder.listFiles()?.count { it.name.startsWith("frame_") } ?: 0
            } ?: 0
        } else 0

        return StorageInfo(
            screenshotsSize = screenshotsSize,
            recordingsSize = recordingsSize,
            totalSize = screenshotsSize + recordingsSize,
            screenshotCount = screenshotCount,
            recordingCount = recordingCount,
            totalFrames = totalFrames
        )
    }

    fun deleteAllScreenshots(): Boolean {
        val dir = File(context.filesDir, "screenshots")
        return if (dir.exists()) dir.deleteRecursively() else true
    }

    fun deleteAllRecordings(): Boolean {
        val dir = File(context.filesDir, "recordings")
        return if (dir.exists()) dir.deleteRecursively() else true
    }

    fun deleteAllData(): Boolean {
        deleteAllScreenshots()
        deleteAllRecordings()
        val eventsFile = File(context.filesDir, "events.json")
        if (eventsFile.exists()) eventsFile.delete()
        return true
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }
}
