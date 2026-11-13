package com.example.moisessongs

import com.example.moisessongs.data.local.SongStore
import com.example.moisessongs.domain.*
import com.example.moisessongs.domain.playback.PlaybackState
import com.example.moisessongs.domain.playback.PreviewPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

fun song(id: Long = 1, albumId: Long = 10) = Song(id, "Song $id", "Artist", albumId, "Album", null,
    "https://example.com/$id.m4a", "https://music.apple.com/song/$id", 210_000, id.toInt())

fun album(songs: List<Song> = listOf(song())) = Album(10, "Album", "Artist", null, "2026-01-01", "Pop", songs.size, null, songs)

class MemorySongStore : SongStore {
    private val searches = mutableMapOf<String, MutableStateFlow<SearchSnapshot?>>()
    private val albums = mutableMapOf<Long, MutableStateFlow<Album?>>()
    private val songs = mutableMapOf<Long, Song>()
    val recents = MutableStateFlow<List<Song>>(emptyList())
    private fun searchFlow(query: String) = searches.getOrPut(query) { MutableStateFlow(null) }
    private fun albumFlow(id: Long) = albums.getOrPut(id) { MutableStateFlow(null) }
    override fun observeSearch(query: String) = searchFlow(query).asStateFlow()
    override fun observeRecentSongs() = recents.asStateFlow()
    override fun observeAlbum(id: Long) = albumFlow(id).asStateFlow()
    override suspend fun search(query: String) = searchFlow(query).value
    override suspend fun saveSearch(query: String, snapshot: SearchSnapshot) {
        snapshot.songs.forEach { songs[it.id] = it }
        searchFlow(query).value = snapshot
    }
    override suspend fun saveAlbum(album: Album) {
        album.songs.forEach { songs[it.id] = it }
        albumFlow(album.id).value = album
    }
    override suspend fun song(id: Long) = songs[id]
    override suspend fun recordPlayed(song: Song, playedAt: Long) {
        songs[song.id] = song
        recents.value = (listOf(song) + recents.value).distinctBy(Song::id).take(20)
    }
}

class FakeCatalog : SongCatalog {
    val requests = mutableListOf<Pair<String, Int>>()
    var search: suspend (String, Int) -> CatalogSearch = { _, limit ->
        CatalogSearch((1..limit).map { song(it.toLong()) }, limit)
    }
    var lookup: suspend (Long) -> Album = { album() }
    override suspend fun search(query: String, limit: Int): CatalogSearch {
        requests += query to limit
        return search.invoke(query, limit)
    }
    override suspend fun album(id: Long) = lookup(id)
}

class FakePlayer : PreviewPlayer {
    val mutableState = MutableStateFlow(PlaybackState())
    override val state = mutableState.asStateFlow()
    val preparations = mutableListOf<Pair<Song, Boolean>>()
    var released = false
    override fun prepare(song: Song, autoPlay: Boolean) {
        preparations += song to autoPlay
        mutableState.value = PlaybackState(song = song)
    }
    override fun play() { mutableState.value = state.value.copy(playing = true) }
    override fun togglePlayback() { mutableState.value = state.value.copy(playing = !state.value.playing) }
    override fun seekTo(positionMs: Long) { mutableState.value = state.value.copy(positionMs = positionMs) }
    override fun toggleRepeat() { mutableState.value = state.value.copy(repeat = !state.value.repeat) }
    override fun pause() { mutableState.value = state.value.copy(playing = false) }
    override fun release() { released = true }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
