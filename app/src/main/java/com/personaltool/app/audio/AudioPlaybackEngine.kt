package com.personaltool.app.audio

enum class AudioInterruptionReason {
    SYSTEM_FOCUS_LOSS,
    SYSTEM_TRANSIENT_FOCUS_LOSS
}

interface AudioPlaybackEngine {
    fun prepare(
        filePath: String,
        onPrepared: (durationMs: Long) -> Unit,
        onError: (errorMessage: String) -> Unit,
        onCompletion: () -> Unit,
        onInterruption: (reason: AudioInterruptionReason) -> Unit = {}
    )
    fun start(): Boolean
    fun pause(): Boolean
    fun seekTo(positionMs: Long): Boolean
    fun setPlaybackSpeed(speed: Float): Boolean
    fun getCurrentPosition(): Long
    fun getDuration(): Long
    fun release()
}
