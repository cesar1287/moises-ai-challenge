package com.example.moisessongs.data.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.moisessongs.domain.Song
import com.example.moisessongs.domain.playback.PlaybackState
import com.example.moisessongs.domain.playback.PreviewPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class Media3PreviewPlayer(context: Context, private val scope: CoroutineScope) : PreviewPlayer {
    private val application = context.applicationContext
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state = mutableState.asStateFlow()
    private var engine: ExoPlayer? = null
    private var ticker: Job? = null

    private fun player(): ExoPlayer = engine ?: ExoPlayer.Builder(application)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        .setHandleAudioBecomingNoisy(true).build().also { player ->
            engine = player
            player.repeatMode = if (state.value.repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            player.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    updateState(player)
                    if (player.isPlaying && ticker?.isActive != true) {
                        ticker = scope.launch {
                            while (isActive && player.isPlaying) { updateState(player); delay(250.milliseconds) }
                        }
                    } else if (!player.isPlaying) ticker?.cancel()
                }
                override fun onPlayerError(error: PlaybackException) {
                    mutableState.update { it.copy(failed = true, buffering = false, playing = false) }
                }
            })
        }

    override fun prepare(song: Song, autoPlay: Boolean) {
        engine?.stop()
        engine?.clearMediaItems()
        mutableState.value = PlaybackState(song = song, repeat = state.value.repeat)
        song.previewUrl?.let { url ->
            player().apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = autoPlay
            }
        }
    }

    override fun play() {
        val song = state.value.song ?: return
        if (song.previewUrl == null) return
        if (state.value.failed) {
            prepare(song, autoPlay = true)
        } else {
            val player = player()
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    override fun togglePlayback() {
        val player = engine ?: return
        if (player.isPlaying || player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) player.pause()
        else play()
    }

    override fun seekTo(positionMs: Long) {
        val player = engine ?: return
        if (state.value.durationMs > 0) {
            player.seekTo(positionMs.coerceIn(0, state.value.durationMs))
            updateState(player)
        }
    }

    override fun toggleRepeat() {
        val enabled = !state.value.repeat
        engine?.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        mutableState.update { it.copy(repeat = enabled) }
    }
    override fun pause() { engine?.pause() }
    override fun release() { ticker?.cancel(); engine?.release(); engine = null }

    private fun updateState(player: Player) {
        mutableState.update { it.copy(
            playing = player.isPlaying,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
            ended = player.playbackState == Player.STATE_ENDED,
        ) }
    }
}
