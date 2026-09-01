package com.personaltool.call.capture.api

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.model.call.CallLifecycleState
import com.personaltool.core.model.call.RecordingQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class CaptureCapability(
    val isSupported: Boolean,
    val captureEngineType: String,
    val chipFamily: String? = null,
    val requiresSystemPrivilege: Boolean = false,
    val supportedAudioFormats: List<String> = listOf("m4a", "wav"),
    val notes: String? = null
)

data class ActiveCaptureState(
    val callId: String,
    val state: CallLifecycleState,
    val durationSeconds: Long = 0L,
    val currentQualityEstimate: RecordingQuality = RecordingQuality.UNKNOWN,
    val outputFilePath: String? = null
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
