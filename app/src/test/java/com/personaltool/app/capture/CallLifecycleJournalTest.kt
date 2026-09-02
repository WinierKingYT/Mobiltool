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
    fun activeCallLifecycleEntry_malformedData_returnsNullSafely() {
        val badData = "corrupt_single_line"
        val result = ActiveCallLifecycleEntry.fromSerializedString(badData)
        assertThat(result).isNull()
    }
}
