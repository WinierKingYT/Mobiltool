package com.personaltool.transcription.api

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.model.transcript.Transcript
import com.personaltool.core.model.transcript.TranscriptSegment

enum class TranscriptionComputeTarget(val displayName: String) {
    LOCAL_DEVICE_QUANTIZED("Local On-Device (TFLite Whisper-Tiny)"),
    DESKTOP_GPU_OFFLOAD("Desktop Bridge GPU (Fast & Zero Phone Battery)")
}

data class TranscriptionRequest(
    val targetId: String,
    val audioFilePath: String,
    val language: String = "auto",
    val computeTarget: TranscriptionComputeTarget = TranscriptionComputeTarget.LOCAL_DEVICE_QUANTIZED
)

data class TranscriptionProgress(
    val targetId: String,
    val progressPercent: Int,
    val latestSegment: TranscriptSegment? = null
)

data class ModelStatus(
    val isReady: Boolean,
    val modelName: String,
    val modelSizeBytes: Long,
    val isDownloading: Boolean = false
)

interface TranscriptionEngine {
    val engineName: String

    suspend fun checkModelStatus(): ModelStatus

    suspend fun transcribe(
        request: TranscriptionRequest,
        onProgress: (TranscriptionProgress) -> Unit
    ): AppResult<Transcript>

    suspend fun cancelTranscription(targetId: String): AppResult<Unit>
}
