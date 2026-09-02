package com.personaltool.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.personaltool.app.PersonalToolApplication
import com.personaltool.app.capture.AudioFileInspector
import com.personaltool.app.capture.CallCaptureCapabilityDetector
import com.personaltool.app.capture.CallRecordingJournal
import com.personaltool.app.capture.CallSessionTracker
import com.personaltool.app.capture.CallTerminationEvent
import com.personaltool.app.capture.InFlightCallJournalEntry
import com.personaltool.app.capture.OemImportResult
import com.personaltool.app.capture.OemRecordingImporter
import com.personaltool.core.model.call.CallCaptureTier
import com.personaltool.core.model.call.CallDirection
import com.personaltool.core.model.call.CallSession
import com.personaltool.core.model.call.RecordingQuality
import com.personaltool.core.storage.entity.CallEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Durable, background-safe telephony broadcast receiver.
 * Uses goAsync() for bounded completion; avoids illegal while-in-use FGS background starts.
 */
class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            return
        }

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                CallSessionTracker.onRinging(incomingNumber)
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                val shouldStart = CallSessionTracker.onOffhook(incomingNumber)
                if (shouldStart) {
                    val snapshot = CallSessionTracker.snapshot.value
                    val capability = CallCaptureCapabilityDetector.detectCapability(context)
                    val callId = UUID.randomUUID().toString()
                    val startTime = snapshot.callStartTimeMs ?: System.currentTimeMillis()

                    // Register minimal crash-safety journal entry without holding long-lived FGS
                    CallRecordingJournal.recordStart(
                        context = context,
                        entry = InFlightCallJournalEntry(
                            callId = callId,
                            phoneNumber = snapshot.phoneNumber ?: "Unknown",
                            direction = if (snapshot.isIncoming) CallDirection.INCOMING else CallDirection.OUTGOING,
                            captureTier = capability.tier,
                            startTimeEpochMs = startTime,
                            tempAudioPath = File(context.filesDir, "calls/in_flight_$callId.m4a").absolutePath
                        )
                    )
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                when (val event = CallSessionTracker.onIdle()) {
                    is CallTerminationEvent.MissedCall -> {
                        val pendingResult = goAsync()
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val app = context.applicationContext as? PersonalToolApplication
                                val dao = app?.database?.callDao()
                                if (dao != null) {
                                    val missedSession = CallSession(
                                        id = UUID.randomUUID().toString(),
                                        phoneNumber = event.phoneNumber,
                                        direction = CallDirection.MISSED,
                                        startTimeEpochMs = event.timestampMs,
                                        endTimeEpochMs = System.currentTimeMillis(),
                                        durationMs = 0L,
                                        recordingQuality = RecordingQuality.UNSUPPORTED,
                                        captureTier = CallCaptureTier.UNSUPPORTED_USERSPACE,
                                        unrecordedReason = "Missed incoming call (unanswered)."
                                    )
                                    dao.insertCall(CallEntity.fromDomain(missedSession))
                                }
                            } finally {
                                CallRecordingJournal.recordEnd(context)
                                pendingResult.finish()
                            }
                        }
                    }

                    is CallTerminationEvent.ActiveCallEnded -> {
                        val pendingResult = goAsync()
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                handleActiveCallEnded(context, event)
                            } finally {
                                CallRecordingJournal.recordEnd(context)
                                pendingResult.finish()
                            }
                        }
                    }

                    is CallTerminationEvent.NoActiveCall -> {
                        // Idempotent duplicate event
                    }
                }
            }
        }
    }

    private suspend fun handleActiveCallEnded(context: Context, event: CallTerminationEvent.ActiveCallEnded) {
        val app = context.applicationContext as? PersonalToolApplication
        val dao = app?.database?.callDao() ?: return
        val capability = CallCaptureCapabilityDetector.detectCapability(context)
        val callId = UUID.randomUUID().toString()
        val durationMs = (event.endTimeMs - event.startTimeMs).coerceAtLeast(0L)
        val direction = if (event.isIncoming) CallDirection.INCOMING else CallDirection.OUTGOING

        if (capability.canAttemptFeasibility && capability.tier == CallCaptureTier.OEM_IMPORT) {
            // Allow bounded OEM flush delay (up to 1.5s)
            delay(1500)

            val vaultDir = File(context.filesDir, "calls").apply { mkdirs() }
            val importResult = OemRecordingImporter.findAndImport(
                context = context,
                phoneNumber = event.phoneNumber,
                startTimeMs = event.startTimeMs,
                endTimeMs = event.endTimeMs,
                targetVaultDir = vaultDir
            )

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
                        phoneNumber = event.phoneNumber,
                        direction = direction,
                        startTimeEpochMs = event.startTimeMs,
                        endTimeEpochMs = event.endTimeMs,
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
                        phoneNumber = event.phoneNumber,
                        direction = direction,
                        startTimeEpochMs = event.startTimeMs,
                        endTimeEpochMs = event.endTimeMs,
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
                        phoneNumber = event.phoneNumber,
                        direction = direction,
                        startTimeEpochMs = event.startTimeMs,
                        endTimeEpochMs = event.endTimeMs,
                        durationMs = durationMs,
                        recordingQuality = RecordingQuality.UNSUPPORTED,
                        captureTier = CallCaptureTier.OEM_IMPORT,
                        unrecordedReason = "OEM Collision Safety: ${importResult.diagnosticReason}"
                    )
                    dao.insertCall(CallEntity.fromDomain(unrecordedSession))
                }
            }
        } else {
            // Metadata-only unsupported userspace record
            val unrecordedSession = CallSession(
                id = callId,
                phoneNumber = event.phoneNumber,
                direction = direction,
                startTimeEpochMs = event.startTimeMs,
                endTimeEpochMs = event.endTimeMs,
                durationMs = durationMs,
                recordingQuality = RecordingQuality.UNSUPPORTED,
                captureTier = capability.tier,
                unrecordedReason = capability.physicalLimitationReason
            )
            dao.insertCall(CallEntity.fromDomain(unrecordedSession))
        }
    }
}
