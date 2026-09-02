package com.personaltool.app.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

enum class TelephonyTrackedState {
    IDLE,
    RINGING,
    OFFHOOK
}

data class ActiveCallSnapshot(
    val state: TelephonyTrackedState,
    val phoneNumber: String?,
    val isIncoming: Boolean,
    val isRecordingActive: Boolean,
    val ringingStartTimeMs: Long? = null,
    val callStartTimeMs: Long? = null
)

sealed class CallTerminationEvent {
    object NoActiveCall : CallTerminationEvent()
    data class MissedCall(val phoneNumber: String, val timestampMs: Long) : CallTerminationEvent()
    data class ActiveCallEnded(
        val phoneNumber: String,
        val isIncoming: Boolean,
        val startTimeMs: Long,
        val endTimeMs: Long
    ) : CallTerminationEvent()
}

object CallSessionTracker {

    private val _snapshot = MutableStateFlow(
        ActiveCallSnapshot(
            state = TelephonyTrackedState.IDLE,
            phoneNumber = null,
            isIncoming = false,
            isRecordingActive = false,
            ringingStartTimeMs = null,
            callStartTimeMs = null
        )
    )
    val snapshot: StateFlow<ActiveCallSnapshot> = _snapshot.asStateFlow()

    private val recordingInProgress = AtomicBoolean(false)

    /**
     * Triggered on incoming call ring. Sets state to RINGING, isIncoming = true.
     */
    @Synchronized
    fun onRinging(incomingNumber: String?): Boolean {
        val current = _snapshot.value
        val number = incomingNumber ?: current.phoneNumber ?: "Unknown Caller"
        _snapshot.value = ActiveCallSnapshot(
            state = TelephonyTrackedState.RINGING,
            phoneNumber = number,
            isIncoming = true,
            isRecordingActive = false,
            ringingStartTimeMs = System.currentTimeMillis(),
            callStartTimeMs = null
        )
        return false // Do not start capture on ringing
    }

    /**
     * Triggered when call goes offhook (answered or dialed).
     * Invariant:
     * - If state was RINGING -> INCOMING answered.
     * - If state was IDLE -> OUTGOING dialed.
     */
    @Synchronized
    fun onOffhook(number: String?): Boolean {
        val current = _snapshot.value
        val isFirstTransition = recordingInProgress.compareAndSet(false, true)

        val isIncoming = if (isFirstTransition) {
            current.state == TelephonyTrackedState.RINGING
        } else {
            current.isIncoming
        }

        val resolvedNumber = when {
            !number.isNullOrBlank() -> number
            !current.phoneNumber.isNullOrBlank() -> current.phoneNumber
            isIncoming -> "Unknown Caller"
            else -> "Outgoing Call (Number Unavailable)"
        }

        _snapshot.value = ActiveCallSnapshot(
            state = TelephonyTrackedState.OFFHOOK,
            phoneNumber = resolvedNumber,
            isIncoming = isIncoming,
            isRecordingActive = isFirstTransition,
            ringingStartTimeMs = current.ringingStartTimeMs,
            callStartTimeMs = if (isFirstTransition) System.currentTimeMillis() else current.callStartTimeMs
        )

        return isFirstTransition
    }

    /**
     * Triggered when call terminates to IDLE.
     * Invariant:
     * - RINGING -> IDLE = Missed incoming call.
     * - OFFHOOK -> IDLE = Active call ended (triggers post-call finalization).
     * - IDLE -> IDLE (duplicate) = NoActiveCall.
     */
    @Synchronized
    fun onIdle(): CallTerminationEvent {
        val current = _snapshot.value
        val now = System.currentTimeMillis()
        val wasRecording = recordingInProgress.getAndSet(false)

        val result: CallTerminationEvent = when {
            current.state == TelephonyTrackedState.RINGING -> {
                // Call was ringing but never answered -> Missed Call
                CallTerminationEvent.MissedCall(
                    phoneNumber = current.phoneNumber ?: "Unknown Caller",
                    timestampMs = current.ringingStartTimeMs ?: now
                )
            }
            wasRecording || current.state == TelephonyTrackedState.OFFHOOK -> {
                // Call was active/offhook -> Ended Call
                CallTerminationEvent.ActiveCallEnded(
                    phoneNumber = current.phoneNumber ?: if (current.isIncoming) "Unknown Caller" else "Outgoing Call",
                    isIncoming = current.isIncoming,
                    startTimeMs = current.callStartTimeMs ?: now,
                    endTimeMs = now
                )
            }
            else -> CallTerminationEvent.NoActiveCall
        }

        _snapshot.value = ActiveCallSnapshot(
            state = TelephonyTrackedState.IDLE,
            phoneNumber = null,
            isIncoming = false,
            isRecordingActive = false,
            ringingStartTimeMs = null,
            callStartTimeMs = null
        )

        return result
    }

    fun isRecording(): Boolean = recordingInProgress.get()

    @Synchronized
    fun reset() {
        recordingInProgress.set(false)
        _snapshot.value = ActiveCallSnapshot(
            state = TelephonyTrackedState.IDLE,
            phoneNumber = null,
            isIncoming = false,
            isRecordingActive = false,
            ringingStartTimeMs = null,
            callStartTimeMs = null
        )
    }
}
