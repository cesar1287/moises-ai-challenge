package com.example.moisessongs.domain

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val albumId: Long?,
    val album: String,
    val artworkUrl: String?,
    val previewUrl: String?,
    val storeUrl: String?,
    val durationMs: Long,
    val trackNumber: Int = 0,
    val discNumber: Int = 1,
    val genre: String = "",
    val releaseDate: String = "",
    val explicit: Boolean = false,
)

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val releaseDate: String,
    val genre: String,
    val trackCount: Int,
    val storeUrl: String?,
    val songs: List<Song>,
)

data class SearchSnapshot(
    val songs: List<Song> = emptyList(),
    val requestedLimit: Int = 0,
    val endReached: Boolean = false,
    val updatedAt: Long = 0,
)
