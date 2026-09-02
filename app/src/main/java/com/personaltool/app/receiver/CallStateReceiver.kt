package com.personaltool.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.personaltool.app.PersonalToolApplication
import com.personaltool.app.capture.ActiveCallLifecycleEntry
import com.personaltool.app.capture.CallCaptureCapabilityDetector
import com.personaltool.app.capture.CallLifecycleJournal
import com.personaltool.app.capture.CallSessionTracker
import com.personaltool.app.capture.CallTerminationEvent
import com.personaltool.app.capture.OemPostCallImportWorker
import com.personaltool.app.capture.PersistedCallState
import com.personaltool.core.model.call.CallCaptureTier
import com.personaltool.core.model.call.CallDirection
import com.personaltool.core.model.call.CallSession
import com.personaltool.core.model.call.RecordingQuality
import com.personaltool.core.storage.entity.CallEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Durable, background-safe telephony broadcast receiver.
 * Uses CallLifecycleJournal as the authoritative cross-process source of truth for OFFHOOK -> IDLE recovery.
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

                    // Persist authoritative active call lifecycle entry for cross-process durability
                    CallLifecycleJournal.recordOffhook(
                        context = context,
                        entry = ActiveCallLifecycleEntry(
                            callId = callId,
                            lifecycleState = PersistedCallState.OFFHOOK_ACTIVE,
                            phoneNumber = snapshot.phoneNumber ?: "Unknown",
                            isIncoming = snapshot.isIncoming,
                            ringingStartTimeMs = snapshot.ringingStartTimeMs,
                            callStartTimeMs = startTime,
                            callEndTimeMs = null,
                            capturePathCandidate = capability.tier,
                            createdAtEpochMs = System.currentTimeMillis()
                        )
                    )
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                val pendingResult = goAsync()
                val now = System.currentTimeMillis()
                val activeJournalEntry = CallLifecycleJournal.getActiveEntry(context)
                val inMemoryEvent = CallSessionTracker.onIdle()

                if (activeJournalEntry != null && (
                            activeJournalEntry.lifecycleState == PersistedCallState.OFFHOOK_ACTIVE ||
                            activeJournalEntry.lifecycleState == PersistedCallState.ENDED_IMPORT_PENDING
                        )) {
                    // Authoritative cross-process active call recovery
                    val updatedEntry = CallLifecycleJournal.recordIdle(context, now) ?: activeJournalEntry
                    val capability = CallCaptureCapabilityDetector.detectCapability(context)

                    val callId = updatedEntry.callId
                    val startTime = updatedEntry.callStartTimeMs
                    val endTime = updatedEntry.callEndTimeMs ?: now
                    val isIncoming = updatedEntry.isIncoming
                    val phoneNumber = updatedEntry.phoneNumber

                    if (capability.canAttemptFeasibility && capability.tier == CallCaptureTier.OEM_IMPORT) {
                        // Enqueue durable WorkManager task with unique work semantics
                        val workRequest = OneTimeWorkRequestBuilder<OemPostCallImportWorker>()
                            .setInputData(
                                OemPostCallImportWorker.createInputData(
                                    callId = callId,
                                    phoneNumber = phoneNumber,
                                    isIncoming = isIncoming,
                                    startTimeMs = startTime,
                                    endTimeMs = endTime,
                                    candidateTier = CallCaptureTier.OEM_IMPORT
                                )
                            )
                            .build()

                        WorkManager.getInstance(context).enqueueUniqueWork(
                            "oem_import_$callId",
                            ExistingWorkPolicy.REPLACE,
                            workRequest
                        )
                        pendingResult.finish()
                    } else {
                        // Metadata-only unsupported record
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val app = context.applicationContext as? PersonalToolApplication
                                val dao = app?.database?.callDao()
                                if (dao != null) {
                                    val unrecordedSession = CallSession(
                                        id = callId,
                                        phoneNumber = phoneNumber,
                                        direction = if (isIncoming) CallDirection.INCOMING else CallDirection.OUTGOING,
                                        startTimeEpochMs = startTime,
                                        endTimeEpochMs = endTime,
                                        durationMs = (endTime - startTime).coerceAtLeast(0L),
                                        recordingQuality = RecordingQuality.UNSUPPORTED,
                                        captureTier = capability.tier,
                                        unrecordedReason = capability.physicalLimitationReason
                                    )
                                    dao.insertCall(CallEntity.fromDomain(unrecordedSession))
                                }
                            } finally {
                                CallLifecycleJournal.clear(context)
                                pendingResult.finish()
                            }
                        }
                    }
                } else if (inMemoryEvent is CallTerminationEvent.MissedCall) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val app = context.applicationContext as? PersonalToolApplication
                            val dao = app?.database?.callDao()
                            if (dao != null) {
                                val missedSession = CallSession(
                                    id = UUID.randomUUID().toString(),
                                    phoneNumber = inMemoryEvent.phoneNumber,
                                    direction = CallDirection.MISSED,
                                    startTimeEpochMs = inMemoryEvent.timestampMs,
                                    endTimeEpochMs = now,
                                    durationMs = 0L,
                                    recordingQuality = RecordingQuality.UNSUPPORTED,
                                    captureTier = CallCaptureTier.UNSUPPORTED_USERSPACE,
                                    unrecordedReason = "Missed incoming call (unanswered)."
                                )
                                dao.insertCall(CallEntity.fromDomain(missedSession))
                            }
                        } finally {
                            CallLifecycleJournal.clear(context)
                            pendingResult.finish()
                        }
                    }
                } else if (inMemoryEvent is CallTerminationEvent.ActiveCallEnded) {
                    val capability = CallCaptureCapabilityDetector.detectCapability(context)
                    val callId = UUID.randomUUID().toString()
                    val startTime = inMemoryEvent.startTimeMs
                    val endTime = inMemoryEvent.endTimeMs
                    val isIncoming = inMemoryEvent.isIncoming
                    val phoneNumber = inMemoryEvent.phoneNumber

                    if (capability.canAttemptFeasibility && capability.tier == CallCaptureTier.OEM_IMPORT) {
                        val workRequest = OneTimeWorkRequestBuilder<OemPostCallImportWorker>()
                            .setInputData(
                                OemPostCallImportWorker.createInputData(
                                    callId = callId,
                                    phoneNumber = phoneNumber,
                                    isIncoming = isIncoming,
                                    startTimeMs = startTime,
                                    endTimeMs = endTime,
                                    candidateTier = CallCaptureTier.OEM_IMPORT
                                )
                            )
                            .build()

                        WorkManager.getInstance(context).enqueueUniqueWork(
                            "oem_import_$callId",
                            ExistingWorkPolicy.REPLACE,
                            workRequest
                        )
                        pendingResult.finish()
                    } else {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val app = context.applicationContext as? PersonalToolApplication
                                val dao = app?.database?.callDao()
                                if (dao != null) {
                                    val unrecordedSession = CallSession(
                                        id = callId,
                                        phoneNumber = phoneNumber,
                                        direction = if (isIncoming) CallDirection.INCOMING else CallDirection.OUTGOING,
                                        startTimeEpochMs = startTime,
                                        endTimeEpochMs = endTime,
                                        durationMs = (endTime - startTime).coerceAtLeast(0L),
                                        recordingQuality = RecordingQuality.UNSUPPORTED,
                                        captureTier = capability.tier,
                                        unrecordedReason = capability.physicalLimitationReason
                                    )
                                    dao.insertCall(CallEntity.fromDomain(unrecordedSession))
                                }
                            } finally {
                                CallLifecycleJournal.clear(context)
                                pendingResult.finish()
                            }
                        }
                    }
                } else {
                    pendingResult.finish()
                }
            }
        }
    }
}
