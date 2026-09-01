package com.personaltool.core.model.call

enum class CallCaptureTier(val displayName: String) {
    TIER_1_STANDARD_USERSPACE("Standard User-Space (Loudspeaker/InCall)"),
    TIER_2_SYSTEM_COMPANION("System/Root Privileged Direct Audio")
}

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
    REJECTED,
    LOCAL_AMBIENT_MEMO
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
    val captureTier: CallCaptureTier = CallCaptureTier.TIER_1_STANDARD_USERSPACE,
    val isLoudspeakerActive: Boolean = false,
    val audioFilePath: String? = null,
    val fileSizeBytes: Long = 0L,
    val hasTranscript: Boolean = false,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
