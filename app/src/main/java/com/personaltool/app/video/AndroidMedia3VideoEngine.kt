package com.personaltool.app.video

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

class AndroidMedia3VideoEngine(
    private val context: Context
) : VideoPlaybackEngine {

    private var exoPlayer: ExoPlayer? = null
    private var durationMs: Long = 0L
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var isPrepared = false

    override val playerInstance: Any?
        get() = exoPlayer

    override fun prepare(
        filePath: String,
        onPrepared: (durationMs: Long, width: Int, height: Int) -> Unit,
        onError: (errorMessage: String) -> Unit,
        onCompletion: () -> Unit,
        onActivityChanged: (activity: VideoPlaybackActivity) -> Unit,
        onPositionDiscontinuity: (confirmedPositionMs: Long) -> Unit,
        onVideoMetadataChanged: (width: Int, height: Int) -> Unit
    ) {
        release()

        val file = File(filePath)
        if (!file.exists() || !file.isFile || !file.canRead()) {
            onError("Cannot access video file: $filePath")
            return
        }

        var localPlayer: ExoPlayer? = null
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()

            val player = ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, true)
                .build()
            localPlayer = player

            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            if (!isPrepared) {
                                isPrepared = true
                                val dur = player.duration.coerceAtLeast(1L)
                                durationMs = dur
                                onPrepared(dur, videoWidth, videoHeight)
                            }
                            if (!player.isPlaying) {
                                onActivityChanged(VideoPlaybackActivity.PAUSED)
                            }
                        }
                        Player.STATE_BUFFERING -> {
                            onActivityChanged(VideoPlaybackActivity.BUFFERING)
                        }
                        Player.STATE_ENDED -> {
                            onActivityChanged(VideoPlaybackActivity.ENDED)
                            onCompletion()
                        }
                        Player.STATE_IDLE -> {}
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        onActivityChanged(VideoPlaybackActivity.PLAYING)
                    } else {
                        when (player.playbackState) {
                            Player.STATE_BUFFERING -> onActivityChanged(VideoPlaybackActivity.BUFFERING)
                            Player.STATE_ENDED -> onActivityChanged(VideoPlaybackActivity.ENDED)
                            else -> onActivityChanged(VideoPlaybackActivity.PAUSED)
                        }
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    val confirmedMs = newPosition.positionMs.coerceAtLeast(0L)
                    onPositionDiscontinuity(confirmedMs)
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        videoWidth = videoSize.width
                        videoHeight = videoSize.height
                        if (isPrepared) {
                            onVideoMetadataChanged(videoWidth, videoHeight)
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    onError("Video playback error: ${error.message} (${error.errorCodeName})")
                }
            })

            val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
            player.setMediaItem(mediaItem)
            player.prepare()

            // Setup succeeded: transfer ownership to field
            exoPlayer = localPlayer
            localPlayer = null
        } catch (e: Exception) {
            try {
                localPlayer?.apply {
                    playWhenReady = false
                    stop()
                    clearMediaItems()
                    release()
                }
            } catch (_: Exception) {
            }
            localPlayer = null
            exoPlayer = null
            onError("Failed to setup video player: ${e.message}")
        }
    }

    override fun requestPlay(): Boolean {
        val player = exoPlayer ?: return false
        return try {
            player.playWhenReady = true
            player.play()
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun requestPause(): Boolean {
        val player = exoPlayer ?: return false
        return try {
            player.pause()
            player.playWhenReady = false
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun requestSeek(positionMs: Long): Boolean {
        val player = exoPlayer ?: return false
        val target = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        return try {
            player.seekTo(target)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun setPlaybackSpeed(speed: Float): Boolean {
        val player = exoPlayer ?: return false
        val clamped = speed.coerceIn(0.5f, 2.0f)
        return try {
            player.setPlaybackSpeed(clamped)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun getCurrentPosition(): Long {
        val player = exoPlayer ?: return 0L
        return try {
            player.currentPosition.coerceAtLeast(0L)
        } catch (e: Exception) {
            0L
        }
    }

    override fun getDuration(): Long {
        val player = exoPlayer ?: return durationMs
        return try {
            val dur = player.duration
            if (dur > 0L) {
                durationMs = dur
                dur
            } else {
                durationMs
            }
        } catch (e: Exception) {
            durationMs
        }
    }

    override fun release() {
        isPrepared = false
        durationMs = 0L
        videoWidth = 0
        videoHeight = 0
        try {
            exoPlayer?.apply {
                playWhenReady = false
                stop()
                clearMediaItems()
                release()
            }
        } catch (_: Exception) {
        } finally {
            exoPlayer = null
        }
    }
}
