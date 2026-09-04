package com.personaltool.app.video

import com.personaltool.app.viewmodel.VaultFileAvailabilityInspector
import com.personaltool.app.viewmodel.VaultFileState
import com.personaltool.core.model.media.DownloadStatus
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

data class VideoPlaybackSource(
    val targetId: String,
    val title: String,
    val filePath: String?,
    val expectedSizeBytes: Long = 0L,
    val downloadStatus: DownloadStatus = DownloadStatus.COMPLETED
)

class VideoPlaybackController(
    private val engineFactory: () -> VideoPlaybackEngine,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    companion object {
        const val REWIND_CONFIRM_TOLERANCE_MS = 250L
    }

    private var engine: VideoPlaybackEngine? = null
    private var progressJob: Job? = null
    private var sessionGeneration: Long = 0L
    private var pendingPlayOnRewind: Boolean = false

    val currentEngine: VideoPlaybackEngine?
        get() = engine

    private val _state = MutableStateFlow(VideoPlaybackState())
    val state: StateFlow<VideoPlaybackState> = _state.asStateFlow()

    fun openVideo(
        targetId: String,
        title: String,
        filePath: String?,
        expectedSizeBytes: Long = 0L,
        downloadStatus: DownloadStatus = DownloadStatus.COMPLETED
    ) {
        openVideo(
            VideoPlaybackSource(
                targetId = targetId,
                title = title,
                filePath = filePath,
                expectedSizeBytes = expectedSizeBytes,
                downloadStatus = downloadStatus
            )
        )
    }

    fun openVideo(source: VideoPlaybackSource) {
        // Idempotently release any active engine session and increment generation
        releaseCurrentEngine()

        if (source.filePath.isNullOrBlank()) {
            sessionGeneration++
            _state.value = VideoPlaybackState(
                phase = VideoPlaybackPhase.ERROR,
                targetId = source.targetId,
                title = source.title,
                errorMessage = "Video file path is missing or blank"
            )
            return
        }

        if (source.downloadStatus != DownloadStatus.COMPLETED) {
            sessionGeneration++
            _state.value = VideoPlaybackState(
                phase = VideoPlaybackPhase.ERROR,
                targetId = source.targetId,
                title = source.title,
                filePath = source.filePath,
                errorMessage = "Video is not ready for playback (status: ${source.downloadStatus})"
            )
            return
        }

        val file = File(source.filePath)
        val fileState = VaultFileAvailabilityInspector.inspectMediaFile(
            file = file,
            expectedSizeBytes = source.expectedSizeBytes,
            downloadStatus = source.downloadStatus
        )

        if (fileState != VaultFileState.AVAILABLE) {
            sessionGeneration++
            val errorMsg = when (fileState) {
                VaultFileState.NOT_READY -> "Video is not ready for playback (status: ${source.downloadStatus})"
                VaultFileState.MISSING -> "Video file not found: ${file.absolutePath}"
                VaultFileState.UNREADABLE -> "Video file is unreadable: ${file.absolutePath}"
                VaultFileState.SIZE_MISMATCH -> "Video file size mismatch (expected: ${source.expectedSizeBytes} bytes, actual: ${file.length()} bytes)"
                VaultFileState.INVALID_MEDIA -> "Video file is corrupt, incomplete, or not a valid media container: ${file.name}"
                VaultFileState.NO_LOCAL_FILE -> "Video local file path is absent"
                else -> "Video file is not playable (state: ${fileState.name})"
            }
            _state.value = VideoPlaybackState(
                phase = VideoPlaybackPhase.ERROR,
                targetId = source.targetId,
                title = source.title,
                filePath = source.filePath,
                errorMessage = errorMsg
            )
            return
        }

        val currentGen = ++sessionGeneration
        _state.value = VideoPlaybackState(
            phase = VideoPlaybackPhase.LOADING,
            targetId = source.targetId,
            title = source.title,
            filePath = source.filePath
        )

        val newEngine = try {
            engineFactory()
        } catch (e: Exception) {
            _state.value = VideoPlaybackState(
                phase = VideoPlaybackPhase.ERROR,
                targetId = source.targetId,
                title = source.title,
                filePath = source.filePath,
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
                        pendingPlayOnRewind = false
                        stopProgressPolling()
                        currentEngine.release()
                        if (engine === currentEngine) {
                            engine = null
                        }
                        _state.update { current ->
                            current.copy(
                                phase = VideoPlaybackPhase.ERROR,
                                errorMessage = errorMsg
                            )
                        }
                    }
                },
                onCompletion = {
                    if (currentGen == sessionGeneration && engine === currentEngine) {
                        pendingPlayOnRewind = false
                        stopProgressPolling()
                        _state.update { current ->
                            current.copy(
                                phase = VideoPlaybackPhase.COMPLETED,
                                currentPositionMs = current.durationMs
                            )
                        }
                    }
                },
                onActivityChanged = { activity ->
                    if (currentGen == sessionGeneration && engine === currentEngine) {
                        when (activity) {
                            VideoPlaybackActivity.PLAYING -> {
                                _state.update { it.copy(phase = VideoPlaybackPhase.PLAYING) }
                                startProgressPolling(currentGen, currentEngine)
                            }
                            VideoPlaybackActivity.PAUSED -> {
                                stopProgressPolling()
                                _state.update { current ->
                                    when (current.phase) {
                                        VideoPlaybackPhase.PLAYING, VideoPlaybackPhase.LOADING -> {
                                            if (current.durationMs > 0L && current.targetId != null) {
                                                val pos = currentEngine.getCurrentPosition()
                                                current.copy(phase = VideoPlaybackPhase.PAUSED, currentPositionMs = pos)
                                            } else {
                                                current
                                            }
                                        }
                                        VideoPlaybackPhase.READY -> current
                                        VideoPlaybackPhase.COMPLETED -> current
                                        else -> current
                                    }
                                }
                            }
                            VideoPlaybackActivity.BUFFERING -> {
                                stopProgressPolling()
                                _state.update { current ->
                                    if (current.phase == VideoPlaybackPhase.PLAYING) {
                                        current.copy(phase = VideoPlaybackPhase.LOADING)
                                    } else {
                                        current
                                    }
                                }
                            }
                            VideoPlaybackActivity.ENDED -> {
                                stopProgressPolling()
                            }
                        }
                    }
                },
                onPositionDiscontinuity = { confirmedPositionMs ->
                    if (currentGen == sessionGeneration && engine === currentEngine) {
                        if (pendingPlayOnRewind) {
                            if (confirmedPositionMs in 0L..REWIND_CONFIRM_TOLERANCE_MS) {
                                pendingPlayOnRewind = false
                                _state.update { current ->
                                    current.copy(
                                        currentPositionMs = confirmedPositionMs,
                                        phase = VideoPlaybackPhase.READY
                                    )
                                }
                                currentEngine.requestPlay()
                            }
                        } else {
                            _state.update { current ->
                                current.copy(
                                    currentPositionMs = confirmedPositionMs,
                                    phase = if (current.phase == VideoPlaybackPhase.COMPLETED) VideoPlaybackPhase.READY else current.phase
                                )
                            }
                        }
                    }
                },
                onVideoMetadataChanged = { width, height ->
                    if (currentGen == sessionGeneration && engine === currentEngine) {
                        _state.update { current ->
                            current.copy(videoWidth = width, videoHeight = height)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            if (currentGen == sessionGeneration) {
                pendingPlayOnRewind = false
                releaseCurrentEngine()
                _state.value = VideoPlaybackState(
                    phase = VideoPlaybackPhase.ERROR,
                    targetId = source.targetId,
                    title = source.title,
                    filePath = source.filePath,
                    errorMessage = "Failed to prepare video: ${e.message}"
                )
            }
        }
    }

    fun play(): Boolean {
        val current = _state.value
        if (!current.canPlay) return false

        val activeEngine = engine ?: return false

        // If completed, request rewind to 0 before starting; wait for confirmed rewind before issuing play
        if (current.phase == VideoPlaybackPhase.COMPLETED) {
            pendingPlayOnRewind = true
            val rewindRequested = activeEngine.requestSeek(0L)
            if (!rewindRequested) {
                pendingPlayOnRewind = false
                return false
            }
            return true
        }

        return activeEngine.requestPlay()
    }

    fun pause() {
        val current = _state.value
        if (current.phase != VideoPlaybackPhase.PLAYING) return

        val activeEngine = engine ?: return
        activeEngine.requestPause()
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
        return activeEngine.requestSeek(clamped)
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
        pendingPlayOnRewind = false
        sessionGeneration++
        releaseCurrentEngine()
        _state.value = VideoPlaybackState(phase = VideoPlaybackPhase.IDLE)
    }

    private fun releaseCurrentEngine() {
        sessionGeneration++
        pendingPlayOnRewind = false
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
