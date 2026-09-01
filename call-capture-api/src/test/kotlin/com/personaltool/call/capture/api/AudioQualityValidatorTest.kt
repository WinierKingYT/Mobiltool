package com.personaltool.call.capture.api

import com.google.common.truth.Truth.assertThat
import com.personaltool.core.model.call.RecordingQuality
import org.junit.Test

class AudioQualityValidatorTest {

    @Test
    fun bothActiveTracks_yieldVerifiedBidirectional() {
        val voiceSamplesLocal = ShortArray(1000) { (15000 * Math.sin(it.toDouble())).toInt().toShort() }
        val voiceSamplesRemote = ShortArray(1000) { (12000 * Math.cos(it.toDouble())).toInt().toShort() }

        val quality = AudioQualityValidator.assessQuality(voiceSamplesLocal, voiceSamplesRemote)
        assertThat(quality).isEqualTo(RecordingQuality.VERIFIED_BIDIRECTIONAL)
    }

    @Test
    fun oneSilentTrack_yieldsOneSided() {
        val voiceSamples = ShortArray(1000) { (15000 * Math.sin(it.toDouble())).toInt().toShort() }
        val silentSamples = ShortArray(1000) { 0 }

        val quality = AudioQualityValidator.assessQuality(voiceSamples, silentSamples)
        assertThat(quality).isEqualTo(RecordingQuality.ONE_SIDED)
    }

    @Test
    fun bothSilentTracks_yieldSilent() {
        val silentSamples1 = ShortArray(1000) { 0 }
        val silentSamples2 = ShortArray(1000) { 1 }

        val quality = AudioQualityValidator.assessQuality(silentSamples1, silentSamples2)
        assertThat(quality).isEqualTo(RecordingQuality.SILENT)
    }

    @Test
    fun emptyData_yieldsCorrupt() {
        val quality = AudioQualityValidator.assessQuality(ShortArray(0), ShortArray(0))
        assertThat(quality).isEqualTo(RecordingQuality.CORRUPT)
    }
}
