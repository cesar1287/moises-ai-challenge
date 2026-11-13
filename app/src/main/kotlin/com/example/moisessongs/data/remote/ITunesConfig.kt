package com.example.moisessongs.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType

internal object ITunesConfig {
    const val BASE_URL = "https://itunes.apple.com/"
    val jsonMediaType = "application/json".toMediaType()

    // Reuse the serializer cache; initialize only when the catalog is first needed.
    val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
}
