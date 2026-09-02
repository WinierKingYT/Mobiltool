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
    val isLoudspeakerOn: Boolean,
    val expectedQuality: RecordingQuality,
    val physicalLimitationReason: String
)

object CallCaptureCapabilityDetector {

    fun detectCapability(context: Context): DetailedCaptureCapability {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isSpeakerOn = audioManager?.isSpeakerphoneOn == true

        val isCompanionActive = PrivilegedCompanionClient.isCompanionActive()
        val isOemDirectoryPresent = OemRecordingImporter.isOemRecordingDirectoryPresent()

        val tier = when {
            isCompanionActive -> CallCaptureTier.PRIVILEGED_DIRECT
            isOemDirectoryPresent -> CallCaptureTier.OEM_IMPORT
            else -> CallCaptureTier.UNSUPPORTED_USERSPACE
        }

        val isTwoWaySupported = tier == CallCaptureTier.PRIVILEGED_DIRECT || tier == CallCaptureTier.OEM_IMPORT

        val audioSourceType = when (tier) {
            CallCaptureTier.PRIVILEGED_DIRECT -> "UNIX_SOCKET_ALSA_STREAM"
            CallCaptureTier.OEM_IMPORT -> "OEM_MEDIASTORE_INGESTION"
            else -> "NONE_UNSUPPORTED"
        }

        val expectedQuality = when (tier) {
            CallCaptureTier.PRIVILEGED_DIRECT, CallCaptureTier.OEM_IMPORT -> RecordingQuality.VERIFIED_BIDIRECTIONAL
            else -> RecordingQuality.UNSUPPORTED
        }

        val limitationReason = when (tier) {
            CallCaptureTier.PRIVILEGED_DIRECT -> "Privileged companion daemon active: Capturing hardware-level ALSA dual streams."
            CallCaptureTier.OEM_IMPORT -> "OEM call recording active: Ingesting manufacturer dual-channel recording."
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
            isLoudspeakerOn = isSpeakerOn,
            expectedQuality = expectedQuality,
            physicalLimitationReason = limitationReason
        )
    }
}

