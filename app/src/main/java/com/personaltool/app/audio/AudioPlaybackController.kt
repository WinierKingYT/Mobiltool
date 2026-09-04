package com.personaltool.app.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioPlaybackController(
    private val engineFactory: () -> AudioPlaybackEngine,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private var engine: AudioPlaybackEngine? = null
    private var progressJob: Job? = null
    private var sessionGeneration: Long = 0L

    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    fun openAudio(targetId: String, title: String, filePath: String?) {
        // Idempotently release any active engine session and increment generation
        releaseCurrentEngine()

        if (filePath.isNullOrBlank()) {
            sessionGeneration++
            _state.value = AudioPlaybackState(
                phase = AudioPlaybackPhase.ERROR,
                targetId = targetId,
                title = title,
                errorMessage = "Audio file path is missing or blank"
            )
            return
        }

        val file = File(filePath)
        if (!file.exists()) {
            sessionGeneration++
            _state.value = AudioPlaybackState(
                phase = AudioPlaybackPhase.ERROR,
                targetId = targetId,
                title = title,
                filePath = filePath,
                errorMessage = "Audio file not found: $filePath"
            )
            return
        }

        if (!file.isFile || !file.canRead()) {
            sessionGeneration++
            _state.value = AudioPlaybackState(
                phase = AudioPlaybackPhase.ERROR,
                targetId = targetId,
                title = title,
                filePath = filePath,
                errorMessage = "Audio file is unreadable: $filePath"
            )
            return
        }

        val currentGen = ++sessionGeneration
        _state.value = AudioPlaybackState(
            phase = AudioPlaybackPhase.LOADING,
            targetId = targetId,
            title = title,
            filePath = filePath
        )

        val newEngine = try {
            engineFactory()
        } catch (e: Exception) {
            _state.value = AudioPlaybackState(
                phase = AudioPlaybackPhase.ERROR,
                targetId = targetId,
                title = title,
                filePath = filePath,
                errorMessage = "Failed to instantiate audio player: ${e.message}"
            )
            return
        }

        val currentEngine = newEngine
        engine = currentEngine

        try {
            currentEngine.prepare(
                filePath = file.absolutePath,
                onPrepared = { durationMs ->
                    if (currentGen == sessionGeneration && engine === currentEngine) {
                        val finalDuration = durationMs.coerceAtLeast(1L)
                        _state.update { current ->
                            if (current.phase == AudioPlaybackPhase.LOADING) {
                                current.copy(
                                    phase = AudioPlaybackPhase.READY,
                                    durationMs = finalDuration,
                                    currentPositionMs = 0L
                                )
                            } else current
                        }
                    }
                },
                onError = { errorMsg ->
                    if (currentGen == sessionGeneration && engine === currentEngine) {
                        _state.update { current ->
                            current.copy(
                                phase = AudioPlaybackPhase.ERROR,
                                errorMessage = errorMsg
                            )
                        }
                        stopProgressPolling()
                        currentEngine.release()
                        if (engine === currentEngine) {
                            engine = null
                        }
                    }
                },
                onCompletion = {
                    if (currentGen == sessionGeneration && engine === currentEngine) {
                        _state.update { current ->
                            current.copy(
                                phase = AudioPlaybackPhase.COMPLETED,
                                currentPositionMs = current.durationMs
                            )
                        }
                        stopProgressPolling()
                    }
                },
                onInterruption = { _ ->
                    if (currentGen == sessionGeneration && engine === currentEngine) {
                        if (_state.value.phase == AudioPlaybackPhase.PLAYING) {
                            stopProgressPolling()
                            val pos = currentEngine.getCurrentPosition()
                            _state.update { current ->
                                if (current.phase == AudioPlaybackPhase.PLAYING) {
                                    current.copy(
                                        phase = AudioPlaybackPhase.PAUSED,
                                        currentPositionMs = pos
                                    )
                                } else current
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            if (currentGen == sessionGeneration) {
                releaseCurrentEngine()
                _state.value = AudioPlaybackState(
                    phase = AudioPlaybackPhase.ERROR,
                    targetId = targetId,
                    title = title,
                    filePath = filePath,
                    errorMessage = "Failed to prepare audio: ${e.message}"
                )
            }
        }
    }

    fun play() {
        val current = _state.value
        if (!current.canPlay) return

        val currentGen = sessionGeneration
        val activeEngine = engine ?: return

        // If completed, seek back to 0 before restarting
        if (current.phase == AudioPlaybackPhase.COMPLETED) {
            activeEngine.seekTo(0L)
            _state.update { it.copy(currentPositionMs = 0L) }
        }

        val started = activeEngine.start()
        if (started) {
            _state.update { it.copy(phase = AudioPlaybackPhase.PLAYING) }
            startProgressPolling(currentGen, activeEngine)
        }
    }

    fun pause() {
        val current = _state.value
        if (current.phase != AudioPlaybackPhase.PLAYING) return

        val activeEngine = engine ?: return
        val paused = activeEngine.pause()
        if (paused) {
            stopProgressPolling()
            val pos = activeEngine.getCurrentPosition()
            _state.update { it.copy(phase = AudioPlaybackPhase.PAUSED, currentPositionMs = pos) }
        }
    }

    fun togglePlayPause() {
        if (_state.value.phase == AudioPlaybackPhase.PLAYING) {
            pause()
        } else if (_state.value.canPlay) {
            play()
        }
    }

    fun seekTo(positionMs: Long): Boolean {
        val current = _state.value
        if (!current.canSeek) return false

        val clamped = positionMs.coerceIn(0L, current.durationMs.coerceAtLeast(0L))
        val activeEngine = engine ?: return false
        val sought = activeEngine.seekTo(clamped)
        if (sought) {
            _state.update { it.copy(currentPositionMs = clamped) }
            return true
        }
        return false
    }

    fun seekBy(offsetMs: Long) {
        val currentPos = _state.value.currentPositionMs
        seekTo(currentPos + offsetMs)
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        val activeEngine = engine ?: return
        val updated = activeEngine.setPlaybackSpeed(clamped)
        if (updated) {
            _state.update { it.copy(playbackSpeed = clamped) }
        }
    }

    fun release() {
        sessionGeneration++
        releaseCurrentEngine()
        _state.value = AudioPlaybackState(phase = AudioPlaybackPhase.IDLE)
    }

    private fun releaseCurrentEngine() {
        sessionGeneration++
        stopProgressPolling()
        engine?.release()
        engine = null
    }

    private fun startProgressPolling(gen: Long, activeEngine: AudioPlaybackEngine) {
        stopProgressPolling()
        progressJob = coroutineScope.launch {
            while (isActive) {
                delay(250)
                if (gen == sessionGeneration && engine === activeEngine && _state.value.phase == AudioPlaybackPhase.PLAYING) {
                    val pos = activeEngine.getCurrentPosition()
                    val dur = activeEngine.getDuration().coerceAtLeast(_state.value.durationMs)
                    _state.update { current ->
                        if (current.phase == AudioPlaybackPhase.PLAYING) {
                            current.copy(currentPositionMs = pos, durationMs = dur)
                        } else current
                    }
                } else {
                    break
                }
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }
}
