package com.example.moisessongs.data.remote

import com.example.moisessongs.data.ContentTypes
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType

internal object ITunesConfig {
    const val BASE_URL = "https://itunes.apple.com/"
    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val READ_TIMEOUT_SECONDS = 20L
    const val CALL_TIMEOUT_SECONDS = 30L
    val jsonMediaType = ContentTypes.JSON.toMediaType()

    // Reuse the serializer cache; initialize only when the catalog is first needed.
    val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
}
