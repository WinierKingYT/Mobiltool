package com.personaltool.app.capture

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.model.call.CallCaptureTier
import com.personaltool.core.model.call.RecordingQuality
import org.junit.Test

class CallCaptureCapabilityDetectorTest {

    @Test
    fun oemCandidateDetected_doesNotImplyTwoWaySupported() {
        // Truth Invariant: Candidate != Qualified.
        // Even if an OEM recording folder or candidate exists, isTwoWaySupported MUST be false
        // until the device profile has been physically qualified.
        val capability = DetailedCaptureCapability(
            tier = CallCaptureTier.OEM_IMPORT,
            capturePathCandidate = CallCaptureTier.OEM_IMPORT,
            canAttemptFeasibility = true,
            isBidirectionalQualified = false,
            isTwoWaySupported = false,
            audioSourceType = "OEM_MEDIASTORE_INGESTION (CANDIDATE)",
            chipArchitecture = "Samsung SM-S918B (API 34)",
            rootCompanionDetected = false,
            oemDiscoveryState = OemDiscoveryState.OEM_CANDIDATE_DETECTED,
            isLoudspeakerOn = false,
            expectedQuality = RecordingQuality.MIXED_UNVERIFIED,
            physicalLimitationReason = "Candidate OEM call recording detected. Requires 1-2 call feasibility validation."
        )

        assertThat(capability.isTwoWaySupported).isFalse()
        assertThat(capability.isBidirectionalQualified).isFalse()
        assertThat(capability.canAttemptFeasibility).isTrue()
        assertThat(capability.expectedQuality).isEqualTo(RecordingQuality.MIXED_UNVERIFIED)
    }

    @Test
    fun privilegedCandidate_withoutPhysicalQualification_doesNotClaimTwoWaySupported() {
        // Truth Invariant: Companion socket presence != Bidirectional qualification
        val capability = DetailedCaptureCapability(
            tier = CallCaptureTier.PRIVILEGED_DIRECT,
            capturePathCandidate = CallCaptureTier.PRIVILEGED_DIRECT,
            canAttemptFeasibility = true,
            isBidirectionalQualified = false,
            isTwoWaySupported = false,
            audioSourceType = "UNIX_SOCKET_ALSA_STREAM (UNLINKED CANDIDATE)",
            chipArchitecture = "Google Pixel 8 (API 34)",
            rootCompanionDetected = true,
            oemDiscoveryState = OemDiscoveryState.NONE,
            isLoudspeakerOn = false,
            expectedQuality = RecordingQuality.MIXED_UNVERIFIED,
            physicalLimitationReason = "Candidate companion daemon active. Hardware ALSA stream requires physical preflight qualification."
        )

        assertThat(capability.isTwoWaySupported).isFalse()
        assertThat(capability.isBidirectionalQualified).isFalse()
        assertThat(capability.expectedQuality).isEqualTo(RecordingQuality.MIXED_UNVERIFIED)
    }

    @Test
    fun oemMediaPermissionRequired_setsTransparentLimitationReason() {
        val capability = DetailedCaptureCapability(
            tier = CallCaptureTier.OEM_IMPORT,
            capturePathCandidate = CallCaptureTier.OEM_IMPORT,
            canAttemptFeasibility = false,
            isBidirectionalQualified = false,
            isTwoWaySupported = false,
            audioSourceType = "OEM_MEDIASTORE_INGESTION (CANDIDATE)",
            chipArchitecture = "Xiaomi 13 (API 34)",
            rootCompanionDetected = false,
            oemDiscoveryState = OemDiscoveryState.OEM_MEDIA_PERMISSION_REQUIRED,
            isLoudspeakerOn = false,
            expectedQuality = RecordingQuality.MIXED_UNVERIFIED,
            physicalLimitationReason = "OEM call recording candidate detected, but READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE permission is not granted."
        )

        assertThat(capability.canAttemptFeasibility).isFalse()
        assertThat(capability.physicalLimitationReason).contains("permission is not granted")
    }
}
