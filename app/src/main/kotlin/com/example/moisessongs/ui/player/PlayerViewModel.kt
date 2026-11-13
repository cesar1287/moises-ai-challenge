package com.example.moisessongs.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moisessongs.domain.Song
import com.example.moisessongs.domain.SongRepository
import com.example.moisessongs.domain.playback.PlaybackProgress
import com.example.moisessongs.domain.playback.PlaybackQueue
import com.example.moisessongs.domain.playback.PreviewPlayer
import com.example.moisessongs.ui.common.SavedStateKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val repository: SongRepository,
    private val savedState: SavedStateHandle,
    playerFactory: (kotlinx.coroutines.CoroutineScope) -> PreviewPlayer,
) : ViewModel() {
    private val player = playerFactory(viewModelScope)
    val playback = player.state
    // Timer ticks must not invalidate artwork, controls, or navigation.
    val presentation = playback.map { it.copy(positionMs = 0) }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, playback.value.copy(positionMs = 0))
    val progress = playback.map { PlaybackProgress(it.positionMs, it.durationMs) }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackProgress())
    val queue = player.queue
    val historyFailed = playback.map { it.historyFailed }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, playback.value.historyFailed)
    private var restorePending = true

    init {
        viewModelScope.launch {
            val id = savedState.get<Long>(SavedStateKeys.SELECTED_SONG_ID)
            val song = try { id?.let { repository.getSong(it) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (restorePending && song != null) {
                player.prepare(song, autoPlay = false)
            }
            restorePending = false
        }
        viewModelScope.launch {
            playback.map { it.song?.id }.distinctUntilChanged().collect { id ->
                id?.let { savedState[SavedStateKeys.SELECTED_SONG_ID] = it }
            }
        }
    }

    fun select(song: Song, queue: List<Song>) {
        restorePending = false
        savedState[SavedStateKeys.SELECTED_SONG_ID] = song.id
        if (playback.value.song?.id != song.id) player.prepare(song, autoPlay = true,
            queue = PlaybackQueue.startingWith(song, queue).songs)
        else player.play()
    }

    fun previous() = skip(-1)
    fun next() = skip(1)
    private fun skip(direction: Int) {
        val current = playback.value.song ?: return
        PlaybackQueue.startingWith(current, queue.value).adjacent(current.id, direction)?.let { song ->
            savedState[SavedStateKeys.SELECTED_SONG_ID] = song.id
            player.prepare(song, autoPlay = true, queue = queue.value)
        }
    }

    fun togglePlayback() = player.togglePlayback()
    fun seek(positionMs: Long) = player.seekTo(positionMs)
    fun toggleRepeat() = player.toggleRepeat()
    fun pause() = player.pause()
    override fun onCleared() = player.release()
}
