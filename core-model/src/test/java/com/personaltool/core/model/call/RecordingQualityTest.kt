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
            recordingQuality = RecordingQuality.VERIFIED_BIDIRECTIONAL,
            captureTier = CallCaptureTier.PRIVILEGED_DIRECT
        )

        assertThat(session.id).isEqualTo("test-id")
        assertThat(session.phoneNumber).isEqualTo("+905001112233")
        assertThat(session.recordingQuality).isEqualTo(RecordingQuality.VERIFIED_BIDIRECTIONAL)
        assertThat(session.captureTier).isEqualTo(CallCaptureTier.PRIVILEGED_DIRECT)
        assertThat(session.durationMs).isEqualTo(45000L)
    }

    @Test
    fun callCaptureTier_productionPaths_areDistinctAndTruthful() {
        assertThat(CallCaptureTier.PRIVILEGED_DIRECT.displayName).contains("Privileged")
        assertThat(CallCaptureTier.OEM_IMPORT.displayName).contains("OEM Native")
        assertThat(CallCaptureTier.STANDALONE_MEMO.displayName).contains("Microphone Memo")
        assertThat(CallCaptureTier.UNSUPPORTED_USERSPACE.displayName).contains("AOSP Restricted")
    }

    @Test
    fun unrecordedCall_preservesReasonAndUnsupportedQuality() {
        val unrecordedSession = CallSession(
            id = "unrec-1",
            phoneNumber = "+905551234567",
            direction = CallDirection.INCOMING,
            startTimeEpochMs = 1700000000000L,
            durationMs = 0L,
            recordingQuality = RecordingQuality.UNSUPPORTED,
            captureTier = CallCaptureTier.UNSUPPORTED_USERSPACE,
            unrecordedReason = "Android 9+ userspace audio policy restriction (no companion daemon present)."
        )

        assertThat(unrecordedSession.recordingQuality).isEqualTo(RecordingQuality.UNSUPPORTED)
        assertThat(unrecordedSession.recordingQuality.isTranscribable).isFalse()
        assertThat(unrecordedSession.unrecordedReason).contains("Android 9+")
    }
}
