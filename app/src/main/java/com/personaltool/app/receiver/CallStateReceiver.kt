package com.personaltool.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.personaltool.app.capture.CallSessionTracker
import com.personaltool.app.service.CallCaptureForegroundService

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
                    val shouldStop = CallSessionTracker.onIdle()
                    if (shouldStop) {
                        val serviceIntent = Intent(context, CallCaptureForegroundService::class.java).apply {
                            action = CallCaptureForegroundService.ACTION_STOP_CALL_CAPTURE
                        }
                        context.startService(serviceIntent)
                    }
                }
            }
        }
    }
}
