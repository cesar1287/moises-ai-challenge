package com.example.moisessongs.domain.library

import com.example.moisessongs.domain.Album
import com.example.moisessongs.domain.Song
import com.example.moisessongs.domain.SongRepository
import com.example.moisessongs.domain.playback.PlaybackQueue
import kotlinx.coroutines.flow.first

object MediaIds {
    const val ROOT = "music"
    const val RECENT = "recent"
    const val ALBUMS = "albums"
    private const val SONG_PREFIX = "song:"
    private const val ALBUM_PREFIX = "album:"

    fun song(id: Long) = "$SONG_PREFIX$id"
    fun album(id: Long) = "$ALBUM_PREFIX$id"
    fun songId(value: String): Long? = value.idAfter(SONG_PREFIX)
    fun albumId(value: String): Long? = value.idAfter(ALBUM_PREFIX)
    private fun String.idAfter(prefix: String): Long? =
        takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.toLongOrNull()?.takeIf { it > 0 }
}

/** Browsing uses cached content first, including when the phone UI is not running. */
class MediaLibrary(private val repository: SongRepository) {
    suspend fun recent(): List<Song> = repository.observeRecentSongs().first()

    suspend fun albums(): List<Song> = recent().filter { it.albumId != null }.distinctBy(Song::albumId)

    suspend fun album(id: Long): Album {
        repository.observeAlbum(id).first()?.let { return it }
        repository.refreshAlbum(id)
        return requireNotNull(repository.observeAlbum(id).first()) { "Album is unavailable" }
    }

    suspend fun song(id: Long): Song = requireNotNull(repository.getSong(id)) { "Song is unavailable" }

    suspend fun queueFor(song: Song): List<Song> {
        val album = song.albumId?.let { repository.observeAlbum(it).first() }
        return PlaybackQueue.startingWith(song, album?.songs ?: recent()).songs.filter { it.previewUrl != null }
    }

    suspend fun search(query: String): List<Song> {
        repository.loadSearch(query, refresh = true)
        return searchResults(query)
    }

    suspend fun searchResults(query: String): List<Song> =
        repository.observeSearch(query).first()?.songs.orEmpty().filter { it.previewUrl != null }
}

internal fun <T> List<T>.libraryPage(page: Int, pageSize: Int): List<T> {
    require(page >= 0 && pageSize > 0)
    val start = page.toLong() * pageSize
    if (start >= size) return emptyList()
    return subList(start.toInt(), minOf(size.toLong(), start + pageSize).toInt())
}
