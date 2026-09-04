package com.personaltool.app.video

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

class VideoPlaybackController(
    private val engineFactory: () -> VideoPlaybackEngine,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private var engine: VideoPlaybackEngine? = null
    private var progressJob: Job? = null
    private var sessionGeneration: Long = 0L

    val currentEngine: VideoPlaybackEngine?
        get() = engine

    private val _state = MutableStateFlow(VideoPlaybackState())
    val state: StateFlow<VideoPlaybackState> = _state.asStateFlow()

    fun openVideo(targetId: String, title: String, filePath: String?) {
        // Idempotently release any active engine session and increment generation
        releaseCurrentEngine()

        if (filePath.isNullOrBlank()) {
            sessionGeneration++
            _state.value = VideoPlaybackState(
                phase = VideoPlaybackPhase.ERROR,
                targetId = targetId,
                title = title,
                errorMessage = "Video file path is missing or blank"
            )
            return
        }

        val file = File(filePath)
        if (!file.exists()) {
            sessionGeneration++
            _state.value = VideoPlaybackState(
                phase = VideoPlaybackPhase.ERROR,
                targetId = targetId,
                title = title,
                filePath = filePath,
                errorMessage = "Video file not found: $filePath"
            )
            return
        }

        if (!file.isFile || !file.canRead()) {
            sessionGeneration++
            _state.value = VideoPlaybackState(
                phase = VideoPlaybackPhase.ERROR,
                targetId = targetId,
                title = title,
                filePath = filePath,
                errorMessage = "Video file is unreadable: $filePath"
            )
            return
        }

        if (file.name.endsWith(".part", ignoreCase = true) || file.name.endsWith(".tmp", ignoreCase = true)) {
            sessionGeneration++
            _state.value = VideoPlaybackState(
                phase = VideoPlaybackPhase.ERROR,
                targetId = targetId,
                title = title,
                filePath = filePath,
                errorMessage = "Incomplete video download cannot be played: ${file.name}"
            )
            return
        }

        val currentGen = ++sessionGeneration
        _state.value = VideoPlaybackState(
            phase = VideoPlaybackPhase.LOADING,
            targetId = targetId,
            title = title,
            filePath = filePath
        )

        val newEngine = try {
            engineFactory()
        } catch (e: Exception) {
            _state.value = VideoPlaybackState(
                phase = VideoPlaybackPhase.ERROR,
                targetId = targetId,
                title = title,
                filePath = filePath,
                errorMessage = "Failed to instantiate video player: ${e.message}"
            )
            return
        }

        val currentEngine = newEngine
        engine = currentEngine

        try {
            currentEngine.prepare(
                filePath = file.absolutePath,
                onPrepared = { durationMs, width, height ->
                    if (currentGen == sessionGeneration && engine === currentEngine) {
                        val finalDuration = durationMs.coerceAtLeast(1L)
                        _state.update { current ->
                            if (current.phase == VideoPlaybackPhase.LOADING || current.phase == VideoPlaybackPhase.READY) {
                                current.copy(
                                    phase = VideoPlaybackPhase.READY,
                                    durationMs = finalDuration,
                                    videoWidth = width,
                                    videoHeight = height,
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
                                phase = VideoPlaybackPhase.ERROR,
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
                                phase = VideoPlaybackPhase.COMPLETED,
                                currentPositionMs = current.durationMs
                            )
                        }
                        stopProgressPolling()
                    }
                }
            )
        } catch (e: Exception) {
            if (currentGen == sessionGeneration) {
                releaseCurrentEngine()
                _state.value = VideoPlaybackState(
                    phase = VideoPlaybackPhase.ERROR,
                    targetId = targetId,
                    title = title,
                    filePath = filePath,
                    errorMessage = "Failed to prepare video: ${e.message}"
                )
            }
        }
    }

    fun play(): Boolean {
        val current = _state.value
        if (!current.canPlay) return false

        val currentGen = sessionGeneration
        val activeEngine = engine ?: return false

        // If completed, seek back to 0 before restarting; fail closed if rewind fails
        if (current.phase == VideoPlaybackPhase.COMPLETED) {
            val rewound = activeEngine.seekTo(0L)
            if (!rewound) {
                return false
            }
            _state.update { it.copy(currentPositionMs = 0L) }
        }

        val started = activeEngine.start()
        if (started) {
            _state.update { it.copy(phase = VideoPlaybackPhase.PLAYING) }
            startProgressPolling(currentGen, activeEngine)
            return true
        }
        return false
    }

    fun pause() {
        val current = _state.value
        if (current.phase != VideoPlaybackPhase.PLAYING) return

        val activeEngine = engine ?: return
        val paused = activeEngine.pause()
        if (paused) {
            stopProgressPolling()
            val pos = activeEngine.getCurrentPosition()
            _state.update { it.copy(phase = VideoPlaybackPhase.PAUSED, currentPositionMs = pos) }
        }
    }

    fun togglePlayPause() {
        if (_state.value.phase == VideoPlaybackPhase.PLAYING) {
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

    fun seekBy(offsetMs: Long): Boolean {
        val currentPos = _state.value.currentPositionMs
        return seekTo(currentPos + offsetMs)
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
        _state.value = VideoPlaybackState(phase = VideoPlaybackPhase.IDLE)
    }

    private fun releaseCurrentEngine() {
        sessionGeneration++
        stopProgressPolling()
        engine?.release()
        engine = null
    }

    private fun startProgressPolling(gen: Long, activeEngine: VideoPlaybackEngine) {
        stopProgressPolling()
        progressJob = coroutineScope.launch {
            while (isActive) {
                delay(250)
                if (gen == sessionGeneration && engine === activeEngine && _state.value.phase == VideoPlaybackPhase.PLAYING) {
                    val pos = activeEngine.getCurrentPosition()
                    val dur = activeEngine.getDuration().coerceAtLeast(_state.value.durationMs)
                    _state.update { current ->
                        if (current.phase == VideoPlaybackPhase.PLAYING) {
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
