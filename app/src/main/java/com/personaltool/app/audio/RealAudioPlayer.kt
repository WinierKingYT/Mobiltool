package com.personaltool.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

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

class RealAudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(AudioPlayerState())
    val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    fun loadAndPlay(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            _state.value = AudioPlayerState(errorMessage = "File not found: $filePath")
            return
        }

        stop()

        runCatching {
            val player = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(file))
                prepare()
                start()
            }
            mediaPlayer = player

            val duration = player.duration.toLong().coerceAtLeast(1L)
            _state.value = AudioPlayerState(
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = duration,
                activeFilePath = filePath
            )

            player.setOnCompletionListener {
                _state.value = _state.value.copy(isPlaying = false, currentPositionMs = duration)
                progressJob?.cancel()
            }

            startProgressPolling()
        }.onFailure { err ->
            _state.value = AudioPlayerState(errorMessage = err.message)
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _state.value = _state.value.copy(isPlaying = false)
            progressJob?.cancel()
        } else {
            player.start()
            _state.value = _state.value.copy(isPlaying = true)
            startProgressPolling()
        }
    }

    fun seekToPercent(percent: Float) {
        val player = mediaPlayer ?: return
        val duration = _state.value.durationMs
        val targetMs = (percent.coerceIn(0f, 1f) * duration).toInt()
        player.seekTo(targetMs)
        _state.value = _state.value.copy(currentPositionMs = targetMs.toLong())
    }

    fun seekToMs(positionMs: Long) {
        val player = mediaPlayer ?: return
        player.seekTo(positionMs.toInt())
        _state.value = _state.value.copy(currentPositionMs = positionMs)
    }

    fun setSpeed(speed: Float) {
        val player = mediaPlayer ?: return
        runCatching {
            player.playbackParams = player.playbackParams.setSpeed(speed)
            _state.value = _state.value.copy(playbackSpeed = speed)
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        runCatching {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        }
        mediaPlayer = null
        _state.value = _state.value.copy(isPlaying = false)
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        _state.value = _state.value.copy(
                            currentPositionMs = player.currentPosition.toLong(),
                            durationMs = player.duration.toLong().coerceAtLeast(1L)
                        )
                    }
                }
                delay(100)
            }
        }
    }
}
