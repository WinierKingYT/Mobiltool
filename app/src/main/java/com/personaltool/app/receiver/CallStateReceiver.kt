package com.personaltool.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.personaltool.app.service.CallCaptureForegroundService

class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var savedPhoneNumber: String? = null
        private var isIncoming = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_NEW_OUTGOING_CALL) {
            savedPhoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            isIncoming = false
            return
        }

        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            if (!incomingNumber.isNullOrBlank()) {
                savedPhoneNumber = incomingNumber
                isIncoming = true
            }

            val state = when (stateStr) {
                TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
                TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                else -> TelephonyManager.CALL_STATE_IDLE
            }

            onCallStateChanged(context, state)
        }
    }

    private fun onCallStateChanged(context: Context, state: Int) {
        if (lastState == state) return

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                isIncoming = true
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call answered or dialed — start foreground call capture
                val serviceIntent = Intent(context, CallCaptureForegroundService::class.java).apply {
                    action = CallCaptureForegroundService.ACTION_START_CALL_CAPTURE
                    putExtra(CallCaptureForegroundService.EXTRA_PHONE_NUMBER, savedPhoneNumber ?: "Unknown Contact")
                    putExtra(CallCaptureForegroundService.EXTRA_IS_INCOMING, isIncoming)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // Call ended — stop foreground service and finalize recording
                val serviceIntent = Intent(context, CallCaptureForegroundService::class.java).apply {
                    action = CallCaptureForegroundService.ACTION_STOP_CALL_CAPTURE
                }
                context.startService(serviceIntent)
                savedPhoneNumber = null
            }
        }
        lastState = state
    }
}
