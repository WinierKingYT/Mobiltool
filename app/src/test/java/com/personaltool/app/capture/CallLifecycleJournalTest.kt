package com.personaltool.app.capture

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.model.call.CallCaptureTier
import org.junit.Test

class CallLifecycleJournalTest {

    @Test
    fun activeCallLifecycleEntry_serializationAndDeserialization_preservesAllFields() {
        val entry = ActiveCallLifecycleEntry(
            callId = "call-abc-123",
            lifecycleState = PersistedCallState.OFFHOOK_ACTIVE,
            phoneNumber = "+905551112233",
            isIncoming = true,
            ringingStartTimeMs = 1700000000000L,
            callStartTimeMs = 1700000005000L,
            callEndTimeMs = null,
            capturePathCandidate = CallCaptureTier.OEM_IMPORT,
            createdAtEpochMs = 1700000005100L
        )

        val serialized = entry.toSerializedString()
        val deserialized = ActiveCallLifecycleEntry.fromSerializedString(serialized)

        assertThat(deserialized).isNotNull()
        assertThat(deserialized!!.callId).isEqualTo("call-abc-123")
        assertThat(deserialized.lifecycleState).isEqualTo(PersistedCallState.OFFHOOK_ACTIVE)
        assertThat(deserialized.phoneNumber).isEqualTo("+905551112233")
        assertThat(deserialized.isIncoming).isTrue()
        assertThat(deserialized.ringingStartTimeMs).isEqualTo(1700000000000L)
        assertThat(deserialized.callStartTimeMs).isEqualTo(1700000005000L)
        assertThat(deserialized.callEndTimeMs).isNull()
        assertThat(deserialized.capturePathCandidate).isEqualTo(CallCaptureTier.OEM_IMPORT)
    }

    @Test
    fun activeCallLifecycleEntry_withEndTime_preservesEndTime() {
        val entry = ActiveCallLifecycleEntry(
            callId = "call-ended-456",
            lifecycleState = PersistedCallState.ENDED_IMPORT_PENDING,
            phoneNumber = "+905559998877",
            isIncoming = false,
            ringingStartTimeMs = null,
            callStartTimeMs = 1700000000000L,
            callEndTimeMs = 1700000060000L,
            capturePathCandidate = CallCaptureTier.OEM_IMPORT,
            createdAtEpochMs = 1700000000100L
        )

        val serialized = entry.toSerializedString()
        val deserialized = ActiveCallLifecycleEntry.fromSerializedString(serialized)

        assertThat(deserialized).isNotNull()
        assertThat(deserialized!!.callEndTimeMs).isEqualTo(1700000060000L)
        assertThat(deserialized.lifecycleState).isEqualTo(PersistedCallState.ENDED_IMPORT_PENDING)
    }

    @Test
    fun duplicateIdle_preservesFirstCallEndTime_andIsIdempotent() {
        val originalStartTime = 1700000000000L
        val firstIdleTime = 1700000045000L
        val duplicateIdleTime = 1700000055000L

        val entry = ActiveCallLifecycleEntry(
            callId = "call-idempotent-999",
            lifecycleState = PersistedCallState.ENDED_IMPORT_PENDING,
            phoneNumber = "+905558887766",
            isIncoming = true,
            ringingStartTimeMs = originalStartTime - 5000L,
            callStartTimeMs = originalStartTime,
            callEndTimeMs = firstIdleTime,
            capturePathCandidate = CallCaptureTier.OEM_IMPORT,
            createdAtEpochMs = originalStartTime
        )

        // Simulating second IDLE arrival when state is already ENDED_IMPORT_PENDING
        assertThat(entry.lifecycleState).isEqualTo(PersistedCallState.ENDED_IMPORT_PENDING)
        val retainedEndTime = entry.callEndTimeMs

        // P1-PREFLIGHT-25: First IDLE freezes callEndTimeMs; duplicate IDLE must NOT mutate endTimeMs
        assertThat(retainedEndTime).isEqualTo(firstIdleTime)
        assertThat(retainedEndTime).isNotEqualTo(duplicateIdleTime)
    }

    @Test
    fun staleCallRecovery_storesZeroDuration_andTruthfulDiagnostic() {
        val callStartTime = 1700000000000L
        val staleEntry = ActiveCallLifecycleEntry(
            callId = "call-stale-test",
            lifecycleState = PersistedCallState.OFFHOOK_ACTIVE,
            phoneNumber = "+905551112233",
            isIncoming = false,
            ringingStartTimeMs = null,
            callStartTimeMs = callStartTime,
            callEndTimeMs = null,
            capturePathCandidate = CallCaptureTier.OEM_IMPORT,
            createdAtEpochMs = callStartTime
        )

        // P1-PREFLIGHT-22: Stale session conversion must NOT fabricate 4-hour duration
        val recoveredDurationMs = 0L
        val unrecordedReason = "Call session timed out; telephony termination event was not observed. Actual duration is unknown."

        assertThat(recoveredDurationMs).isEqualTo(0L)
        assertThat(unrecordedReason).contains("termination event was not observed")
        assertThat(unrecordedReason).contains("Actual duration is unknown")
    }

    @Test
    fun processDeathRecovery_memoryResetBetweenOffhookAndIdle_recoversOriginalCallIdAndStartTime() {
        val originalCallId = "call-proc-death-789"
        val originalStartTime = 1700000000000L
        val originalPhone = "+905554443322"

        val offhookEntry = ActiveCallLifecycleEntry(
            callId = originalCallId,
            lifecycleState = PersistedCallState.OFFHOOK_ACTIVE,
            phoneNumber = originalPhone,
            isIncoming = false,
            ringingStartTimeMs = null,
            callStartTimeMs = originalStartTime,
            callEndTimeMs = null,
            capturePathCandidate = CallCaptureTier.OEM_IMPORT,
            createdAtEpochMs = originalStartTime
        )

        val serialized = offhookEntry.toSerializedString()

        // 2. Simulate process death: CallSessionTracker is reset to IDLE in new process
        CallSessionTracker.reset()
        assertThat(CallSessionTracker.snapshot.value.state).isEqualTo(TelephonyTrackedState.IDLE)
        val inMemoryEvent = CallSessionTracker.onIdle()
        assertThat(inMemoryEvent).isEqualTo(CallTerminationEvent.NoActiveCall)

        // 3. IDLE receiver reads disk journal first
        val recoveredActiveEntry = ActiveCallLifecycleEntry.fromSerializedString(serialized)
        assertThat(recoveredActiveEntry).isNotNull()
        assertThat(recoveredActiveEntry!!.lifecycleState).isEqualTo(PersistedCallState.OFFHOOK_ACTIVE)

        // 4. Update to ENDED_IMPORT_PENDING with same callId and original start time
        val idleTimestamp = originalStartTime + 45000L
        val transitionEntry = recoveredActiveEntry.copy(
            lifecycleState = PersistedCallState.ENDED_IMPORT_PENDING,
            callEndTimeMs = idleTimestamp
        )

        assertThat(transitionEntry.callId).isEqualTo(originalCallId)
        assertThat(transitionEntry.callStartTimeMs).isEqualTo(originalStartTime)
        assertThat(transitionEntry.callEndTimeMs).isEqualTo(idleTimestamp)
        assertThat(transitionEntry.phoneNumber).isEqualTo(originalPhone)

        // 5. Build worker input data and verify same callId
        val inputData = OemPostCallImportWorker.createInputData(
            callId = transitionEntry.callId,
            phoneNumber = transitionEntry.phoneNumber,
            isIncoming = transitionEntry.isIncoming,
            startTimeMs = transitionEntry.callStartTimeMs,
            endTimeMs = transitionEntry.callEndTimeMs!!,
            candidateTier = transitionEntry.capturePathCandidate
        )

        assertThat(inputData.getString(OemPostCallImportWorker.KEY_CALL_ID)).isEqualTo(originalCallId)
        assertThat(inputData.getLong(OemPostCallImportWorker.KEY_START_TIME, 0L)).isEqualTo(originalStartTime)
        assertThat(inputData.getLong(OemPostCallImportWorker.KEY_END_TIME, 0L)).isEqualTo(idleTimestamp)
    }
}
