package com.example.moisessongs.domain.playback

import com.example.moisessongs.domain.Song

/** Queue ordering and boundary rules are independent of the player and Android lifecycle. */
class PlaybackQueue private constructor(val songs: List<Song>) {
    fun adjacent(currentId: Long?, direction: Int): Song? {
        require(direction == -1 || direction == 1)
        val index = songs.indexOfFirst { it.id == currentId }
        return if (index < 0) null else songs.getOrNull(index + direction)
    }

    companion object {
        fun startingWith(song: Song, context: List<Song>): PlaybackQueue =
            PlaybackQueue((context + song).distinctBy(Song::id))
    }
}
