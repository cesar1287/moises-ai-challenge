package com.example.moisessongs.domain

private val artworkHost = Regex("https://[^/]+\\.mzstatic\\.com/.*")
private val artworkSize = Regex("/100x100bb\\.(jpg|png)$")

/** Request larger artwork only from the catalog's known image CDN. */
fun largeArtworkUrl(url: String?): String? =
    if (url?.matches(artworkHost) == true) url.replace(artworkSize, "/600x600bb.$1") else url
