package com.personaltool.core.model.call

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecordingQualityTest {

    @Test
    fun verifiedBidirectional_isTranscribable_andDoesNotRequireWarning() {
        val quality = RecordingQuality.VERIFIED_BIDIRECTIONAL
        assertThat(quality.isTranscribable).isTrue()
        assertThat(quality.requiresWarning).isFalse()
    }

    @Test
    fun silentAndCorrupt_areNotTranscribable() {
        assertThat(RecordingQuality.SILENT.isTranscribable).isFalse()
        assertThat(RecordingQuality.CORRUPT.isTranscribable).isFalse()
        assertThat(RecordingQuality.UNSUPPORTED.isTranscribable).isFalse()
    }

    @Test
    fun oneSidedAndMixed_requireWarningBeforeTranscription() {
        assertThat(RecordingQuality.ONE_SIDED.requiresWarning).isTrue()
        assertThat(RecordingQuality.MIXED_UNVERIFIED.requiresWarning).isTrue()
        assertThat(RecordingQuality.UNKNOWN.requiresWarning).isTrue()
    }

    @Test
    fun callSession_instantiation_holdsExpectedValues() {
        val session = CallSession(
            id = "test-id",
            phoneNumber = "+905001112233",
            contactName = "Test Contact",
            direction = CallDirection.INCOMING,
            startTimeEpochMs = 1700000000000L,
            durationMs = 45000L,
            recordingQuality = RecordingQuality.VERIFIED_BIDIRECTIONAL
        )

        assertThat(session.id).isEqualTo("test-id")
        assertThat(session.phoneNumber).isEqualTo("+905001112233")
        assertThat(session.recordingQuality).isEqualTo(RecordingQuality.VERIFIED_BIDIRECTIONAL)
        assertThat(session.durationMs).isEqualTo(45000L)
    }
}
