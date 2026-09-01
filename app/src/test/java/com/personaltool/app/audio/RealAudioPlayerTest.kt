package com.personaltool.app.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RealAudioPlayerTest {

    @Test
    fun audioPlayerState_calculatesAccurateProgressPercent() {
        val state = AudioPlayerState(
            isPlaying = true,
            currentPositionMs = 30000L,
            durationMs = 60000L
        )

        assertThat(state.progressPercent).isEqualTo(0.5f)
    }

    @Test
    fun audioPlayerState_handlesZeroDuration_safely() {
        val state = AudioPlayerState(
            isPlaying = false,
            currentPositionMs = 0L,
            durationMs = 0L
        )

        assertThat(state.progressPercent).isEqualTo(0f)
    }

    @Test
    fun audioPlayerState_clampsOverflowingPosition_to100Percent() {
        val state = AudioPlayerState(
            isPlaying = true,
            currentPositionMs = 75000L,
            durationMs = 60000L
        )

        assertThat(state.progressPercent).isEqualTo(1.0f)
    }
}
