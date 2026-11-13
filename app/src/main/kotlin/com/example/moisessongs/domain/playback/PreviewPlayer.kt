package com.example.moisessongs.domain.playback

import com.example.moisessongs.domain.Song
import kotlinx.coroutines.flow.StateFlow

data class PlaybackProgress(val positionMs: Long = 0, val durationMs: Long = 0)

data class PlaybackState(
    val song: Song? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val ended: Boolean = false,
    val failed: Boolean = false,
    val repeat: Boolean = false,
    val historyFailed: Boolean = false,
)

interface PreviewPlayer {
    val state: StateFlow<PlaybackState>
    val queue: StateFlow<List<Song>>
    fun prepare(song: Song, autoPlay: Boolean, queue: List<Song> = listOf(song))
    /** Starts or resumes without pausing or restarting active playback. */
    fun play()
    fun togglePlayback()
    fun seekTo(positionMs: Long)
    fun toggleRepeat()
    fun pause()
    fun release()
}
