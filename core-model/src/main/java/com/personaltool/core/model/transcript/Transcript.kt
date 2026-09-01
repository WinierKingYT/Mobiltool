package com.personaltool.core.model.transcript

enum class TranscriptStatus {
    NONE,
    REQUESTED,
    QUEUED,
    RUNNING,
    READY,
    FAILED,
    CANCELLED
}

data class TranscriptSegment(
    val id: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val speakerTag: String? = null,
    val confidence: Float = 1.0f
)

data class Transcript(
    val id: String,
    val targetId: String, // CallSession ID or MediaItem ID
    val language: String = "auto",
    val status: TranscriptStatus = TranscriptStatus.NONE,
    val segments: List<TranscriptSegment> = emptyList(),
    val confidence: Float = 1.0f,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
