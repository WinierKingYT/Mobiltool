package com.personaltool.app.audio

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AudioPlayerState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val activeFilePath: String? = null,
    val errorMessage: String? = null
) {
    val progressPercent: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

class RealAudioPlayer(
    context: Context,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val controller = AudioPlaybackController(
        engineFactory = { AndroidMediaPlayerEngine(context) },
        coroutineScope = coroutineScope
    )

    private val _state = MutableStateFlow(AudioPlayerState())
    val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    init {
        coroutineScope.launch {
            controller.state.collect { s ->
                _state.value = AudioPlayerState(
                    isPlaying = s.phase == AudioPlaybackPhase.PLAYING,
                    currentPositionMs = s.currentPositionMs,
                    durationMs = s.durationMs,
                    playbackSpeed = s.playbackSpeed,
                    activeFilePath = s.filePath,
                    errorMessage = s.errorMessage
                )
            }
        }
    }

    fun loadAndPlay(filePath: String) {
        controller.openAudio(
            targetId = filePath,
            title = filePath.substringAfterLast('/'),
            filePath = filePath
        )
        controller.play()
    }

    fun togglePlayPause() {
        controller.togglePlayPause()
    }

    fun seekToPercent(percent: Float) {
        val dur = controller.state.value.durationMs
        val target = (percent.coerceIn(0f, 1f) * dur).toLong()
        controller.seekTo(target)
    }

    fun seekToMs(positionMs: Long) {
        controller.seekTo(positionMs)
    }

    fun setSpeed(speed: Float) {
        controller.setSpeed(speed)
    }

    fun stop() {
        controller.release()
    }
}
