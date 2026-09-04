package com.personaltool.app.video

interface VideoPlaybackEngine {
    fun prepare(
        filePath: String,
        onPrepared: (durationMs: Long, width: Int, height: Int) -> Unit,
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
    val playerInstance: Any? get() = null
}
