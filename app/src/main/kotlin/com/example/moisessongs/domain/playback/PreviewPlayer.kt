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
)

interface PreviewPlayer {
    val state: StateFlow<PlaybackState>
    fun prepare(song: Song, autoPlay: Boolean)
    fun togglePlayback()
    fun seekTo(positionMs: Long)
    fun toggleRepeat()
    fun pause()
    fun release()
}
