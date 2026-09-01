package com.personaltool.call.capture.api

import com.personaltool.core.model.call.RecordingQuality
import kotlin.math.sqrt

object AudioQualityValidator {

    private const val SILENCE_RMS_THRESHOLD = 0.005f
    private const val SPEECH_RMS_THRESHOLD = 0.025f

    fun assessQuality(
        localTrackPcm: ShortArray,
        remoteTrackPcm: ShortArray
    ): RecordingQuality {
        if (localTrackPcm.isEmpty() && remoteTrackPcm.isEmpty()) {
            return RecordingQuality.CORRUPT
        }

        val localRms = calculateRms(localTrackPcm)
        val remoteRms = calculateRms(remoteTrackPcm)

        val hasLocalAudio = localRms >= SPEECH_RMS_THRESHOLD
        val hasRemoteAudio = remoteRms >= SPEECH_RMS_THRESHOLD

        return when {
            hasLocalAudio && hasRemoteAudio -> RecordingQuality.VERIFIED_BIDIRECTIONAL
            hasLocalAudio && remoteRms < SILENCE_RMS_THRESHOLD -> RecordingQuality.ONE_SIDED
            hasRemoteAudio && localRms < SILENCE_RMS_THRESHOLD -> RecordingQuality.ONE_SIDED
            localRms < SILENCE_RMS_THRESHOLD && remoteRms < SILENCE_RMS_THRESHOLD -> RecordingQuality.SILENT
            else -> RecordingQuality.MIXED_UNVERIFIED
        }
    }

    fun assessSingleTrackQuality(pcmData: ShortArray): RecordingQuality {
        if (pcmData.isEmpty()) return RecordingQuality.CORRUPT
        val rms = calculateRms(pcmData)
        return when {
            rms < SILENCE_RMS_THRESHOLD -> RecordingQuality.SILENT
            rms >= SPEECH_RMS_THRESHOLD -> RecordingQuality.MIXED_UNVERIFIED
            else -> RecordingQuality.UNKNOWN
        }
    }

    private fun calculateRms(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f
        var sumSquares = 0.0
        for (sample in samples) {
            val normalized = sample.toDouble() / Short.MAX_VALUE
            sumSquares += normalized * normalized
        }
        return sqrt(sumSquares / samples.size).toFloat()
    }
}
