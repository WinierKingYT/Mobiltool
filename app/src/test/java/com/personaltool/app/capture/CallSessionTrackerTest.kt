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
    fun outgoingCall_idleThenOffhook_setsDirectionOutgoing_andCannotBecomeIncoming() {
        // Direct Offhook from IDLE -> MUST be Outgoing
        val shouldStart = CallSessionTracker.onOffhook("+905550001122")
        assertThat(shouldStart).isTrue()
        assertThat(CallSessionTracker.snapshot.value.isIncoming).isFalse()
        assertThat(CallSessionTracker.snapshot.value.phoneNumber).isEqualTo("+905550001122")

        val termination = CallSessionTracker.onIdle()
        assertThat(termination).isInstanceOf(CallTerminationEvent.ActiveCallEnded::class.java)
        val ended = termination as CallTerminationEvent.ActiveCallEnded
        assertThat(ended.isIncoming).isFalse()
        assertThat(ended.phoneNumber).isEqualTo("+905550001122")
    }

    @Test
    fun outgoingCall_numberUnavailable_setsAccurateFallback_andIncomingFalse() {
        val shouldStart = CallSessionTracker.onOffhook(null)
        assertThat(shouldStart).isTrue()
        assertThat(CallSessionTracker.snapshot.value.isIncoming).isFalse()
        assertThat(CallSessionTracker.snapshot.value.phoneNumber).contains("Outgoing")

        val termination = CallSessionTracker.onIdle()
        val ended = termination as CallTerminationEvent.ActiveCallEnded
        assertThat(ended.isIncoming).isFalse()
        assertThat(ended.phoneNumber).contains("Outgoing")
    }

    @Test
    fun privateIncomingCall_preservesUnknownCaller_andIncomingTrue() {
        CallSessionTracker.onRinging(null)
        assertThat(CallSessionTracker.snapshot.value.isIncoming).isTrue()
        assertThat(CallSessionTracker.snapshot.value.phoneNumber).contains("Unknown")

        CallSessionTracker.onOffhook(null)
        assertThat(CallSessionTracker.snapshot.value.isIncoming).isTrue()

        val termination = CallSessionTracker.onIdle()
        val ended = termination as CallTerminationEvent.ActiveCallEnded
        assertThat(ended.isIncoming).isTrue()
    }

    @Test
    fun duplicateOffhookAndIdle_remainIdempotent() {
        CallSessionTracker.onOffhook("+905551112233")
        val duplicate1 = CallSessionTracker.onOffhook("+905551112233")
        val duplicate2 = CallSessionTracker.onOffhook("+905551112233")
        assertThat(duplicate1).isFalse()
        assertThat(duplicate2).isFalse()

        val termination1 = CallSessionTracker.onIdle()
        val termination2 = CallSessionTracker.onIdle()
        val termination3 = CallSessionTracker.onIdle()

        assertThat(termination1).isInstanceOf(CallTerminationEvent.ActiveCallEnded::class.java)
        assertThat(termination2).isInstanceOf(CallTerminationEvent.NoActiveCall::class.java)
        assertThat(termination3).isInstanceOf(CallTerminationEvent.NoActiveCall::class.java)
    }
}
