package com.personaltool.call.capture.api

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.model.call.CallCaptureTier
import com.personaltool.core.model.call.CallLifecycleState
import com.personaltool.core.model.call.RecordingQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class DefaultCaptureEngine(
    override val engineName: String = "TruthfulDualTierCaptureEngine",
    private val storageDir: File = File(System.getProperty("java.io.tmpdir"), "PersonalTool/recordings"),
    private val captureTier: CallCaptureTier = CallCaptureTier.TIER_1_STANDARD_USERSPACE,
    private val isLoudspeakerActive: Boolean = false
) : CaptureEngine {

    private val _activeState = MutableStateFlow<ActiveCaptureState?>(null)
    override val activeState: StateFlow<ActiveCaptureState?> = _activeState.asStateFlow()

    private var captureStartTime = 0L

    override suspend fun checkCapability(): CaptureCapability {
        val hasSystemPrivilege = captureTier == CallCaptureTier.TIER_2_SYSTEM_COMPANION
        return CaptureCapability(
            isSupported = hasSystemPrivilege,
            captureEngineType = if (hasSystemPrivilege) "ROOT_COMPANION_ALSA" else "RESTRICTED_AOSP_USERSPACE",
            chipFamily = "Platform Native",
            requiresSystemPrivilege = true,
            supportedAudioFormats = listOf("m4a", "aac"),
            notes = if (hasSystemPrivilege)
                "System Companion active: Bidirectional hardware stream capture enabled"
            else
                "Android 9+ restriction: Direct call downlink is blocked in userspace. Ambient mic only."
        )
    }

    override suspend fun startCapture(callId: String, phoneNumber: String): AppResult<Unit> {
        // Hard Truth Gate: If hardware capability is unsupported, reject immediately before entering RECORDING state
        val capability = checkCapability()
        if (!capability.isSupported) {
            return AppResult.Error(
                "Bidirectional call capture is blocked on this device (Android 9+ userspace restriction). System Companion / Root privilege required."
            )
        }

        storageDir.mkdirs()
        val outputFile = File(storageDir, "call-$callId.m4a")
        captureStartTime = System.currentTimeMillis()

        _activeState.value = ActiveCaptureState(
            callId = callId,
            state = CallLifecycleState.RECORDING,
            durationSeconds = 0L,
            currentQualityEstimate = RecordingQuality.VERIFIED_BIDIRECTIONAL,
            outputFilePath = outputFile.absolutePath
        )

        return AppResult.Success(Unit)
    }

    override suspend fun stopCapture(callId: String): AppResult<FinalizedRecording> {
        val currentState = _activeState.value
        val durationMs = (System.currentTimeMillis() - captureStartTime).coerceAtLeast(1000L)
        val filePath = currentState?.outputFilePath ?: File(storageDir, "call-$callId.m4a").absolutePath
        val file = File(filePath)

        if (!file.exists() || file.length() == 0L) {
            _activeState.value = null
            return AppResult.Error("Capture failed: No physical audio stream was recorded on disk.")
        }

        val quality = currentState?.currentQualityEstimate ?: RecordingQuality.ONE_SIDED

        _activeState.value = null

        return AppResult.Success(
            FinalizedRecording(
                callId = callId,
                audioFilePath = file.absolutePath,
                durationMs = durationMs,
                fileSizeBytes = file.length(),
                quality = quality
            )
        )
    }

    override suspend fun cancelCapture(callId: String): AppResult<Unit> {
        val currentState = _activeState.value
        if (currentState?.callId == callId) {
            currentState.outputFilePath?.let { File(it).delete() }
        }
        _activeState.value = null
        return AppResult.Success(Unit)
    }
}
