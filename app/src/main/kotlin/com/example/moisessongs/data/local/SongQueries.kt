package com.example.moisessongs.data.local

import com.example.moisessongs.data.local.DatabaseContract.Columns
import com.example.moisessongs.data.local.DatabaseContract.Prefixes
import com.example.moisessongs.data.local.DatabaseContract.Tables

internal object SongQueries {
    const val OBSERVE_SEARCH = """
        SELECT q.`${Columns.QUERY}` AS ${Prefixes.SEARCH}${Columns.QUERY},
               q.${Columns.REQUESTED_LIMIT} AS ${Prefixes.SEARCH}${Columns.REQUESTED_LIMIT},
               q.${Columns.END_REACHED} AS ${Prefixes.SEARCH}${Columns.END_REACHED},
               q.${Columns.UPDATED_AT} AS ${Prefixes.SEARCH}${Columns.UPDATED_AT},
               s.*
        FROM ${Tables.SEARCHES} q
        LEFT JOIN ${Tables.SEARCH_RESULTS} r ON q.`${Columns.QUERY}` = r.`${Columns.QUERY}`
        LEFT JOIN ${Tables.SONGS} s ON s.${Columns.ID} = r.${Columns.SONG_ID}
        WHERE q.`${Columns.QUERY}` = :query
        ORDER BY r.${Columns.POSITION}
    """

    const val OBSERVE_ALBUM = """
        SELECT a.${Columns.ID} AS ${Prefixes.ALBUM}${Columns.ID},
               a.${Columns.TITLE} AS ${Prefixes.ALBUM}${Columns.TITLE},
               a.${Columns.ARTIST} AS ${Prefixes.ALBUM}${Columns.ARTIST},
               a.${Columns.ARTWORK_URL} AS ${Prefixes.ALBUM}${Columns.ARTWORK_URL},
               a.${Columns.RELEASE_DATE} AS ${Prefixes.ALBUM}${Columns.RELEASE_DATE},
               a.${Columns.GENRE} AS ${Prefixes.ALBUM}${Columns.GENRE},
               a.${Columns.TRACK_COUNT} AS ${Prefixes.ALBUM}${Columns.TRACK_COUNT},
               a.${Columns.STORE_URL} AS ${Prefixes.ALBUM}${Columns.STORE_URL},
               s.*
        FROM ${Tables.ALBUMS} a
        LEFT JOIN ${Tables.ALBUM_TRACKS} t ON a.${Columns.ID} = t.${Columns.ALBUM_ID}
        LEFT JOIN ${Tables.SONGS} s ON s.${Columns.ID} = t.${Columns.SONG_ID}
        WHERE a.${Columns.ID} = :id
        ORDER BY t.${Columns.POSITION}
    """

    const val GET_SONG = """
        SELECT *
        FROM ${Tables.SONGS}
        WHERE ${Columns.ID} = :id
    """

    const val GET_SEARCH = """
        SELECT *
        FROM ${Tables.SEARCHES}
        WHERE `${Columns.QUERY}` = :query
    """

    const val GET_SEARCH_SONGS = """
        SELECT s.*
        FROM ${Tables.SONGS} s
        INNER JOIN ${Tables.SEARCH_RESULTS} r ON s.${Columns.ID} = r.${Columns.SONG_ID}
        WHERE r.`${Columns.QUERY}` = :query
        ORDER BY r.${Columns.POSITION}
    """

    const val CLEAR_SEARCH_RESULTS = """
        DELETE FROM ${Tables.SEARCH_RESULTS}
        WHERE `${Columns.QUERY}` = :query
    """

    const val OBSERVE_RECENT = """
        SELECT s.*
        FROM ${Tables.SONGS} s
        INNER JOIN ${Tables.RECENT_PLAYS} r ON s.${Columns.ID} = r.${Columns.SONG_ID}
        ORDER BY r.${Columns.PLAYED_AT} DESC, r.${Columns.SONG_ID} DESC LIMIT ${DatabaseContract.RECENT_HISTORY_LIMIT}
    """

    const val TRIM_RECENT = """
        DELETE FROM ${Tables.RECENT_PLAYS}
        WHERE ${Columns.SONG_ID} NOT IN (
            SELECT ${Columns.SONG_ID} FROM ${Tables.RECENT_PLAYS}
            ORDER BY ${Columns.PLAYED_AT} DESC, ${Columns.SONG_ID} DESC
            LIMIT ${DatabaseContract.RECENT_HISTORY_LIMIT}
        )
    """

    const val CLEAR_ALBUM_TRACKS = """
        DELETE FROM ${Tables.ALBUM_TRACKS}
        WHERE ${Columns.ALBUM_ID} = :id
    """
}
