package com.personaltool.app.video

enum class VideoPlaybackPhase {
    IDLE,
    LOADING,
    READY,
    PLAYING,
    PAUSED,
    COMPLETED,
    ERROR
}

data class VideoPlaybackState(
    val phase: VideoPlaybackPhase = VideoPlaybackPhase.IDLE,
    val targetId: String? = null,
    val title: String = "",
    val filePath: String? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val errorMessage: String? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0
) {
    val progressPercent: Float
        get() = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val canPlay: Boolean
        get() = phase == VideoPlaybackPhase.READY || phase == VideoPlaybackPhase.PAUSED || phase == VideoPlaybackPhase.COMPLETED

    val canPause: Boolean
        get() = phase == VideoPlaybackPhase.PLAYING

    val canSeek: Boolean
        get() = durationMs > 0L && (phase == VideoPlaybackPhase.READY || phase == VideoPlaybackPhase.PLAYING || phase == VideoPlaybackPhase.PAUSED || phase == VideoPlaybackPhase.COMPLETED)
}
