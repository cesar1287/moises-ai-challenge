package com.example.moisessongs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkTest {
    @Test fun `known artwork thumbnails retain their image format when enlarged`() {
        for (extension in listOf("jpg", "png")) {
            assertEquals("https://is1-ssl.mzstatic.com/image/600x600bb.$extension",
                largeArtworkUrl("https://is1-ssl.mzstatic.com/image/100x100bb.$extension"))
        }
    }

    @Test fun `foreign hosts and lookalike CDN domains are never rewritten`() {
        for (url in listOf("https://example.com/100x100bb.jpg",
            "https://is1.mzstatic.com.example.com/100x100bb.jpg",
            "http://is1.mzstatic.com/100x100bb.jpg", "file:///100x100bb.jpg")) {
            assertEquals(url, largeArtworkUrl(url))
        }
    }

    @Test fun `missing already large and unsupported artwork keep their original values`() {
        assertNull(largeArtworkUrl(null))
        for (url in listOf("", "https://is1.mzstatic.com/600x600bb.jpg",
            "https://is1.mzstatic.com/100x100bb.webp", "https://is1.mzstatic.com/100x100bb.jpg?token=abc")) {
            assertEquals(url, largeArtworkUrl(url))
        }
    }
}
