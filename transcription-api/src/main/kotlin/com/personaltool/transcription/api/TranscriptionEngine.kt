package com.personaltool.transcription.api

import com.personaltool.core.common.result.AppResult
import com.personaltool.core.model.transcript.Transcript

enum class TranscriptionComputeTarget {
    ON_DEVICE_CPU,
    ON_DEVICE_NNAPI,
    DESKTOP_GPU_OFFLOAD
}

data class TranscriptionRequest(
    val targetId: String,
    val audioFilePath: String,
    val language: String = "auto",
    val prompt: String? = null,
    val computeTarget: TranscriptionComputeTarget = TranscriptionComputeTarget.ON_DEVICE_CPU
)

data class TranscriptionProgress(
    val targetId: String,
    val percent: Int,
    val currentAudioTimestampMs: Long,
    val totalAudioDurationMs: Long,
    val currentSegmentText: String? = null
)

data class ModelStatus(
    val isReady: Boolean,
    val modelName: String,
    val modelSizeBytes: Long = 0L,
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
