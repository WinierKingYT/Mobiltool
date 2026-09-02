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
 * Uses CallLifecycleJournal for cross-process state continuity and schedules durable WorkManager workers on IDLE.
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

                    // Persist active call lifecycle entry for cross-process durability
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
                                CallLifecycleJournal.clear(context)
                                pendingResult.finish()
                            }
                        }
                    }

                    is CallTerminationEvent.ActiveCallEnded -> {
                        val pendingResult = goAsync()
                        val capability = CallCaptureCapabilityDetector.detectCapability(context)
                        val activeEntry = CallLifecycleJournal.recordIdle(context, event.endTimeMs)

                        // Preserve identical callId from OFFHOOK journal even if process died and restarted
                        val callId = activeEntry?.callId ?: UUID.randomUUID().toString()
                        val startTime = activeEntry?.callStartTimeMs ?: event.startTimeMs
                        val endTime = event.endTimeMs
                        val isIncoming = activeEntry?.isIncoming ?: event.isIncoming

                        if (capability.canAttemptFeasibility && capability.tier == CallCaptureTier.OEM_IMPORT) {
                            // Schedule durable background WorkManager task with unique work semantics
                            val workRequest = OneTimeWorkRequestBuilder<OemPostCallImportWorker>()
                                .setInputData(
                                    OemPostCallImportWorker.createInputData(
                                        callId = callId,
                                        phoneNumber = event.phoneNumber,
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
                            // Metadata-only unsupported userspace record
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val app = context.applicationContext as? PersonalToolApplication
                                    val dao = app?.database?.callDao()
                                    if (dao != null) {
                                        val unrecordedSession = CallSession(
                                            id = callId,
                                            phoneNumber = event.phoneNumber,
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
                    }

                    is CallTerminationEvent.NoActiveCall -> {
                        // Idempotent duplicate event
                    }
                }
            }
        }
    }
}
