package com.example.moisessongs.domain.playback

import com.example.moisessongs.domain.Song
import org.junit.Assert.*
import org.junit.Test

class PlaybackQueueTest {
    private fun song(id: Long) = Song(id, "Song $id", "Artist", null, "", null, null, null, 0)

    @Test fun `queue preserves context ordering and deduplicates identifiers`() {
        val queue = PlaybackQueue.startingWith(song(2), listOf(song(1), song(2), song(1)))
        assertEquals(listOf(1L, 2L), queue.songs.map { it.id })
        assertEquals(1L, queue.adjacent(2, -1)?.id)
    }
    @Test fun `selection absent from context is still included`() {
        assertEquals(listOf(1L, 2L), PlaybackQueue.startingWith(song(2), listOf(song(1))).songs.map { it.id })
    }
    @Test fun `queue boundaries do not wrap or jump on an unknown current identifier`() {
        val queue = PlaybackQueue.startingWith(song(1), listOf(song(1), song(2)))
        assertNull(queue.adjacent(1, -1))
        assertNull(queue.adjacent(2, 1))
        assertNull(queue.adjacent(99, 1))
    }
}
