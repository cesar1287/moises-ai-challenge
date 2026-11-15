package com.example.moisessongs.data.remote

import com.example.moisessongs.domain.SearchPolicy

/** Wire values belong to the iTunes adapter, rather than the domain or presentation layers. */
internal object ITunesContract {
    const val SONG = "song"
    const val COLLECTION = "collection"
    const val EXPLICIT = "explicit"
    const val COUNTRY = "US"
    const val MUSIC = "music"
    const val HTTP_PREFIX = "http://"
    const val HTTPS_PREFIX = "https://"

    object Parameters {
        const val TERM = "term"
        const val LIMIT = "limit"
        const val ID = "id"
        const val MEDIA = "media"
        const val ENTITY = "entity"
        const val COUNTRY = "country"
    }

    const val SEARCH = "search?${Parameters.MEDIA}=$MUSIC&${Parameters.ENTITY}=$SONG&${Parameters.COUNTRY}=$COUNTRY"
    const val LOOKUP = "lookup?${Parameters.ENTITY}=$SONG&${Parameters.COUNTRY}=$COUNTRY&${Parameters.LIMIT}=${SearchPolicy.MAX_RESULTS}"
    const val ALBUM_UNAVAILABLE = "This album is no longer available in the catalog."
}
