package com.personaltool.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.personaltool.core.model.call.CallDirection
import com.personaltool.core.model.call.CallSession
import com.personaltool.core.model.call.RecordingQuality

@Entity(tableName = "calls")
data class CallEntity(
    @PrimaryKey val id: String,
    val phoneNumber: String,
    val contactName: String?,
    val direction: String,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long?,
    val durationMs: Long,
    val recordingQuality: String,
    val audioFilePath: String?,
    val fileSizeBytes: Long,
    val hasTranscript: Boolean,
    val isFavorite: Boolean,
    val createdAt: Long
) {
    fun toDomain(): CallSession = CallSession(
        id = id,
        phoneNumber = phoneNumber,
        contactName = contactName,
        direction = runCatching { CallDirection.valueOf(direction) }.getOrDefault(CallDirection.INCOMING),
        startTimeEpochMs = startTimeEpochMs,
        endTimeEpochMs = endTimeEpochMs,
        durationMs = durationMs,
        recordingQuality = runCatching { RecordingQuality.valueOf(recordingQuality) }.getOrDefault(RecordingQuality.UNKNOWN),
        audioFilePath = audioFilePath,
        fileSizeBytes = fileSizeBytes,
        hasTranscript = hasTranscript,
        isFavorite = isFavorite,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(session: CallSession): CallEntity = CallEntity(
            id = session.id,
            phoneNumber = session.phoneNumber,
            contactName = session.contactName,
            direction = session.direction.name,
            startTimeEpochMs = session.startTimeEpochMs,
            endTimeEpochMs = session.endTimeEpochMs,
            durationMs = session.durationMs,
            recordingQuality = session.recordingQuality.name,
            audioFilePath = session.audioFilePath,
            fileSizeBytes = session.fileSizeBytes,
            hasTranscript = session.hasTranscript,
            isFavorite = session.isFavorite,
            createdAt = session.createdAt
        )
    }
}
