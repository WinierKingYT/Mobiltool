package com.personaltool.app.capture

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.model.call.CallCaptureTier
import com.personaltool.core.model.call.CallDirection
import org.junit.Test

class CallRecordingJournalTest {

    @Test
    fun journalEntry_serializationAndDeserialization_preservesAllFields() {
        val entry = InFlightCallJournalEntry(
            callId = "test-call-123",
            phoneNumber = "+905551112233",
            direction = CallDirection.INCOMING,
            captureTier = CallCaptureTier.PRIVILEGED_DIRECT,
            startTimeEpochMs = 1700000000000L,
            tempAudioPath = "/data/user/0/com.personaltool.app/files/calls/temp.m4a"
        )

        val serialized = entry.toSerializedString()
        val deserialized = InFlightCallJournalEntry.fromSerializedString(serialized)

        assertThat(deserialized).isNotNull()
        assertThat(deserialized!!.callId).isEqualTo("test-call-123")
        assertThat(deserialized.phoneNumber).isEqualTo("+905551112233")
        assertThat(deserialized.direction).isEqualTo(CallDirection.INCOMING)
        assertThat(deserialized.captureTier).isEqualTo(CallCaptureTier.PRIVILEGED_DIRECT)
        assertThat(deserialized.startTimeEpochMs).isEqualTo(1700000000000L)
        assertThat(deserialized.tempAudioPath).isEqualTo("/data/user/0/com.personaltool.app/files/calls/temp.m4a")
    }

    @Test
    fun journalEntry_malformedData_returnsNullSafely() {
        val badData = "only_one_line"
        val result = InFlightCallJournalEntry.fromSerializedString(badData)
        assertThat(result).isNull()
    }
}
