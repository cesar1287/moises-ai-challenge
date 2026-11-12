package com.example.moisessongs.data.local

import androidx.room.withTransaction
import com.example.moisessongs.domain.Album
import com.example.moisessongs.domain.SearchSnapshot
import com.example.moisessongs.domain.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface SongStore {
    fun observeSearch(query: String): Flow<SearchSnapshot?>
    fun observeRecentSongs(): Flow<List<Song>>
    fun observeAlbum(id: Long): Flow<Album?>
    suspend fun search(query: String): SearchSnapshot?
    suspend fun saveSearch(query: String, snapshot: SearchSnapshot)
    suspend fun saveAlbum(album: Album)
    suspend fun song(id: Long): Song?
    suspend fun recordPlayed(song: Song, playedAt: Long)
}

class RoomSongStore(private val database: SongDatabase,
    private val mappingDispatcher: CoroutineDispatcher = Dispatchers.Default) : SongStore {
    private val dao = database.songs()

    // A single joined query keeps metadata and ordered results in the same SQLite snapshot.
    override fun observeSearch(query: String) = dao.observeSearchRows(query).map { rows ->
        rows.firstOrNull()?.cache?.let { cache ->
            SearchSnapshot(rows.mapNotNull { it.song?.toSong() }, cache.requestedLimit,
                cache.endReached, cache.updatedAt)
        }
    }.distinctUntilChanged().flowOn(mappingDispatcher)

    override fun observeRecentSongs() = dao.observeRecentSongs()
        .map { songs -> songs.map(SongEntity::toSong) }.distinctUntilChanged().flowOn(mappingDispatcher)

    override fun observeAlbum(id: Long) = dao.observeAlbumRows(id).map { rows ->
        rows.firstOrNull()?.album?.let { a ->
            Album(a.id, a.title, a.artist, a.artworkUrl, a.releaseDate, a.genre, a.trackCount,
                a.storeUrl, rows.mapNotNull { it.song?.toSong() })
        }
    }.distinctUntilChanged().flowOn(mappingDispatcher)

    override suspend fun search(query: String): SearchSnapshot? = database.withTransaction {
        dao.getSearch(query)?.let {
            SearchSnapshot(dao.getSearchSongs(query).map(SongEntity::toSong),
                it.requestedLimit, it.endReached, it.updatedAt)
        }
    }

    override suspend fun saveSearch(query: String, snapshot: SearchSnapshot) {
        database.withTransaction {
            dao.putSongs(snapshot.songs.map(Song::toEntity))
            dao.clearSearchResults(query)
            dao.putSearchResults(snapshot.songs.mapIndexed { index, song ->
                SearchResultEntity(query, song.id, index)
            })
            dao.putSearch(SearchEntity(query, snapshot.requestedLimit, snapshot.endReached, snapshot.updatedAt))
        }
    }

    override suspend fun saveAlbum(album: Album) {
        database.withTransaction {
            dao.putSongs(album.songs.map(Song::toEntity))
            dao.putAlbum(album.toEntity())
            dao.clearAlbumTracks(album.id)
            dao.putAlbumTracks(album.songs.mapIndexed { index, song -> AlbumTrackEntity(album.id, song.id, index) })
        }
    }

    override suspend fun song(id: Long) = dao.getSong(id)?.toSong()

    override suspend fun recordPlayed(song: Song, playedAt: Long) {
        database.withTransaction {
            // Search/album refreshes own metadata. Avoid invalidating every catalog observer
            // (or overwriting newer metadata) when an already cached song starts playing.
            dao.insertMissingSong(song.toEntity())
            dao.putRecentPlay(RecentPlayEntity(song.id, playedAt))
            dao.trimRecentPlays()
        }
    }
}
