package com.personaltool.app.video

enum class VideoPlaybackActivity {
    BUFFERING,
    PLAYING,
    PAUSED,
    ENDED
}

interface VideoPlaybackEngine {
    fun prepare(
        filePath: String,
        onPrepared: (durationMs: Long, width: Int, height: Int) -> Unit,
        onError: (errorMessage: String) -> Unit,
        onCompletion: () -> Unit,
        onActivityChanged: (activity: VideoPlaybackActivity) -> Unit = {},
        onPositionDiscontinuity: (confirmedPositionMs: Long) -> Unit = {}
    )
    fun requestPlay(): Boolean
    fun requestPause(): Boolean
    fun requestSeek(positionMs: Long): Boolean
    fun setPlaybackSpeed(speed: Float): Boolean
    fun getCurrentPosition(): Long
    fun getDuration(): Long
    fun release()
    val playerInstance: Any? get() = null
}
