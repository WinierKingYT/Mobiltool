package com.personaltool.core.model.call

enum class RecordingQuality {
    VERIFIED_BIDIRECTIONAL,
    MIXED_UNVERIFIED,
    ONE_SIDED,
    SILENT,
    CORRUPT,
    UNSUPPORTED,
    UNKNOWN;

    val isTranscribable: Boolean
        get() = this != SILENT && this != CORRUPT && this != UNSUPPORTED

    val requiresWarning: Boolean
        get() = this == ONE_SIDED || this == MIXED_UNVERIFIED || this == UNKNOWN
}

enum class CallDirection {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED
}

enum class CallLifecycleState {
    DISCOVERED,
    RINGING,
    ACTIVE,
    ACTIVE_UNRECORDED,
    RECORDING,
    FINALIZING,
    STORED,
    FAILED,
    CORRUPT
}

data class CallSession(
    val id: String,
    val phoneNumber: String,
    val contactName: String? = null,
    val direction: CallDirection,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long? = null,
    val durationMs: Long = 0L,
    val recordingQuality: RecordingQuality = RecordingQuality.UNKNOWN,
    val audioFilePath: String? = null,
    val fileSizeBytes: Long = 0L,
    val hasTranscript: Boolean = false,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
