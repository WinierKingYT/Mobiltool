package com.personaltool.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import java.io.File

class AndroidMediaPlayerEngine(
    private val context: Context
) : AudioPlaybackEngine {

    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    private var durationMs: Long = 0L

    init {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    override fun prepare(
        filePath: String,
        onPrepared: (durationMs: Long) -> Unit,
        onError: (errorMessage: String) -> Unit,
        onCompletion: () -> Unit
    ) {
        release()

        val file = File(filePath)
        if (!file.exists() || !file.isFile || !file.canRead()) {
            onError("Cannot access file: $filePath")
            return
        }

        var player: MediaPlayer? = null
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, Uri.fromFile(file))

                setOnErrorListener { _, what, extra ->
                    onError("MediaPlayer error: what=$what, extra=$extra")
                    true
                }

                setOnPreparedListener { mp ->
                    val dur = mp.duration.toLong().coerceAtLeast(1L)
                    durationMs = dur
                    onPrepared(dur)
                }

                setOnCompletionListener {
                    abandonAudioFocus()
                    onCompletion()
                }

                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            player?.release()
            mediaPlayer = null
            onError("Failed to setup audio player: ${e.message}")
        }
    }

    override fun start(): Boolean {
        val player = mediaPlayer ?: return false
        if (!requestAudioFocus()) {
            return false
        }
        return try {
            player.start()
            true
        } catch (e: Exception) {
            abandonAudioFocus()
            false
        }
    }

    override fun pause(): Boolean {
        val player = mediaPlayer ?: return false
        return try {
            if (player.isPlaying) {
                player.pause()
            }
            abandonAudioFocus()
            true
        } catch (e: Exception) {
            abandonAudioFocus()
            false
        }
    }

    override fun seekTo(positionMs: Long): Boolean {
        val player = mediaPlayer ?: return false
        val targetMs = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        return try {
            player.seekTo(targetMs.toInt())
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun setPlaybackSpeed(speed: Float): Boolean {
        val player = mediaPlayer ?: return false
        val clamped = speed.coerceIn(0.5f, 2.0f)
        return try {
            val params = try {
                player.playbackParams
            } catch (_: Exception) {
                PlaybackParams()
            }
            params.speed = clamped
            player.playbackParams = params
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun getCurrentPosition(): Long {
        val player = mediaPlayer ?: return 0L
        return try {
            player.currentPosition.toLong().coerceAtLeast(0L)
        } catch (e: Exception) {
            0L
        }
    }

    override fun getDuration(): Long {
        val player = mediaPlayer ?: return durationMs
        return try {
            val dur = player.duration.toLong()
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
        abandonAudioFocus()
        try {
            mediaPlayer?.apply {
                try {
                    if (isPlaying) {
                        stop()
                    }
                } catch (_: Exception) {}
                reset()
                release()
            }
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
            durationMs = 0L
        }
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        if (hasAudioFocus) return true

        return try {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    ) {
                        try {
                            if (mediaPlayer?.isPlaying == true) {
                                mediaPlayer?.pause()
                            }
                        } catch (_: Exception) {}
                        hasAudioFocus = false
                    }
                }
                .build()

            audioFocusRequest = req
            val result = am.requestAudioFocus(req)
            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            hasAudioFocus
        } catch (e: Exception) {
            true // Fallback gracefully if focus request fails
        }
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        val am = audioManager ?: return
        val req = audioFocusRequest
        try {
            if (req != null) {
                am.abandonAudioFocusRequest(req)
            }
        } catch (_: Exception) {
        } finally {
            hasAudioFocus = false
            audioFocusRequest = null
        }
    }
}
