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
    val callStartTimeMs: Long?
)

object CallSessionTracker {

    private val _snapshot = MutableStateFlow(
        ActiveCallSnapshot(
            state = TelephonyTrackedState.IDLE,
            phoneNumber = null,
            isIncoming = false,
            isRecordingActive = false,
            callStartTimeMs = null
        )
    )
    val snapshot: StateFlow<ActiveCallSnapshot> = _snapshot.asStateFlow()

    private val recordingInProgress = AtomicBoolean(false)

    @Synchronized
    fun onRinging(incomingNumber: String?): Boolean {
        _snapshot.value = _snapshot.value.copy(
            state = TelephonyTrackedState.RINGING,
            phoneNumber = incomingNumber ?: _snapshot.value.phoneNumber,
            isIncoming = true
        )
        return false // Do not start recording on ringing
    }

    @Synchronized
    fun onOffhook(dialedNumber: String?): Boolean {
        val current = _snapshot.value
        val number = dialedNumber ?: current.phoneNumber ?: "Unknown"
        val isFirstTransition = recordingInProgress.compareAndSet(false, true)

        _snapshot.value = ActiveCallSnapshot(
            state = TelephonyTrackedState.OFFHOOK,
            phoneNumber = number,
            isIncoming = current.isIncoming,
            isRecordingActive = isFirstTransition,
            callStartTimeMs = if (isFirstTransition) System.currentTimeMillis() else current.callStartTimeMs
        )

        return isFirstTransition // Return true to trigger service start
    }

    @Synchronized
    fun onIdle(): Boolean {
        val wasRecording = recordingInProgress.getAndSet(false)

        _snapshot.value = ActiveCallSnapshot(
            state = TelephonyTrackedState.IDLE,
            phoneNumber = null,
            isIncoming = false,
            isRecordingActive = false,
            callStartTimeMs = null
        )

        return wasRecording // Return true only if a recording session was active and needs stopping
    }

    fun isRecording(): Boolean = recordingInProgress.get()
}
