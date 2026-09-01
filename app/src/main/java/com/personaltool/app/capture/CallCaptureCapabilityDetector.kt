package com.personaltool.app.capture

import android.content.Context
import android.media.AudioManager
import android.os.Build
import com.personaltool.core.model.call.CallCaptureTier
import com.personaltool.core.model.call.RecordingQuality
import java.io.File

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

        val rootCompanionPresent = checkRootCompanionPresent()

        val tier = if (rootCompanionPresent) {
            CallCaptureTier.TIER_2_SYSTEM_COMPANION
        } else {
            CallCaptureTier.TIER_1_STANDARD_USERSPACE
        }

        val isTwoWaySupported = rootCompanionPresent

        val expectedQuality = when {
            rootCompanionPresent -> RecordingQuality.VERIFIED_BIDIRECTIONAL
            isSpeakerOn -> RecordingQuality.MIXED_UNVERIFIED
            else -> RecordingQuality.ONE_SIDED
        }

        val limitationReason = when {
            rootCompanionPresent -> "Root companion active: ALSA direct hardware tap enabled."
            isSpeakerOn -> "Loudspeaker active: Remote audio captured acoustically via microphone."
            Build.VERSION.SDK_INT >= 29 -> "Android 10+ SELinux restriction: Voice call downlink is restricted in userspace."
            else -> "AOSP Userspace: Standard microphone captures uplink audio and ambient sound."
        }

        return DetailedCaptureCapability(
            tier = tier,
            isTwoWaySupported = isTwoWaySupported,
            audioSourceType = if (rootCompanionPresent) "ALSA_SYSTEM_HOOK" else "VOICE_COMMUNICATION_MIC",
            chipArchitecture = "${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})",
            rootCompanionDetected = rootCompanionPresent,
            isLoudspeakerOn = isSpeakerOn,
            expectedQuality = expectedQuality,
            physicalLimitationReason = limitationReason
        )
    }

    private fun checkRootCompanionPresent(): Boolean {
        return runCatching {
            val companionPaths = listOf(
                "/system/bin/mobiltool_companion",
                "/data/local/tmp/mobiltool_companion",
                "/sbin/su",
                "/system/xbin/su"
            )
            companionPaths.any { File(it).exists() }
        }.getOrDefault(false)
    }
}
