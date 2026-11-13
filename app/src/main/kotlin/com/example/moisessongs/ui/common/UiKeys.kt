package com.example.moisessongs.ui.common

/** Persisted names must remain stable across process recreation and app updates. */
internal object SavedStateKeys {
    const val SEARCH_INPUT = "input"
    const val SEARCH_QUERY = "query"
    const val SELECTED_SONG_ID = "songId"
}

internal object UiTestTags {
    const val SEARCH_INPUT = "search_input"
    const val SONG_LIST = "song_list"
}

internal object ListItemKeys {
    const val FAILURE = "failure"
    const val RECENT_HEADING = "recent_heading"
    const val WELCOME = "welcome"
    const val LOADING = "loading"
    const val EMPTY = "empty"
    const val PAGINATION = "pagination"
    const val ALBUM_HEADER = "album_header"
}

internal object ListContentTypes {
    const val SONG = "song"
}
