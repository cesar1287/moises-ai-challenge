package com.example.moisessongs.data

import com.example.moisessongs.data.local.SongStore
import com.example.moisessongs.domain.SearchPolicy
import com.example.moisessongs.domain.SearchSnapshot
import com.example.moisessongs.domain.Song
import com.example.moisessongs.domain.SongCatalog
import com.example.moisessongs.domain.SongRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.catch
import java.util.Locale

class DefaultSongRepository(
    private val catalog: SongCatalog,
    private val store: SongStore,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Long = System::currentTimeMillis,
) : SongRepository {
    private val searchLock = Mutex()
    override fun observeSearch(query: String) = store.observeSearch(query.cacheKey()).catch { throw it.asDomainFailure() }
    override fun observeRecentSongs() = store.observeRecentSongs().catch { throw it.asDomainFailure() }
    override fun observeAlbum(id: Long) = store.observeAlbum(id).catch { throw it.asDomainFailure() }
    override suspend fun getSong(id: Long) = dataCall { store.song(id) }
    override suspend fun recordPlayed(song: Song) = dataCall { store.recordPlayed(song, now()) }
    override suspend fun refreshAlbum(id: Long) = dataCall { store.saveAlbum(catalog.album(id)) }

    override suspend fun loadSearch(query: String, refresh: Boolean) = dataCall {
        withContext(computationDispatcher) {
            val normalized = SearchPolicy.normalize(query)
            if (normalized.isBlank()) return@withContext
            searchLock.withLock {
                val key = normalized.cacheKey()
                val previous = store.search(key)
                if (!refresh && previous?.endReached == true) return@withLock
                // iTunes ignores offset: request a larger prefix, then merge by stable track ID.
                val limit = if (refresh) maxOf(SearchPolicy.PAGE_SIZE, previous?.requestedLimit ?: 0)
                    else SearchPolicy.nextLimit(previous?.requestedLimit ?: 0)
                val response = catalog.search(normalized, limit)
                val songs = if (refresh) response.songs.distinctBy(Song::id)
                    else (response.songs + previous?.songs.orEmpty()).distinctBy(Song::id)
                store.saveSearch(key, SearchSnapshot(
                    songs = songs.take(SearchPolicy.MAX_RESULTS),
                    requestedLimit = limit,
                    endReached = response.rawResultCount < limit || limit >= SearchPolicy.MAX_RESULTS,
                    updatedAt = now(),
                ))
            }
        }
    }
}

private fun String.cacheKey(): String = SearchPolicy.normalize(this).lowercase(Locale.ROOT)
