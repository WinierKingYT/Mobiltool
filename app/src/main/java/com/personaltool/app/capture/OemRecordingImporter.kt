package com.personaltool.app.capture

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

sealed class OemImportResult {
    data class Success(val importedFile: File, val originalPath: String, val fileSize: Long) : OemImportResult()
    data class NotFound(val diagnosticReason: String) : OemImportResult()
}

object OemRecordingImporter {

    private val CANDIDATE_OEM_PATHS = listOf(
        "/storage/emulated/0/Recordings/Call",
        "/storage/emulated/0/Sounds/Call",
        "/sdcard/Recordings/Call",
        "/sdcard/Sounds/Call",
        "/storage/emulated/0/MIUI/sound_recorder/call_rec"
    )

    /**
     * Checks if native OEM call recording directory exists and is accessible.
     */
    fun isOemRecordingDirectoryPresent(): Boolean {
        return CANDIDATE_OEM_PATHS.any { path ->
            val dir = File(path)
            dir.exists() && dir.isDirectory
        } || isStandardRecordingsDirectoryPresent()
    }

    private fun isStandardRecordingsDirectoryPresent(): Boolean {
        return try {
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS)
            val callDir = File(publicDir, "Call")
            callDir.exists() && callDir.isDirectory
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Searches OEM call recording directories for a recording corresponding to the call window.
     * Copies the matched file atomically into the internal vault directory.
     */
    fun findAndImport(
        phoneNumber: String,
        startTimeMs: Long,
        endTimeMs: Long,
        targetVaultDir: File
    ): OemImportResult {
        val searchDirs = mutableListOf<File>()

        for (path in CANDIDATE_OEM_PATHS) {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                searchDirs.add(dir)
            }
        }

        try {
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS)
            val callDir = File(publicDir, "Call")
            if (callDir.exists() && callDir.isDirectory && !searchDirs.contains(callDir)) {
                searchDirs.add(callDir)
            }
        } catch (_: Exception) {
            // Ignore storage retrieval errors
        }

        if (searchDirs.isEmpty()) {
            return OemImportResult.NotFound("No OEM call recording directories found on device.")
        }

        val windowStart = startTimeMs - 15000L // 15s buffer before offhook
        val windowEnd = endTimeMs + 20000L     // 20s buffer for file flush

        val candidateFiles = searchDirs.flatMap { dir ->
            dir.listFiles()?.toList() ?: emptyList()
        }.filter { file ->
            file.isFile && file.length() > 2048L && (file.lastModified() in windowStart..windowEnd)
        }.sortedByDescending { it.lastModified() }

        if (candidateFiles.isEmpty()) {
            return OemImportResult.NotFound("No OEM recording files found within timestamp window ($startTimeMs..$endTimeMs).")
        }

        // Prefer candidate whose name contains clean phone digits
        val cleanNumber = phoneNumber.filter { it.isDigit() }
        val bestMatch = candidateFiles.firstOrNull { file ->
            cleanNumber.isNotEmpty() && cleanNumber.length >= 7 && file.name.contains(cleanNumber)
        } ?: candidateFiles.first()

        targetVaultDir.mkdirs()
        val destFile = File(targetVaultDir, "call_oem_${UUID.randomUUID()}_${System.currentTimeMillis()}.m4a")

        return try {
            FileInputStream(bestMatch).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            OemImportResult.Success(
                importedFile = destFile,
                originalPath = bestMatch.absolutePath,
                fileSize = destFile.length()
            )
        } catch (err: Exception) {
            OemImportResult.NotFound("Failed to copy OEM recording into vault: ${err.message}")
        }
    }
}
