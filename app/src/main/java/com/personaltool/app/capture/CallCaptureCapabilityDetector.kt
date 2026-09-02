package com.personaltool.app.capture

import android.content.Context
import android.media.AudioManager
import android.os.Build
import com.personaltool.core.model.call.CallCaptureTier
import com.personaltool.core.model.call.RecordingQuality

data class DetailedCaptureCapability(
    val tier: CallCaptureTier,
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

    fun detectCapability(context: Context): DetailedCaptureCapability {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isSpeakerOn = audioManager?.isSpeakerphoneOn == true

        val isCompanionActive = PrivilegedCompanionClient.isCompanionActive()
        val oemDiscovery = OemRecordingImporter.checkOemDiscoveryState(context)
        val isOemCandidate = oemDiscovery == OemDiscoveryState.OEM_RECORDING_CONFIRMED ||
                oemDiscovery == OemDiscoveryState.OEM_ACCESSIBLE ||
                oemDiscovery == OemDiscoveryState.OEM_CANDIDATE_DETECTED

        val tier = when {
            isCompanionActive -> CallCaptureTier.PRIVILEGED_DIRECT
            isOemCandidate -> CallCaptureTier.OEM_IMPORT
            else -> CallCaptureTier.UNSUPPORTED_USERSPACE
        }

        // Truth invariant: Before physical qualification, candidate capture paths are candidate/unverified
        val isTwoWaySupported = tier == CallCaptureTier.PRIVILEGED_DIRECT || tier == CallCaptureTier.OEM_IMPORT

        val audioSourceType = when (tier) {
            CallCaptureTier.PRIVILEGED_DIRECT -> "UNIX_SOCKET_ALSA_STREAM (UNLINKED CANDIDATE)"
            CallCaptureTier.OEM_IMPORT -> "OEM_MEDIASTORE_INGESTION (CANDIDATE)"
            else -> "NONE_UNSUPPORTED"
        }

        val expectedQuality = when (tier) {
            CallCaptureTier.PRIVILEGED_DIRECT, CallCaptureTier.OEM_IMPORT -> RecordingQuality.MIXED_UNVERIFIED
            else -> RecordingQuality.UNSUPPORTED
        }

        val limitationReason = when (tier) {
            CallCaptureTier.PRIVILEGED_DIRECT -> "Candidate companion daemon: Hardware ALSA stream requires physical link and qualification."
            CallCaptureTier.OEM_IMPORT -> "Candidate OEM call recording: MediaStore ingestion requires 1-2 call feasibility validation."
            else -> {
                if (Build.VERSION.SDK_INT >= 29) {
                    "Android 10+ SELinux & AudioPolicy restriction: Direct voice call downlink is blocked in userspace. System companion or OEM import required."
                } else {
                    "AOSP Userspace: Standard microphone captures uplink audio only. Bidirectional hardware tap unavailable."
                }
            }
        }

        return DetailedCaptureCapability(
            tier = tier,
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
