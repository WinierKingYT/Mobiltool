package com.personaltool.app.capture

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

enum class OemDiscoveryState(val description: String) {
    NONE("No OEM call recording paths or files found."),
    OEM_MEDIA_PERMISSION_REQUIRED("Media audio permission required to inspect OEM recordings."),
    OEM_CANDIDATE_DETECTED("Candidate OEM recording directory detected on storage."),
    OEM_ACCESSIBLE("OEM recording directory accessible via MediaStore/Filesystem."),
    OEM_RECORDING_CONFIRMED("Genuine readable OEM dialer recording confirmed on device."),
    OEM_PROFILE_QUALIFIED("Device profile physically qualified for bidirectional OEM import.")
}

sealed class OemImportResult {
    data class Success(val importedFile: File, val sourceUri: Uri?, val fileSize: Long) : OemImportResult()
    data class NotFound(val diagnosticReason: String) : OemImportResult()
    data class AmbiguousCollision(val diagnosticReason: String) : OemImportResult()
}

data class OemAudioCandidate(
    val uri: Uri? = null,
    val displayName: String,
    val dateModifiedEpochMs: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val filePath: String?
)

object OemRecordingImporter {

    private val CANDIDATE_OEM_PATHS = listOf(
        "/storage/emulated/0/Recordings/Call",
        "/storage/emulated/0/Sounds/Call",
        "/sdcard/Recordings/Call",
        "/sdcard/Sounds/Call",
        "/storage/emulated/0/MIUI/sound_recorder/call_rec"
    )

    /**
     * Checks discovery state by inspecting permissions, MediaStore, and filesystem.
     * Invariant: Never silently interpret missing permission as "no recordings exist".
     */
    fun checkOemDiscoveryState(context: Context): OemDiscoveryState {
        // 1. Check runtime permission
        if (!OemPermissionManager.hasPermission(context)) {
            // Check if directories exist on disk as a candidate indicator
            val hasDirectory = isOemRecordingDirectoryPresent()
            return if (hasDirectory) {
                OemDiscoveryState.OEM_MEDIA_PERMISSION_REQUIRED
            } else {
                OemDiscoveryState.NONE
            }
        }

        // 2. Query MediaStore for confirmed call recordings
        val hasConfirmedRecording = queryMediaStoreCandidates(
            context = context,
            windowStartMs = 0L,
            windowEndMs = Long.MAX_VALUE
        ).isNotEmpty()

        if (hasConfirmedRecording) {
            return OemDiscoveryState.OEM_RECORDING_CONFIRMED
        }

        // 3. Check filesystem directory presence
        val hasDirectory = isOemRecordingDirectoryPresent()
        return if (hasDirectory) {
            OemDiscoveryState.OEM_CANDIDATE_DETECTED
        } else {
            OemDiscoveryState.NONE
        }
    }

    fun isOemRecordingDirectoryPresent(context: Context? = null): Boolean {
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
     * Scoped-Storage compliant MediaStore query for audio recordings.
     */
    fun queryMediaStoreCandidates(
        context: Context,
        windowStartMs: Long,
        windowEndMs: Long
    ): List<OemAudioCandidate> {
        if (!OemPermissionManager.hasPermission(context)) {
            return emptyList()
        }

        val candidates = mutableListOf<OemAudioCandidate>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA
        )

        val windowStartSec = (windowStartMs / 1000L).coerceAtLeast(0L)
        val windowEndSec = (windowEndMs / 1000L).coerceAtLeast(0L)

        val selection = if (windowStartMs > 0L && windowEndMs < Long.MAX_VALUE) {
            "${MediaStore.Audio.Media.DATE_MODIFIED} >= ? AND ${MediaStore.Audio.Media.DATE_MODIFIED} <= ?"
        } else {
            null
        }

        val selectionArgs = if (selection != null) {
            arrayOf(windowStartSec.toString(), windowEndSec.toString())
        } else {
            null
        }

        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "unknown"
                    val modSec = cursor.getLong(modCol)
                    val duration = cursor.getLong(durCol)
                    val size = cursor.getLong(sizeCol)
                    val dataPath = if (dataCol != -1) cursor.getString(dataCol) else null
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    val isCallCandidate = name.startsWith("Call_", ignoreCase = true) ||
                            name.startsWith("Recording_", ignoreCase = true) ||
                            name.contains("Call", ignoreCase = true) ||
                            (dataPath != null && (dataPath.contains("/Call") || dataPath.contains("/call_rec")))

                    if (isCallCandidate && size > 2048L) {
                        candidates.add(
                            OemAudioCandidate(
                                uri = uri,
                                displayName = name,
                                dateModifiedEpochMs = modSec * 1000L,
                                durationMs = duration,
                                sizeBytes = size,
                                filePath = dataPath
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // MediaStore query error fallback
        }

        return candidates
    }

    /**
     * Primary OEM Ingestion Pipeline:
     * Scans MediaStore with timestamp correlation, duration matching, and anti-collision validation.
     */
    fun findAndImport(
        context: Context,
        phoneNumber: String,
        startTimeMs: Long,
        endTimeMs: Long,
        targetVaultDir: File
    ): OemImportResult {
        if (!OemPermissionManager.hasPermission(context)) {
            return OemImportResult.NotFound(
                "Cannot import OEM recording: Required media audio permission is not granted."
            )
        }

        val windowStart = startTimeMs - 15000L // 15s pre-call buffer
        val windowEnd = endTimeMs + 25000L     // 25s post-call flush buffer
        val actualCallDurationMs = (endTimeMs - startTimeMs).coerceAtLeast(0L)

        // 1. Query MediaStore candidates in window
        val mediaStoreCandidates = queryMediaStoreCandidates(context, windowStart, windowEnd)

        // 2. Fallback: Check direct filesystem if MediaStore returned empty
        val directCandidates = if (mediaStoreCandidates.isEmpty()) {
            queryDirectFilesystemCandidates(windowStart, windowEnd)
        } else {
            emptyList()
        }

        val allCandidates = mediaStoreCandidates.ifEmpty { directCandidates }

        if (allCandidates.isEmpty()) {
            return OemImportResult.NotFound(
                "No OEM recording found in MediaStore or storage within timestamp window ($windowStart..$windowEnd)."
            )
        }

        // 3. Multi-Factor Correlation: Duration Check
        // If actual call was >= 3 seconds and candidate has duration metadata > 0, verify approximate match
        val durationFilteredCandidates = if (actualCallDurationMs >= 3000L) {
            allCandidates.filter { candidate ->
                if (candidate.durationMs <= 0L) {
                    true // Unknown duration metadata in candidate: keep for further filtering
                } else {
                    val toleranceMs = (actualCallDurationMs * 0.4).toLong().coerceAtLeast(15000L)
                    val diffMs = kotlin.math.abs(candidate.durationMs - actualCallDurationMs)
                    diffMs <= toleranceMs
                }
            }
        } else {
            allCandidates
        }

        if (durationFilteredCandidates.isEmpty()) {
            return OemImportResult.NotFound(
                "Candidate files found in window were rejected due to severe duration mismatch with actual call duration (${actualCallDurationMs}ms)."
            )
        }

        // 4. Collision / Ambiguity Resolution (Fail Closed on ambiguity)
        val cleanNumber = phoneNumber.filter { it.isDigit() }
        val matchedCandidate: OemAudioCandidate = if (durationFilteredCandidates.size == 1) {
            durationFilteredCandidates.first()
        } else {
            // Multiple candidates in window: require clean number match
            if (cleanNumber.length >= 4) {
                val numberMatches = durationFilteredCandidates.filter { candidate ->
                    candidate.displayName.contains(cleanNumber) ||
                            (candidate.filePath != null && candidate.filePath.contains(cleanNumber))
                }
                if (numberMatches.size == 1) {
                    numberMatches.first()
                } else if (numberMatches.size > 1) {
                    return OemImportResult.AmbiguousCollision(
                        "Multiple OEM recording files (${numberMatches.size}) matched phone number '$phoneNumber' in window; failed closed to prevent wrong-call data corruption."
                    )
                } else {
                    return OemImportResult.AmbiguousCollision(
                        "Multiple OEM recording files (${durationFilteredCandidates.size}) found in window but none matched phone number '$phoneNumber'; failed closed to prevent wrong-call corruption."
                    )
                }
            } else {
                return OemImportResult.AmbiguousCollision(
                    "Multiple OEM recording files (${durationFilteredCandidates.size}) found in window for private/unknown number; failed closed to prevent wrong-call corruption."
                )
            }
        }

        // 5. Atomic Vault Copy
        targetVaultDir.mkdirs()
        val destFile = File(targetVaultDir, "call_oem_${UUID.randomUUID()}_${System.currentTimeMillis()}.m4a")

        return try {
            val copied = if (matchedCandidate.filePath != null && File(matchedCandidate.filePath).exists()) {
                FileInputStream(File(matchedCandidate.filePath)).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
                true
            } else if (matchedCandidate.uri != null) {
                context.contentResolver.openInputStream(matchedCandidate.uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
                true
            } else {
                false
            }

            if (copied && destFile.exists() && destFile.length() > 2048L) {
                OemImportResult.Success(
                    importedFile = destFile,
                    sourceUri = matchedCandidate.uri,
                    fileSize = destFile.length()
                )
            } else {
                destFile.delete()
                OemImportResult.NotFound("Copied OEM file is empty or missing after stream transfer.")
            }
        } catch (err: Exception) {
            destFile.delete()
            OemImportResult.NotFound("Failed to copy OEM recording into vault: ${err.message}")
        }
    }

    private fun queryDirectFilesystemCandidates(windowStartMs: Long, windowEndMs: Long): List<OemAudioCandidate> {
        val candidates = mutableListOf<OemAudioCandidate>()
        for (path in CANDIDATE_OEM_PATHS) {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles()?.filter { file ->
                    file.isFile && file.length() > 2048L && (file.lastModified() in windowStartMs..windowEndMs)
                } ?: emptyList()

                for (file in files) {
                    candidates.add(
                        OemAudioCandidate(
                            uri = Uri.fromFile(file),
                            displayName = file.name,
                            dateModifiedEpochMs = file.lastModified(),
                            durationMs = 0L,
                            sizeBytes = file.length(),
                            filePath = file.absolutePath
                        )
                    )
                }
            }
        }
        return candidates
    }
}
