package com.personaltool.app.capture

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.personaltool.app.PersonalToolApplication
import com.personaltool.core.model.call.CallCaptureTier
import com.personaltool.core.model.call.CallDirection
import com.personaltool.core.model.call.CallSession
import com.personaltool.core.model.call.RecordingQuality
import com.personaltool.core.storage.entity.CallEntity
import kotlinx.coroutines.delay
import java.io.File

/**
 * Durable, background-safe worker for post-call OEM recording ingestion.
 * Scheduled via WorkManager on call termination (IDLE) using unique work semantics.
 */
class OemPostCallImportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_CALL_ID = "call_id"
        const val KEY_PHONE_NUMBER = "phone_number"
        const val KEY_IS_INCOMING = "is_incoming"
        const val KEY_START_TIME = "start_time"
        const val KEY_END_TIME = "end_time"
        const val KEY_CANDIDATE_TIER = "candidate_tier"

        fun createInputData(
            callId: String,
            phoneNumber: String,
            isIncoming: Boolean,
            startTimeMs: Long,
            endTimeMs: Long,
            candidateTier: CallCaptureTier
        ) = workDataOf(
            KEY_CALL_ID to callId,
            KEY_PHONE_NUMBER to phoneNumber,
            KEY_IS_INCOMING to isIncoming,
            KEY_START_TIME to startTimeMs,
            KEY_END_TIME to endTimeMs,
            KEY_CANDIDATE_TIER to candidateTier.name
        )
    }

    override suspend fun doWork(): Result {
        val callId = inputData.getString(KEY_CALL_ID) ?: return Result.failure()
        val phoneNumber = inputData.getString(KEY_PHONE_NUMBER) ?: "Unknown"
        val isIncoming = inputData.getBoolean(KEY_IS_INCOMING, false)
        val startTimeMs = inputData.getLong(KEY_START_TIME, 0L)
        val endTimeMs = inputData.getLong(KEY_END_TIME, System.currentTimeMillis())
        val direction = if (isIncoming) CallDirection.INCOMING else CallDirection.OUTGOING
        val durationMs = (endTimeMs - startTimeMs).coerceAtLeast(0L)

        val app = applicationContext as? PersonalToolApplication
        val dao = app?.database?.callDao() ?: return Result.retry()

        // 1. Flush delay for OEM dialer recorder (1.5s)
        delay(1500)

        // 2. Discover and import via MediaStore & filesystem
        val vaultDir = File(applicationContext.filesDir, "calls").apply { mkdirs() }
        val importResult = OemRecordingImporter.findAndImport(
            context = applicationContext,
            phoneNumber = phoneNumber,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            targetVaultDir = vaultDir
        )

        val capability = CallCaptureCapabilityDetector.detectCapability(applicationContext)

        when (importResult) {
            is OemImportResult.Success -> {
                val file = importResult.importedFile
                val inspection = AudioFileInspector.inspectRecordedFile(
                    filePath = file.absolutePath,
                    defaultQuality = RecordingQuality.MIXED_UNVERIFIED,
                    captureTier = CallCaptureTier.OEM_IMPORT,
                    isPhysicallyQualified = capability.isBidirectionalQualified
                )

                val session = CallSession(
                    id = callId,
                    phoneNumber = phoneNumber,
                    direction = direction,
                    startTimeEpochMs = startTimeMs,
                    endTimeEpochMs = endTimeMs,
                    durationMs = if (inspection.isValid) inspection.durationMs else durationMs,
                    recordingQuality = if (inspection.isValid) inspection.determinedQuality else RecordingQuality.MIXED_UNVERIFIED,
                    captureTier = CallCaptureTier.OEM_IMPORT,
                    audioFilePath = file.absolutePath,
                    fileSizeBytes = importResult.fileSize
                )
                dao.insertCall(CallEntity.fromDomain(session))
            }

            is OemImportResult.NotFound -> {
                val unrecordedSession = CallSession(
                    id = callId,
                    phoneNumber = phoneNumber,
                    direction = direction,
                    startTimeEpochMs = startTimeMs,
                    endTimeEpochMs = endTimeMs,
                    durationMs = durationMs,
                    recordingQuality = RecordingQuality.UNSUPPORTED,
                    captureTier = CallCaptureTier.OEM_IMPORT,
                    unrecordedReason = "OEM Ingestion: ${importResult.diagnosticReason}"
                )
                dao.insertCall(CallEntity.fromDomain(unrecordedSession))
            }

            is OemImportResult.AmbiguousCollision -> {
                val unrecordedSession = CallSession(
                    id = callId,
                    phoneNumber = phoneNumber,
                    direction = direction,
                    startTimeEpochMs = startTimeMs,
                    endTimeEpochMs = endTimeMs,
                    durationMs = durationMs,
                    recordingQuality = RecordingQuality.UNSUPPORTED,
                    captureTier = CallCaptureTier.OEM_IMPORT,
                    unrecordedReason = "OEM Collision Safety: ${importResult.diagnosticReason}"
                )
                dao.insertCall(CallEntity.fromDomain(unrecordedSession))
            }
        }

        CallLifecycleJournal.clear(applicationContext)
        return Result.success()
    }
}
