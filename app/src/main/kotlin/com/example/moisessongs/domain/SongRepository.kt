package com.example.moisessongs.domain

import kotlinx.coroutines.flow.Flow

interface SongRepository {
    fun observeSearch(query: String): Flow<SearchSnapshot?>
    fun observeRecentSongs(): Flow<List<Song>>
    fun observeAlbum(id: Long): Flow<Album?>
    suspend fun loadSearch(query: String, refresh: Boolean = false)
    suspend fun refreshAlbum(id: Long)
    suspend fun getSong(id: Long): Song?
    suspend fun recordPlayed(song: Song)
}

interface SongCatalog {
    suspend fun search(query: String, limit: Int): CatalogSearch
    suspend fun album(id: Long): Album
}

data class CatalogSearch(val songs: List<Song>, val rawResultCount: Int)

object SearchPolicy {
    const val PAGE_SIZE = 25
    const val MAX_RESULTS = 200
    fun normalize(query: String): String = query.trim().replace(Regex("\\s+"), " ")
    fun nextLimit(previous: Int): Int = (previous + PAGE_SIZE).coerceAtMost(MAX_RESULTS)
}
