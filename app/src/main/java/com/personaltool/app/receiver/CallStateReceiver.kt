package com.personaltool.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.personaltool.app.PersonalToolApplication
import com.personaltool.app.capture.CallSessionTracker
import com.personaltool.app.capture.CallTerminationEvent
import com.personaltool.app.service.CallCaptureForegroundService
import com.personaltool.core.model.call.CallCaptureTier
import com.personaltool.core.model.call.CallDirection
import com.personaltool.core.model.call.CallSession
import com.personaltool.core.model.call.RecordingQuality
import com.personaltool.core.storage.entity.CallEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class CallStateReceiver : BroadcastReceiver() {

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_NEW_OUTGOING_CALL) {
            val dialedNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            CallSessionTracker.onRinging(dialedNumber)
            return
        }

        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
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
                        val serviceIntent = Intent(context, CallCaptureForegroundService::class.java).apply {
                            action = CallCaptureForegroundService.ACTION_START_CALL_CAPTURE
                            putExtra(CallCaptureForegroundService.EXTRA_PHONE_NUMBER, snapshot.phoneNumber ?: "Unknown Caller")
                            putExtra(CallCaptureForegroundService.EXTRA_IS_INCOMING, snapshot.isIncoming)
                        }
                        ContextCompat.startForegroundService(context, serviceIntent)
                    }
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    when (val event = CallSessionTracker.onIdle()) {
                        is CallTerminationEvent.ActiveCallEnded -> {
                            val serviceIntent = Intent(context, CallCaptureForegroundService::class.java).apply {
                                action = CallCaptureForegroundService.ACTION_STOP_CALL_CAPTURE
                            }
                            context.startService(serviceIntent)
                        }
                        is CallTerminationEvent.MissedCall -> {
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
                                CoroutineScope(Dispatchers.IO).launch {
                                    dao.insertCall(CallEntity.fromDomain(missedSession))
                                }
                            }
                        }
                        is CallTerminationEvent.NoActiveCall -> {
                            // Idempotent no-op
                        }
                    }
                }
            }
        }
    }
}
