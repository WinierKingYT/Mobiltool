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

        // Hard Capability Gate (P1-E03):
        // Standard AOSP Userspace cannot capture bidirectional downlink stream.
        // Privileged Direct Companion or OEM Import must be verified through legitimate IPC/storage.
        val tier = CallCaptureTier.UNSUPPORTED_USERSPACE
        val isTwoWaySupported = false

        val limitationReason = when {
            Build.VERSION.SDK_INT >= 29 -> "Android 10+ SELinux & AudioPolicy restriction: Direct voice call downlink is blocked in userspace. System companion or OEM import required."
            else -> "AOSP Userspace: Standard microphone captures uplink audio only. Bidirectional hardware tap unavailable."
        }

        return DetailedCaptureCapability(
            tier = tier,
            isTwoWaySupported = false,
            audioSourceType = "NONE_UNSUPPORTED",
            chipArchitecture = "${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})",
            rootCompanionDetected = false,
            isLoudspeakerOn = isSpeakerOn,
            expectedQuality = RecordingQuality.UNSUPPORTED,
            physicalLimitationReason = limitationReason
        )
    }
}

