package com.example.moisessongs

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Splash : NavKey
@Serializable data object Songs : NavKey
@Serializable data object Player : NavKey
@Serializable data class AlbumRoute(val id: Long) : NavKey
