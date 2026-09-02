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

        // Truth Gate: Generic root presence (/sbin/su) does NOT imply a functional Mobiltool system companion.
        // Privileged companion daemon protocol is unlinked in P0 baseline; fail-closed.
        val tier = CallCaptureTier.TIER_1_STANDARD_USERSPACE
        val isTwoWaySupported = false

        // Standard microphone and VOICE_COMMUNICATION capture uplink/ambient only; never VERIFIED_BIDIRECTIONAL.
        val expectedQuality = if (isSpeakerOn) {
            RecordingQuality.MIXED_UNVERIFIED
        } else {
            RecordingQuality.ONE_SIDED
        }

        val limitationReason = when {
            isSpeakerOn -> "Loudspeaker active: Remote audio captured acoustically via microphone (unverified acoustic mix)."
            Build.VERSION.SDK_INT >= 29 -> "Android 10+ SELinux restriction: Direct voice call downlink is blocked in userspace. Ambient mic only."
            else -> "AOSP Userspace: Standard microphone captures uplink audio and ambient sound only."
        }

        return DetailedCaptureCapability(
            tier = tier,
            isTwoWaySupported = false,
            audioSourceType = "VOICE_COMMUNICATION_MIC",
            chipArchitecture = "${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})",
            rootCompanionDetected = false,
            isLoudspeakerOn = isSpeakerOn,
            expectedQuality = expectedQuality,
            physicalLimitationReason = limitationReason
        )
    }
}

