package com.example.moisessongs.domain

object ArtworkPolicy {
    const val THUMBNAIL_SIZE_PX = 100
    const val LARGE_SIZE_PX = 600
    internal const val LARGE_SIZE_REPLACEMENT = "/${LARGE_SIZE_PX}x${LARGE_SIZE_PX}bb.$1"
}

private val artworkHost = Regex("https://[^/]+\\.mzstatic\\.com/.*")
private val artworkSize = Regex("/${ArtworkPolicy.THUMBNAIL_SIZE_PX}x${ArtworkPolicy.THUMBNAIL_SIZE_PX}bb\\.(jpg|png)$")

/** Request larger artwork only from the catalog's known image CDN. */
fun largeArtworkUrl(url: String?): String? =
    if (url?.matches(artworkHost) == true) url.replace(artworkSize, ArtworkPolicy.LARGE_SIZE_REPLACEMENT) else url
