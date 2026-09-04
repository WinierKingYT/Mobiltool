package com.personaltool.app.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personaltool.app.audio.AudioPlaybackController
import com.personaltool.app.audio.AudioPlaybackPhase
import com.personaltool.core.common.result.AppResult
import com.personaltool.core.model.transcript.Transcript
import com.personaltool.core.model.transcript.TranscriptSegment
import com.personaltool.core.model.transcript.TranscriptStatus
import com.personaltool.core.storage.dao.CallDao
import com.personaltool.core.storage.dao.MediaDao
import com.personaltool.core.storage.dao.TranscriptDao
import com.personaltool.core.storage.entity.TranscriptEntity
import com.personaltool.transcription.api.DefaultTranscriptionEngine
import com.personaltool.transcription.api.TranscriptExporter
import com.personaltool.transcription.api.TranscriptionEngine
import com.personaltool.transcription.api.TranscriptionProgress
import com.personaltool.transcription.api.TranscriptionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TranscriptViewerState(
    val isOpen: Boolean = false,
    val targetId: String? = null,
    val targetTitle: String = "",
    val audioFilePath: String? = null,
    val transcript: Transcript? = null,
    val status: TranscriptStatus = TranscriptStatus.NONE,
    val progressPercent: Int = 0,
    val currentPlaybackPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val activeSegmentId: String? = null,
    val exportSuccessMessage: String? = null
)

class TranscriptViewModel(
    private val transcriptDao: TranscriptDao,
    private val callDao: CallDao,
    private val mediaDao: MediaDao,
    private val audioPlaybackController: AudioPlaybackController? = null,
    private val transcriptionEngine: TranscriptionEngine = DefaultTranscriptionEngine(),
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(TranscriptViewerState())
    val uiState: StateFlow<TranscriptViewerState> = _uiState.asStateFlow()

    init {
        // Observe real AudioPlaybackController state (zero synthetic timer, zero independent position authority)
        if (audioPlaybackController != null) {
            scope.launch {
                audioPlaybackController.state.collect { playbackState ->
                    val current = _uiState.value
                    if (current.isOpen && playbackState.targetId == current.targetId) {
                        _uiState.update { state ->
                            state.copy(
                                isPlaying = playbackState.phase == AudioPlaybackPhase.PLAYING,
                                currentPlaybackPositionMs = playbackState.currentPositionMs,
                                totalDurationMs = if (playbackState.durationMs > 0L) playbackState.durationMs else state.totalDurationMs,
                                activeSegmentId = findActiveSegment(state.transcript?.segments, playbackState.currentPositionMs)
                            )
                        }
                    }
                }
            }
        }
    }

    fun openTranscript(
        targetId: String,
        targetTitle: String,
        audioFilePath: String?,
        durationMs: Long
    ) {
        _uiState.update {
            it.copy(
                isOpen = true,
                targetId = targetId,
                targetTitle = targetTitle,
                audioFilePath = audioFilePath,
                totalDurationMs = durationMs,
                currentPlaybackPositionMs = 0L,
                isPlaying = false,
                exportSuccessMessage = null
            )
        }

        // Open real audio controller session if file is specified
        if (!audioFilePath.isNullOrBlank()) {
            audioPlaybackController?.openAudio(targetId, targetTitle, audioFilePath)
        }

        scope.launch {
            val existing = transcriptDao.getTranscriptByTargetId(targetId)
            if (existing != null) {
                val domainTranscript = existing.toDomain()
                _uiState.update {
                    it.copy(
                        transcript = domainTranscript,
                        status = domainTranscript.status
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        transcript = null,
                        status = TranscriptStatus.NONE
                    )
                }
            }
        }
    }

    fun closeTranscript() {
        val current = _uiState.value
        if (audioPlaybackController?.state?.value?.targetId == current.targetId) {
            audioPlaybackController?.release()
        }
        _uiState.update { it.copy(isOpen = false, isPlaying = false) }
    }

    fun requestTranscription() {
        val state = _uiState.value
        val targetId = state.targetId ?: return
        val audioPath = state.audioFilePath ?: ""

        scope.launch {
            _uiState.update {
                it.copy(
                    status = TranscriptStatus.RUNNING,
                    progressPercent = 0
                )
            }

            val request = TranscriptionRequest(
                targetId = targetId,
                audioFilePath = audioPath,
                language = "auto"
            )

            when (val result = transcriptionEngine.transcribe(request) { progress: TranscriptionProgress ->
                _uiState.update { it.copy(progressPercent = progress.percent) }
            }) {
                is AppResult.Success -> {
                    val transcript = result.data
                    // Save to Room DB
                    transcriptDao.insertTranscript(TranscriptEntity.fromDomain(transcript))

                    // Mark hasTranscript in Call or Media record
                    val call = callDao.getCallById(targetId)
                    if (call != null) {
                        callDao.updateCall(call.copy(hasTranscript = true))
                    }
                    val media = mediaDao.getMediaById(targetId)
                    if (media != null) {
                        mediaDao.updateMedia(media.copy(hasTranscript = true))
                    }

                    _uiState.update {
                        it.copy(
                            transcript = transcript,
                            status = TranscriptStatus.READY,
                            progressPercent = 100
                        )
                    }
                }
                is AppResult.Error -> {
                    _uiState.update {
                        it.copy(status = TranscriptStatus.FAILED)
                    }
                }
                AppResult.Loading -> {}
            }
        }
    }

    // Real Audio Playback & Seek Synchronizer (Delegates to AudioPlaybackController)
    fun togglePlayPause() {
        val state = _uiState.value
        val audioPath = state.audioFilePath

        if (audioPath.isNullOrBlank()) {
            // Cannot play without an audio file
            return
        }

        val controller = audioPlaybackController ?: return

        if (controller.state.value.targetId != state.targetId) {
            controller.openAudio(state.targetId ?: "", state.targetTitle, audioPath)
        }

        controller.togglePlayPause()
    }

    fun seekToPosition(positionMs: Long) {
        val controller = audioPlaybackController ?: return
        val duration = _uiState.value.totalDurationMs
        if (duration <= 0L) return

        val clamped = positionMs.coerceIn(0L, duration)
        controller.seekTo(clamped)
        // Position and activeSegmentId are updated exclusively via controller state flow observer
    }

    fun seekToSegment(segment: TranscriptSegment) {
        val controller = audioPlaybackController ?: return
        val state = _uiState.value
        if (state.audioFilePath.isNullOrBlank()) return

        if (controller.state.value.targetId != state.targetId) {
            controller.openAudio(state.targetId ?: "", state.targetTitle, state.audioFilePath)
        }
        val sought = controller.seekTo(segment.startTimeMs)
        if (sought) {
            controller.play()
        }
    }

    fun findActiveSegment(segments: List<TranscriptSegment>?, positionMs: Long): String? {
        if (segments.isNullOrEmpty()) return null
        return segments.find { positionMs in it.startTimeMs..it.endTimeMs }?.id
    }

    // Export helpers
    fun copyToClipboard(context: Context) {
        val transcript = _uiState.value.transcript ?: return
        val text = TranscriptExporter.exportAsPlainText(transcript)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Transcript", text)
        clipboard.setPrimaryClip(clip)
        _uiState.update { it.copy(exportSuccessMessage = "COPIED TO CLIPBOARD") }
    }

    fun exportAsSrt(context: Context) {
        val transcript = _uiState.value.transcript ?: return
        val srt = TranscriptExporter.exportAsSrt(transcript)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SRT Subtitles", srt)
        clipboard.setPrimaryClip(clip)
        _uiState.update { it.copy(exportSuccessMessage = "SRT EXPORTED TO CLIPBOARD") }
    }

    fun exportAsMarkdown(context: Context) {
        val transcript = _uiState.value.transcript ?: return
        val md = TranscriptExporter.exportAsMarkdown(transcript, _uiState.value.targetTitle)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Markdown Transcript", md)
        clipboard.setPrimaryClip(clip)
        _uiState.update { it.copy(exportSuccessMessage = "MARKDOWN EXPORTED TO CLIPBOARD") }
    }
}
