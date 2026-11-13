package com.example.moisessongs.data.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionError
import androidx.media3.session.SessionToken
import com.example.moisessongs.domain.Song
import com.example.moisessongs.domain.library.MediaIds
import com.example.moisessongs.domain.playback.PlaybackQueue
import com.example.moisessongs.domain.playback.PlaybackState
import com.example.moisessongs.domain.playback.PreviewPlayer
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** A phone-side controller; PlaybackService remains the only owner of the audio engine. */
class Media3PreviewPlayer(context: Context, private val scope: CoroutineScope) : PreviewPlayer {
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state = mutableState.asStateFlow()
    private val mutableQueue = MutableStateFlow<List<Song>>(emptyList())
    override val queue = mutableQueue.asStateFlow()
    private var ticker: Job? = null
    private var selection: Job? = null
    private var released = false
    private var commandFailed = false
    private var currentSong: Song? = null
    private val connection = MediaController.Builder(context.applicationContext,
        SessionToken(context, ComponentName(context, PlaybackService::class.java)))
        .setListener(object : MediaController.Listener {
            override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                mutableState.update { it.copy(historyFailed = extras.getBoolean(SessionKeys.HISTORY_FAILED)) }
            }
            @androidx.annotation.OptIn(UnstableApi::class)
            override fun onError(controller: MediaController, sessionError: SessionError) {
                commandFailed = true
                mutableState.update { it.copy(failed = true, buffering = false, playing = false) }
            }
            override fun onDisconnected(controller: MediaController) {
                ticker?.cancel()
                if (!released) mutableState.update { it.copy(failed = true, buffering = false, playing = false) }
            }
        }).buildAsync()

    init {
        connection.addListener({
            if (!released) try {
                // This listener runs only once the connection future has completed.
                val connected = connection.get()
                connected.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) { refresh(connected) }
                })
                refresh(connected)
            } catch (_: Exception) {
                mutableState.update { it.copy(failed = true, buffering = false) }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    override fun prepare(song: Song, autoPlay: Boolean, queue: List<Song>) {
        selection?.cancel()
        commandFailed = false
        currentSong = song
        val songs = PlaybackQueue.startingWith(song, queue).songs
        mutableQueue.value = songs
        mutableState.value = PlaybackState(song = song, repeat = state.value.repeat,
            buffering = autoPlay && song.previewUrl != null)
        selection = withController { player ->
            // Opening the phone must not replace or pause an existing car session.
            if (player.currentMediaItem?.mediaId == MediaIds.song(song.id) && player.playerError == null) {
                if (autoPlay) player.play()
                refresh(player)
            } else if (!autoPlay && player.currentMediaItem != null) {
                refresh(player)
            } else if (song.previewUrl == null) {
                player.stop()
                player.clearMediaItems()
            } else {
                val playable = songs.filter { it.previewUrl != null }
                player.setMediaItems(playable.map { it.toMediaItem() }, playable.indexOfFirst { it.id == song.id }, 0)
                player.prepare()
                player.playWhenReady = autoPlay
            }
        }
    }

    override fun play() {
        commandFailed = false
        withController { player ->
            if (player.playerError != null) player.prepare()
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    override fun togglePlayback() {
        withController { player ->
            if (player.isPlaying || player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) player.pause()
            else {
                commandFailed = false
                if (player.playerError != null) player.prepare()
                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                player.play()
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        withController { player ->
            if (player.duration > 0) player.seekTo(positionMs.coerceIn(0, player.duration))
        }
    }

    override fun toggleRepeat() {
        withController { it.repeatMode = if (it.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE }
    }
    override fun pause() { withController { it.pause() } }

    override fun release() {
        released = true
        selection?.cancel()
        ticker?.cancel()
        MediaController.releaseFuture(connection)
    }

    private fun withController(action: (MediaController) -> Unit): Job = scope.launch {
        try {
            val connected = Futures.nonCancellationPropagating(connection).await()
            if (!released) action(connected)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.update { it.copy(failed = true, buffering = false, playing = false) } }
    }

    private fun refresh(player: MediaController) {
        player.currentMediaItem?.toSong()?.let { currentSong = it }
        if (player.mediaItemCount > 0) {
            mutableQueue.value = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).toSong() }
        }
        updateClock(player)
        if (player.isPlaying && ticker?.isActive != true) {
            ticker = scope.launch {
                while (isActive && player.isPlaying) { updateClock(player); delay(250.milliseconds) }
            }
        } else if (!player.isPlaying) ticker?.cancel()
    }

    private fun updateClock(player: MediaController) {
        mutableState.update { it.copy(song = currentSong,
            playing = player.isPlaying, buffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0), durationMs = player.duration.coerceAtLeast(0),
            ended = player.playbackState == Player.STATE_ENDED, failed = commandFailed || player.playerError != null,
            repeat = player.repeatMode == Player.REPEAT_MODE_ONE,
            historyFailed = player.sessionExtras.getBoolean(SessionKeys.HISTORY_FAILED)) }
    }
}
