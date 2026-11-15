package com.example.moisessongs.data.remote

import com.example.moisessongs.domain.Album
import com.example.moisessongs.domain.CatalogSearch
import com.example.moisessongs.domain.Song
import com.example.moisessongs.domain.SongCatalog
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface ITunesApi {
    @GET(ITunesContract.SEARCH)
    suspend fun search(@Query(ITunesContract.Parameters.TERM) term: String, @Query(ITunesContract.Parameters.LIMIT) limit: Int): ITunesResponse

    @GET(ITunesContract.LOOKUP)
    suspend fun lookup(@Query(ITunesContract.Parameters.ID) id: Long): ITunesResponse
}

@Serializable
data class ITunesResponse(val resultCount: Int = 0, val results: List<ITunesItem> = emptyList())

@Serializable
data class ITunesItem(
    val wrapperType: String? = null,
    val kind: String? = null,
    val trackId: Long? = null,
    val collectionId: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val collectionName: String? = null,
    val artworkUrl100: String? = null,
    val previewUrl: String? = null,
    val trackViewUrl: String? = null,
    val collectionViewUrl: String? = null,
    val trackTimeMillis: Long? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val trackCount: Int? = null,
    val primaryGenreName: String? = null,
    val releaseDate: String? = null,
    val trackExplicitness: String? = null,
)

class ITunesCatalog(private val apiFactory: () -> ITunesApi,
    private val mappingDispatcher: CoroutineDispatcher = Dispatchers.Default) : SongCatalog {
    constructor(api: ITunesApi) : this({ api })

    override suspend fun search(query: String, limit: Int): CatalogSearch = withContext(mappingDispatcher) {
        val response = apiFactory().search(query, limit)
        CatalogSearch(response.results.mapNotNull(ITunesItem::toSong), response.results.size)
    }

    override suspend fun album(id: Long): Album = withContext(mappingDispatcher) {
        val response = apiFactory().lookup(id)
        val header = response.results.firstOrNull { it.wrapperType == ITunesContract.COLLECTION }
            ?: throw IOException(ITunesContract.ALBUM_UNAVAILABLE)
        Album(
            id = id,
            title = header.collectionName.orEmpty(),
            artist = header.artistName.orEmpty(),
            artworkUrl = header.artworkUrl100.secureUrl(),
            releaseDate = header.releaseDate.orEmpty(),
            genre = header.primaryGenreName.orEmpty(),
            trackCount = header.trackCount ?: 0,
            storeUrl = header.collectionViewUrl.secureUrl(),
            songs = response.results.mapNotNull(ITunesItem::toSong)
                .distinctBy(Song::id).sortedWith(compareBy(Song::discNumber, Song::trackNumber)),
        )
    }
}

internal fun ITunesItem.toSong(): Song? {
    if (kind != ITunesContract.SONG || trackId == null || trackName.isNullOrBlank() || artistName.isNullOrBlank()) return null
    return Song(
        id = trackId,
        title = trackName,
        artist = artistName,
        albumId = collectionId,
        album = collectionName.orEmpty(),
        artworkUrl = artworkUrl100.secureUrl(),
        previewUrl = previewUrl.secureUrl(),
        storeUrl = trackViewUrl.secureUrl(),
        durationMs = (trackTimeMillis ?: 0).coerceAtLeast(0),
        trackNumber = trackNumber ?: 0,
        discNumber = discNumber ?: 1,
        genre = primaryGenreName.orEmpty(),
        releaseDate = releaseDate.orEmpty(),
        explicit = trackExplicitness == ITunesContract.EXPLICIT,
    )
}

private fun String?.secureUrl(): String? = this?.takeIf { it.isNotBlank() }?.let {
    when {
        it.startsWith(ITunesContract.HTTPS_PREFIX) -> it
        it.startsWith(ITunesContract.HTTP_PREFIX) -> ITunesContract.HTTPS_PREFIX + it.removePrefix(ITunesContract.HTTP_PREFIX)
        else -> null
    }
}
