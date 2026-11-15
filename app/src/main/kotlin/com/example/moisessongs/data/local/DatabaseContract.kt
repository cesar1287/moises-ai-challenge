package com.example.moisessongs.data.local

/** Persisted identifiers are an on-disk contract; changing them requires a Room migration. */
internal object DatabaseContract {
    const val NAME = "songs.db"
    const val VERSION = 1
    const val RECENT_HISTORY_LIMIT = 20

    object Tables {
        const val SONGS = "songs"
        const val SEARCHES = "searches"
        const val SEARCH_RESULTS = "search_results"
        const val RECENT_PLAYS = "recent_plays"
        const val ALBUMS = "albums"
        const val ALBUM_TRACKS = "album_tracks"
    }

    object Columns {
        const val ID = "id"
        const val TITLE = "title"
        const val ARTIST = "artist"
        const val ALBUM_ID = "albumId"
        const val ALBUM = "album"
        const val ARTWORK_URL = "artworkUrl"
        const val PREVIEW_URL = "previewUrl"
        const val STORE_URL = "storeUrl"
        const val DURATION_MS = "durationMs"
        const val TRACK_NUMBER = "trackNumber"
        const val DISC_NUMBER = "discNumber"
        const val GENRE = "genre"
        const val RELEASE_DATE = "releaseDate"
        const val EXPLICIT = "explicit"
        const val QUERY = "query"
        const val REQUESTED_LIMIT = "requestedLimit"
        const val END_REACHED = "endReached"
        const val UPDATED_AT = "updatedAt"
        const val SONG_ID = "songId"
        const val POSITION = "position"
        const val PLAYED_AT = "playedAt"
        const val TRACK_COUNT = "trackCount"
    }

    object Prefixes {
        const val SEARCH = "cache_"
        const val ALBUM = "album_"
    }
}
