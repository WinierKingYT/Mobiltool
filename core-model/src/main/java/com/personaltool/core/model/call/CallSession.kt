package com.personaltool.core.model.call

enum class CallCaptureTier(val displayName: String) {
    PRIVILEGED_DIRECT("Privileged / System Direct Audio (Tier 2)"),
    OEM_IMPORT("OEM Native Recording Ingestion"),
    STANDALONE_MEMO("Standalone Microphone Memo"),
    UNSUPPORTED_USERSPACE("Standard User-Space (AOSP Restricted - Tier 1)");

    companion object {
        val TIER_1_STANDARD_USERSPACE = UNSUPPORTED_USERSPACE
        val TIER_2_SYSTEM_COMPANION = PRIVILEGED_DIRECT
    }
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
    IDLE,
    DISCOVERED,
    RINGING,
    OFFHOOK,
    ACTIVE,
    ACTIVE_UNRECORDED,
    RECORDING,
    FINALIZING,
    STORED,
    FAILED,
    CORRUPT,
    UNSUPPORTED
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
    val captureTier: CallCaptureTier = CallCaptureTier.UNSUPPORTED_USERSPACE,
    val isLoudspeakerActive: Boolean = false,
    val audioFilePath: String? = null,
    val fileSizeBytes: Long = 0L,
    val hasTranscript: Boolean = false,
    val isFavorite: Boolean = false,
    val unrecordedReason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

