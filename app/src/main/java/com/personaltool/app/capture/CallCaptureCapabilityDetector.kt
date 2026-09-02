package com.personaltool.app.capture

import android.content.Context
import android.media.AudioManager
import android.os.Build
import com.personaltool.core.model.call.CallCaptureTier
import com.personaltool.core.model.call.RecordingQuality

data class DetailedCaptureCapability(
    val tier: CallCaptureTier,
    val capturePathCandidate: CallCaptureTier,
    val canAttemptFeasibility: Boolean,
    val isBidirectionalQualified: Boolean,
    val isTwoWaySupported: Boolean,
    val audioSourceType: String,
    val chipArchitecture: String,
    val rootCompanionDetected: Boolean,
    val oemDiscoveryState: OemDiscoveryState,
    val isLoudspeakerOn: Boolean,
    val expectedQuality: RecordingQuality,
    val physicalLimitationReason: String
)

object CallCaptureCapabilityDetector {

    // Device profile qualification registry (populated after physical verification)
    private val QUALIFIED_OEM_PROFILES = setOf<String>()
    private val QUALIFIED_PRIVILEGED_PROFILES = setOf<String>()

    fun isDeviceProfileQualified(context: Context): Boolean {
        val profileKey = "${Build.MANUFACTURER}_${Build.MODEL}_API${Build.VERSION.SDK_INT}"
        return QUALIFIED_OEM_PROFILES.contains(profileKey) || QUALIFIED_PRIVILEGED_PROFILES.contains(profileKey)
    }

    fun detectCapability(context: Context): DetailedCaptureCapability {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isSpeakerOn = audioManager?.isSpeakerphoneOn == true

        val isCompanionActive = PrivilegedCompanionClient.isCompanionActive()
        val oemDiscovery = OemRecordingImporter.checkOemDiscoveryState(context)
        val isQualified = isDeviceProfileQualified(context)

        val isOemCandidate = oemDiscovery == OemDiscoveryState.OEM_RECORDING_CONFIRMED ||
                oemDiscovery == OemDiscoveryState.OEM_ACCESSIBLE ||
                oemDiscovery == OemDiscoveryState.OEM_CANDIDATE_DETECTED ||
                oemDiscovery == OemDiscoveryState.OEM_MEDIA_PERMISSION_REQUIRED

        val candidateTier = when {
            isCompanionActive -> CallCaptureTier.PRIVILEGED_DIRECT
            isOemCandidate -> CallCaptureTier.OEM_IMPORT
            else -> CallCaptureTier.UNSUPPORTED_USERSPACE
        }

        // Truth Invariant: Candidate != Qualified.
        // isBidirectionalQualified is TRUE only when profile is physically qualified.
        val isBidirectionalQualified = isQualified && (candidateTier == CallCaptureTier.PRIVILEGED_DIRECT || candidateTier == CallCaptureTier.OEM_IMPORT)

        // isTwoWaySupported represents verified bidirectional capability (false until qualified)
        val isTwoWaySupported = isBidirectionalQualified

        // canAttemptFeasibility allows trying candidate ingestion during 1-2 call feasibility test
        val canAttemptFeasibility = (candidateTier == CallCaptureTier.PRIVILEGED_DIRECT && isCompanionActive) ||
                (candidateTier == CallCaptureTier.OEM_IMPORT && oemDiscovery != OemDiscoveryState.OEM_MEDIA_PERMISSION_REQUIRED && oemDiscovery != OemDiscoveryState.NONE)

        val audioSourceType = when (candidateTier) {
            CallCaptureTier.PRIVILEGED_DIRECT -> "UNIX_SOCKET_ALSA_STREAM (UNLINKED CANDIDATE)"
            CallCaptureTier.OEM_IMPORT -> "OEM_MEDIASTORE_INGESTION (CANDIDATE)"
            else -> "NONE_UNSUPPORTED"
        }

        val expectedQuality = when {
            isBidirectionalQualified -> RecordingQuality.VERIFIED_BIDIRECTIONAL
            candidateTier == CallCaptureTier.PRIVILEGED_DIRECT || candidateTier == CallCaptureTier.OEM_IMPORT -> RecordingQuality.MIXED_UNVERIFIED
            else -> RecordingQuality.UNSUPPORTED
        }

        val limitationReason = when {
            oemDiscovery == OemDiscoveryState.OEM_MEDIA_PERMISSION_REQUIRED -> {
                "OEM call recording candidate detected, but READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE permission is not granted."
            }
            candidateTier == CallCaptureTier.PRIVILEGED_DIRECT && !isQualified -> {
                "Candidate companion daemon active. Hardware ALSA stream requires physical preflight qualification."
            }
            candidateTier == CallCaptureTier.OEM_IMPORT && !isQualified -> {
                "Candidate OEM call recording detected (${oemDiscovery.name}). Requires 1-2 call feasibility validation before qualification."
            }
            candidateTier == CallCaptureTier.UNSUPPORTED_USERSPACE -> {
                if (Build.VERSION.SDK_INT >= 29) {
                    "Android 10+ SELinux & AudioPolicy restriction: Direct voice call downlink is blocked in userspace. System companion or OEM import required."
                } else {
                    "AOSP Userspace: Standard microphone captures uplink audio only. Bidirectional hardware tap unavailable."
                }
            }
            else -> "Fully qualified bidirectional call recording active."
        }

        return DetailedCaptureCapability(
            tier = candidateTier,
            capturePathCandidate = candidateTier,
            canAttemptFeasibility = canAttemptFeasibility,
            isBidirectionalQualified = isBidirectionalQualified,
            isTwoWaySupported = isTwoWaySupported,
            audioSourceType = audioSourceType,
            chipArchitecture = "${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})",
            rootCompanionDetected = isCompanionActive,
            oemDiscoveryState = oemDiscovery,
            isLoudspeakerOn = isSpeakerOn,
            expectedQuality = expectedQuality,
            physicalLimitationReason = limitationReason
        )
    }
}
