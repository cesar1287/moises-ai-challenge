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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
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
    private val mutableQueue = MutableStateFlow<List<Song>>(emptyList())
    val queue = mutableQueue.asStateFlow()
    private val mutableHistoryFailed = MutableStateFlow(false)
    val historyFailed = mutableHistoryFailed.asStateFlow()
    private var restorePending = true

    init {
        viewModelScope.launch {
            val id = savedState.get<Long>(SavedStateKeys.SELECTED_SONG_ID)
            val song = try { id?.let { repository.getSong(it) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (restorePending && song != null) {
                mutableQueue.value = listOf(song)
                player.prepare(song, autoPlay = false)
            }
            restorePending = false
        }
        viewModelScope.launch {
            var recordedId: Long? = null
            playback.collect { state ->
                val song = state.song
                if (state.playing && song != null && recordedId != song.id) {
                    recordedId = song.id
                    try {
                        repository.recordPlayed(song)
                        mutableHistoryFailed.value = false
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        mutableHistoryFailed.value = true
                    }
                }
            }
        }
    }

    fun select(song: Song, queue: List<Song>) {
        restorePending = false
        mutableQueue.value = PlaybackQueue.startingWith(song, queue).songs
        savedState[SavedStateKeys.SELECTED_SONG_ID] = song.id
        if (playback.value.song?.id != song.id) player.prepare(song, autoPlay = true)
        else player.play()
    }

    fun previous() = skip(-1)
    fun next() = skip(1)
    private fun skip(direction: Int) {
        val current = playback.value.song ?: return
        PlaybackQueue.startingWith(current, queue.value).adjacent(current.id, direction)?.let { song ->
            savedState[SavedStateKeys.SELECTED_SONG_ID] = song.id
            player.prepare(song, autoPlay = true)
        }
    }

    fun togglePlayback() = player.togglePlayback()
    fun seek(positionMs: Long) = player.seekTo(positionMs)
    fun toggleRepeat() = player.toggleRepeat()
    fun pause() = player.pause()
    override fun onCleared() = player.release()
}
