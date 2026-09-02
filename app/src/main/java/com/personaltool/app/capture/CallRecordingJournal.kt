package com.personaltool.app.capture

import android.content.Context
import com.personaltool.app.PersonalToolApplication
import com.personaltool.core.model.call.CallCaptureTier
import com.personaltool.core.model.call.CallDirection
import com.personaltool.core.model.call.CallSession
import com.personaltool.core.model.call.RecordingQuality
import com.personaltool.core.storage.entity.CallEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

data class InFlightCallJournalEntry(
    val callId: String,
    val phoneNumber: String,
    val direction: CallDirection,
    val captureTier: CallCaptureTier,
    val startTimeEpochMs: Long,
    val tempAudioPath: String
) {
    fun toSerializedString(): String {
        return listOf(
            callId,
            phoneNumber,
            direction.name,
            captureTier.name,
            startTimeEpochMs.toString(),
            tempAudioPath
        ).joinToString("\n")
    }

    companion object {
        fun fromSerializedString(data: String): InFlightCallJournalEntry? {
            return runCatching {
                val lines = data.lines().filter { it.isNotBlank() }
                if (lines.size >= 6) {
                    InFlightCallJournalEntry(
                        callId = lines[0],
                        phoneNumber = lines[1],
                        direction = CallDirection.valueOf(lines[2]),
                        captureTier = CallCaptureTier.valueOf(lines[3]),
                        startTimeEpochMs = lines[4].toLong(),
                        tempAudioPath = lines[5]
                    )
                } else null
            }.getOrNull()
        }
    }
}

object CallRecordingJournal {

    private const val JOURNAL_FILE_NAME = "in_flight_call_journal.txt"

    private fun getJournalFile(context: Context): File {
        val dir = File(context.filesDir, "calls").apply { mkdirs() }
        return File(dir, JOURNAL_FILE_NAME)
    }

    @Synchronized
    fun recordStart(context: Context, entry: InFlightCallJournalEntry) {
        runCatching {
            getJournalFile(context).writeText(entry.toSerializedString())
        }
    }

    @Synchronized
    fun recordEnd(context: Context) {
        runCatching {
            val file = getJournalFile(context)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    @Synchronized
    fun recoverPendingSessions(context: Context) {
        val file = getJournalFile(context)
        if (!file.exists()) return

        val text = runCatching { file.readText() }.getOrNull() ?: return
        val entry = InFlightCallJournalEntry.fromSerializedString(text) ?: run {
            file.delete()
            return
        }

        val audioFile = File(entry.tempAudioPath)
        val now = System.currentTimeMillis()
        val durationMs = (now - entry.startTimeEpochMs).coerceAtLeast(0L)

        val app = context.applicationContext as? PersonalToolApplication
        val dao = app?.database?.callDao()

        if (audioFile.exists() && audioFile.length() >= AudioFileInspector.MIN_VALID_FILE_SIZE_BYTES) {
            val inspection = AudioFileInspector.inspectRecordedFile(
                filePath = audioFile.absolutePath,
                defaultQuality = if (entry.captureTier == CallCaptureTier.PRIVILEGED_DIRECT || entry.captureTier == CallCaptureTier.OEM_IMPORT) {
                    RecordingQuality.VERIFIED_BIDIRECTIONAL
                } else {
                    RecordingQuality.MIXED_UNVERIFIED
                }
            )

            val recoveredSession = CallSession(
                id = entry.callId,
                phoneNumber = entry.phoneNumber,
                direction = entry.direction,
                startTimeEpochMs = entry.startTimeEpochMs,
                endTimeEpochMs = now,
                durationMs = if (inspection.isValid) inspection.durationMs else durationMs,
                audioFilePath = audioFile.absolutePath,
                fileSizeBytes = audioFile.length(),
                recordingQuality = inspection.determinedQuality,
                captureTier = entry.captureTier,
                unrecordedReason = if (!inspection.isValid) "Process crash recovery: ${inspection.rejectionReason}" else null
            )

            if (dao != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    dao.insertCall(CallEntity.fromDomain(recoveredSession))
                }
            }
        } else {
            // Audio file missing or empty after crash
            val failedSession = CallSession(
                id = entry.callId,
                phoneNumber = entry.phoneNumber,
                direction = entry.direction,
                startTimeEpochMs = entry.startTimeEpochMs,
                endTimeEpochMs = now,
                durationMs = 0L,
                recordingQuality = RecordingQuality.CORRUPT,
                captureTier = entry.captureTier,
                unrecordedReason = "Process crash recovery: Audio file was not flushed to disk."
            )

            if (dao != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    dao.insertCall(CallEntity.fromDomain(failedSession))
                }
            }
        }

        // Clear journal
        file.delete()
    }
}
