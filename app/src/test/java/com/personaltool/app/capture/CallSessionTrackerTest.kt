package com.personaltool.app.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class CallSessionTrackerTest {

    @Before
    fun setUp() {
        CallSessionTracker.reset()
    }

    @Test
    fun incomingCall_ringingThenOffhook_transitionsToActiveRecording() {
        // 1. Ringing
        val shouldStartOnRinging = CallSessionTracker.onRinging("+905551234567")
        assertThat(shouldStartOnRinging).isFalse()
        assertThat(CallSessionTracker.snapshot.value.state).isEqualTo(TelephonyTrackedState.RINGING)
        assertThat(CallSessionTracker.snapshot.value.isIncoming).isTrue()
        assertThat(CallSessionTracker.snapshot.value.phoneNumber).isEqualTo("+905551234567")

        // 2. Offhook (Answered)
        val shouldStartOnOffhook = CallSessionTracker.onOffhook("+905551234567")
        assertThat(shouldStartOnOffhook).isTrue()
        assertThat(CallSessionTracker.snapshot.value.state).isEqualTo(TelephonyTrackedState.OFFHOOK)
        assertThat(CallSessionTracker.snapshot.value.isRecordingActive).isTrue()
        assertThat(CallSessionTracker.isRecording()).isTrue()

        // 3. Duplicate Offhook broadcast should be idempotent
        val duplicateOffhook = CallSessionTracker.onOffhook("+905551234567")
        assertThat(duplicateOffhook).isFalse()

        // 4. Hangup
        val termination = CallSessionTracker.onIdle()
        assertThat(termination).isInstanceOf(CallTerminationEvent.ActiveCallEnded::class.java)
        val ended = termination as CallTerminationEvent.ActiveCallEnded
        assertThat(ended.phoneNumber).isEqualTo("+905551234567")
        assertThat(ended.isIncoming).isTrue()
        assertThat(CallSessionTracker.isRecording()).isFalse()
        assertThat(CallSessionTracker.snapshot.value.state).isEqualTo(TelephonyTrackedState.IDLE)
    }

    @Test
    fun incomingCall_ringingThenIdle_triggersMissedCallEvent() {
        // 1. Ringing
        CallSessionTracker.onRinging("+905559876543")
        assertThat(CallSessionTracker.snapshot.value.state).isEqualTo(TelephonyTrackedState.RINGING)

        // 2. Idle without offhook -> Missed Call
        val termination = CallSessionTracker.onIdle()
        assertThat(termination).isInstanceOf(CallTerminationEvent.MissedCall::class.java)
        val missed = termination as CallTerminationEvent.MissedCall
        assertThat(missed.phoneNumber).isEqualTo("+905559876543")
        assertThat(CallSessionTracker.isRecording()).isFalse()
    }

    @Test
    fun outgoingCall_idleThenOffhook_setsDirectionOutgoing() {
        // Direct Offhook from IDLE
        val shouldStart = CallSessionTracker.onOffhook("+905550001122")
        assertThat(shouldStart).isTrue()
        assertThat(CallSessionTracker.snapshot.value.isIncoming).isFalse()
        assertThat(CallSessionTracker.snapshot.value.phoneNumber).isEqualTo("+905550001122")

        val termination = CallSessionTracker.onIdle()
        assertThat(termination).isInstanceOf(CallTerminationEvent.ActiveCallEnded::class.java)
        val ended = termination as CallTerminationEvent.ActiveCallEnded
        assertThat(ended.isIncoming).isFalse()
    }

    @Test
    fun idleWhenAlreadyIdle_returnsNoActiveCall() {
        val termination = CallSessionTracker.onIdle()
        assertThat(termination).isInstanceOf(CallTerminationEvent.NoActiveCall::class.java)
    }
}
