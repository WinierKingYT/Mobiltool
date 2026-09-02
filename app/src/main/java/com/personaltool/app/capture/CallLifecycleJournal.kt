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
import java.io.File

enum class PersistedCallState {
    OFFHOOK_ACTIVE,
    ENDED_IMPORT_PENDING,
    COMPLETED
}

data class ActiveCallLifecycleEntry(
    val callId: String,
    val lifecycleState: PersistedCallState,
    val phoneNumber: String,
    val isIncoming: Boolean,
    val ringingStartTimeMs: Long?,
    val callStartTimeMs: Long,
    val callEndTimeMs: Long? = null,
    val capturePathCandidate: CallCaptureTier,
    val createdAtEpochMs: Long
) {
    fun toSerializedString(): String {
        return listOf(
            callId,
            lifecycleState.name,
            phoneNumber,
            isIncoming.toString(),
            ringingStartTimeMs?.toString() ?: "NULL",
            callStartTimeMs.toString(),
            callEndTimeMs?.toString() ?: "NULL",
            capturePathCandidate.name,
            createdAtEpochMs.toString()
        ).joinToString("\n")
    }

    companion object {
        fun fromSerializedString(data: String): ActiveCallLifecycleEntry? {
            return runCatching {
                val lines = data.lines().filter { it.isNotBlank() }
                if (lines.size >= 9) {
                    ActiveCallLifecycleEntry(
                        callId = lines[0],
                        lifecycleState = PersistedCallState.valueOf(lines[1]),
                        phoneNumber = lines[2],
                        isIncoming = lines[3].toBoolean(),
                        ringingStartTimeMs = if (lines[4] == "NULL" || lines[4].isBlank()) null else lines[4].toLongOrNull(),
                        callStartTimeMs = lines[5].toLong(),
                        callEndTimeMs = if (lines[6] == "NULL" || lines[6].isBlank()) null else lines[6].toLongOrNull(),
                        capturePathCandidate = CallCaptureTier.valueOf(lines[7]),
                        createdAtEpochMs = lines[8].toLong()
                    )
                } else null
            }.getOrNull()
        }
    }
}

object CallLifecycleJournal {

    private const val JOURNAL_FILE_NAME = "call_lifecycle_journal.txt"

    private fun getJournalFile(context: Context): File {
        val dir = File(context.filesDir, "calls").apply { mkdirs() }
        return File(dir, JOURNAL_FILE_NAME)
    }

    @Synchronized
    fun recordOffhook(context: Context, entry: ActiveCallLifecycleEntry) {
        runCatching {
            getJournalFile(context).writeText(entry.toSerializedString())
        }
    }

    @Synchronized
    fun recordIdle(context: Context, endTimeMs: Long): ActiveCallLifecycleEntry? {
        val active = getActiveEntry(context) ?: return null
        val updated = active.copy(
            lifecycleState = PersistedCallState.ENDED_IMPORT_PENDING,
            callEndTimeMs = endTimeMs
        )
        runCatching {
            getJournalFile(context).writeText(updated.toSerializedString())
        }
        return updated
    }

    @Synchronized
    fun getActiveEntry(context: Context): ActiveCallLifecycleEntry? {
        val file = getJournalFile(context)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        return ActiveCallLifecycleEntry.fromSerializedString(text)
    }

    @Synchronized
    fun clear(context: Context) {
        runCatching {
            val file = getJournalFile(context)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    /**
     * Reconciles abandoned call sessions upon application startup or process recreation.
     * Invariant: For OEM_IMPORT, a missing Mobiltool temp file is normal and must NEVER yield CORRUPT.
     */
    @Synchronized
    fun reconcileAbandonedSessions(context: Context) {
        val file = getJournalFile(context)
        if (!file.exists()) return

        val text = runCatching { file.readText() }.getOrNull() ?: return
        val entry = ActiveCallLifecycleEntry.fromSerializedString(text) ?: run {
            file.delete()
            return
        }

        val app = context.applicationContext as? PersonalToolApplication
        val dao = app?.database?.callDao()
        val now = System.currentTimeMillis()
        val startTime = entry.callStartTimeMs
        val endTime = entry.callEndTimeMs ?: now
        val durationMs = (endTime - startTime).coerceAtLeast(0L)
        val direction = if (entry.isIncoming) CallDirection.INCOMING else CallDirection.OUTGOING

        val session = CallSession(
            id = entry.callId,
            phoneNumber = entry.phoneNumber,
            direction = direction,
            startTimeEpochMs = startTime,
            endTimeEpochMs = endTime,
            durationMs = durationMs,
            recordingQuality = RecordingQuality.UNSUPPORTED,
            captureTier = entry.capturePathCandidate,
            unrecordedReason = "Call session interrupted by device or process restart before completion."
        )

        if (dao != null) {
            CoroutineScope(Dispatchers.IO).launch {
                dao.insertCall(CallEntity.fromDomain(session))
            }
        }

        file.delete()
    }
}
