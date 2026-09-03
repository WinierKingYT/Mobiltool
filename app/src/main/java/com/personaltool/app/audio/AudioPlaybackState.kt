package com.personaltool.app.audio

enum class AudioPlaybackPhase {
    IDLE,
    LOADING,
    READY,
    PLAYING,
    PAUSED,
    COMPLETED,
    ERROR,
    RELEASED
}

data class AudioPlaybackState(
    val phase: AudioPlaybackPhase = AudioPlaybackPhase.IDLE,
    val targetId: String? = null,
    val title: String = "",
    val filePath: String? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val errorMessage: String? = null
) {
    val progressPercent: Float
        get() = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val canPlay: Boolean
        get() = phase == AudioPlaybackPhase.READY || phase == AudioPlaybackPhase.PAUSED || phase == AudioPlaybackPhase.COMPLETED

    val canPause: Boolean
        get() = phase == AudioPlaybackPhase.PLAYING

    val canSeek: Boolean
        get() = durationMs > 0L && (phase == AudioPlaybackPhase.READY || phase == AudioPlaybackPhase.PLAYING || phase == AudioPlaybackPhase.PAUSED || phase == AudioPlaybackPhase.COMPLETED)
}
