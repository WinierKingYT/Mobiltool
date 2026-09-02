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
    fun processDeathRecovery_memoryResetBetweenOffhookAndIdle_recoversOriginalCallIdAndStartTime() {
        // 1. Offhook creates and persists entry
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

        // 2. Simulate process death: CallSessionTracker is completely reset to IDLE in new process
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

    @Test
    fun startupReconciliation_activeRecentCall_isNotStale_andMustBePreserved() {
        val now = System.currentTimeMillis()
        val recentCallEntry = ActiveCallLifecycleEntry(
            callId = "call-active-live",
            lifecycleState = PersistedCallState.OFFHOOK_ACTIVE,
            phoneNumber = "+905551113344",
            isIncoming = true,
            ringingStartTimeMs = now - 30000L,
            callStartTimeMs = now - 20000L, // 20 seconds ago (call still active)
            callEndTimeMs = null,
            capturePathCandidate = CallCaptureTier.OEM_IMPORT,
            createdAtEpochMs = now - 20000L
        )

        val ageMs = now - recentCallEntry.callStartTimeMs
        val isStale = ageMs >= CallLifecycleJournal.STALE_CALL_THRESHOLD_MS

        assertThat(isStale).isFalse()
        // Invariant: Recent active call journal must NOT be deleted or turned into interrupted session on app startup
    }

    @Test
    fun startupReconciliation_trulyStaleCall_exceedingThreshold_isIdentifiedAsStale() {
        val now = System.currentTimeMillis()
        val staleCallEntry = ActiveCallLifecycleEntry(
            callId = "call-stale-abandoned",
            lifecycleState = PersistedCallState.OFFHOOK_ACTIVE,
            phoneNumber = "+905551113344",
            isIncoming = true,
            ringingStartTimeMs = now - (5 * 3600 * 1000L),
            callStartTimeMs = now - (5 * 3600 * 1000L), // 5 hours ago!
            callEndTimeMs = null,
            capturePathCandidate = CallCaptureTier.OEM_IMPORT,
            createdAtEpochMs = now - (5 * 3600 * 1000L)
        )

        val ageMs = now - staleCallEntry.callStartTimeMs
        val isStale = ageMs >= CallLifecycleJournal.STALE_CALL_THRESHOLD_MS

        assertThat(isStale).isTrue()
    }
}
