package com.personaltool.call.capture.api

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.model.call.CallLifecycleState
import com.personaltool.core.model.call.RecordingQuality
import kotlinx.coroutines.flow.StateFlow

data class CaptureCapability(
    val isSupported: Boolean,
    val captureEngineType: String,
    val chipFamily: String,
    val requiresSystemPrivilege: Boolean,
    val supportedAudioFormats: List<String>,
    val notes: String
)

data class ActiveCaptureState(
    val callId: String,
    val state: CallLifecycleState,
    val durationSeconds: Long,
    val currentQualityEstimate: RecordingQuality,
    val outputFilePath: String?
)

data class FinalizedRecording(
    val callId: String,
    val audioFilePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val quality: RecordingQuality
)

interface CaptureEngine {
    val engineName: String
    val activeState: StateFlow<ActiveCaptureState?>

    suspend fun checkCapability(): CaptureCapability

    suspend fun startCapture(callId: String, phoneNumber: String): AppResult<Unit>

    suspend fun stopCapture(callId: String): AppResult<FinalizedRecording>

    suspend fun cancelCapture(callId: String): AppResult<Unit>
}
