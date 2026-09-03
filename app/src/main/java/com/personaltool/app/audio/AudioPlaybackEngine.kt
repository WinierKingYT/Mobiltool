package com.personaltool.app.audio

interface AudioPlaybackEngine {
    fun prepare(
        filePath: String,
        onPrepared: (durationMs: Long) -> Unit,
        onError: (errorMessage: String) -> Unit,
        onCompletion: () -> Unit
    )
    fun start(): Boolean
    fun pause(): Boolean
    fun seekTo(positionMs: Long): Boolean
    fun setPlaybackSpeed(speed: Float): Boolean
    fun getCurrentPosition(): Long
    fun getDuration(): Long
    fun release()
}
