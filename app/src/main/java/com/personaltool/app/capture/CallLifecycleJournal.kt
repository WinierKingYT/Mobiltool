package com.personaltool.app.capture

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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

    const val STALE_CALL_THRESHOLD_MS = 14400000L // 4 hours threshold for truly abandoned sessions
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
     * Reconciles persisted call journals upon application startup or process recreation.
     * Truthful Reconciliation Invariants:
     * 1. OFFHOOK_ACTIVE is NOT prematurely converted to UNSUPPORTED/deleted unless age >= STALE_CALL_THRESHOLD_MS.
     * 2. ENDED_IMPORT_PENDING idempotently re-enqueues the WorkManager import worker.
     * 3. Truly stale sessions (>= 4h) are logged as truthful UNSUPPORTED metadata records without fabricating audio.
     */
    @Synchronized
    fun reconcileOnStartup(context: Context) {
        val file = getJournalFile(context)
        if (!file.exists()) return

        val text = runCatching { file.readText() }.getOrNull() ?: return
        val entry = ActiveCallLifecycleEntry.fromSerializedString(text) ?: run {
            file.delete()
            return
        }

        when (entry.lifecycleState) {
            PersistedCallState.OFFHOOK_ACTIVE -> {
                val ageMs = System.currentTimeMillis() - entry.callStartTimeMs
                if (ageMs < STALE_CALL_THRESHOLD_MS) {
                    // Call may still be in-progress on the telephony line! Keep journal for subsequent IDLE broadcast.
                    return
                }

                // Truly stale abandoned call (e.g. device rebooted hours ago)
                val app = context.applicationContext as? PersonalToolApplication
                val dao = app?.database?.callDao()
                val session = CallSession(
                    id = entry.callId,
                    phoneNumber = entry.phoneNumber,
                    direction = if (entry.isIncoming) CallDirection.INCOMING else CallDirection.OUTGOING,
                    startTimeEpochMs = entry.callStartTimeMs,
                    endTimeEpochMs = entry.callStartTimeMs + STALE_CALL_THRESHOLD_MS,
                    durationMs = STALE_CALL_THRESHOLD_MS,
                    recordingQuality = RecordingQuality.UNSUPPORTED,
                    captureTier = entry.capturePathCandidate,
                    unrecordedReason = "Call session timed out after 4 hours without receiving telephony termination event."
                )

                if (dao != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        dao.insertCall(CallEntity.fromDomain(session))
                    }
                }
                file.delete()
            }

            PersistedCallState.ENDED_IMPORT_PENDING -> {
                // Re-ensure WorkManager import task is scheduled
                if (entry.capturePathCandidate == CallCaptureTier.OEM_IMPORT) {
                    val workRequest = OneTimeWorkRequestBuilder<OemPostCallImportWorker>()
                        .setInputData(
                            OemPostCallImportWorker.createInputData(
                                callId = entry.callId,
                                phoneNumber = entry.phoneNumber,
                                isIncoming = entry.isIncoming,
                                startTimeMs = entry.callStartTimeMs,
                                endTimeMs = entry.callEndTimeMs ?: System.currentTimeMillis(),
                                candidateTier = entry.capturePathCandidate
                            )
                        )
                        .build()

                    WorkManager.getInstance(context).enqueueUniqueWork(
                        "oem_import_${entry.callId}",
                        ExistingWorkPolicy.KEEP,
                        workRequest
                    )
                }
            }

            PersistedCallState.COMPLETED -> {
                file.delete()
            }
        }
    }
}
