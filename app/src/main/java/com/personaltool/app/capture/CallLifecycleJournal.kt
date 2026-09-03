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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /**
     * Records IDLE transition.
     * Idempotency Invariant (P1-PREFLIGHT-25):
     * If entry is already ENDED_IMPORT_PENDING, the first recorded endTimeMs is preserved and not overwritten.
     */
    @Synchronized
    fun recordIdle(context: Context, endTimeMs: Long): ActiveCallLifecycleEntry? {
        val active = getActiveEntry(context) ?: return null
        if (active.lifecycleState == PersistedCallState.ENDED_IMPORT_PENDING) {
            return active
        }
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
     * Reconciles persisted call journals upon application startup.
     * Invariants:
     * - OFFHOOK_ACTIVE: Kept intact if age < 4h (call may still be ongoing).
     * - Truly stale sessions: Recorded with durationMs = 0 (unknown duration) and explicit diagnostic (P1-PREFLIGHT-22).
     * - Durable persistence: Journal file is ONLY deleted after Room DB insert transaction completes (P1-PREFLIGHT-23).
     * - ENDED_IMPORT_PENDING: Idempotently re-ensures WorkManager task is scheduled (P1-PREFLIGHT-25).
     */
    suspend fun reconcileOnStartup(context: Context) = withContext(Dispatchers.IO) {
        val file = getJournalFile(context)
        if (!file.exists()) return@withContext

        val text = runCatching { file.readText() }.getOrNull() ?: return@withContext
        val entry = ActiveCallLifecycleEntry.fromSerializedString(text) ?: run {
            file.delete()
            return@withContext
        }

        when (entry.lifecycleState) {
            PersistedCallState.OFFHOOK_ACTIVE -> {
                val now = System.currentTimeMillis()
                val ageMs = now - entry.callStartTimeMs
                if (ageMs < STALE_CALL_THRESHOLD_MS) {
                    // Call may still be in-progress on the telephony line! Keep journal for subsequent IDLE broadcast.
                    return@withContext
                }

                // Truly stale abandoned call where termination was not observed
                val app = context.applicationContext as? PersonalToolApplication
                val dao = app?.database?.callDao()
                val session = CallSession(
                    id = entry.callId,
                    phoneNumber = entry.phoneNumber,
                    direction = if (entry.isIncoming) CallDirection.INCOMING else CallDirection.OUTGOING,
                    startTimeEpochMs = entry.callStartTimeMs,
                    endTimeEpochMs = entry.callStartTimeMs,
                    durationMs = 0L, // P1-PREFLIGHT-22: Never fabricate 4h duration; duration is unknown
                    recordingQuality = RecordingQuality.UNSUPPORTED,
                    captureTier = entry.capturePathCandidate,
                    unrecordedReason = "Call session timed out; telephony termination event was not observed. Actual duration is unknown."
                )

                if (dao != null) {
                    try {
                        dao.insertCall(CallEntity.fromDomain(session))
                        // P1-PREFLIGHT-23: Delete journal only after DB write succeeds
                        file.delete()
                    } catch (_: Exception) {
                        // Keep journal if DB persistence failed so it remains recoverable
                    }
                }
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
