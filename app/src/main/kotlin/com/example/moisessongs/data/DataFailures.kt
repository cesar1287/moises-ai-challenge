package com.example.moisessongs.data

import android.database.sqlite.SQLiteException
import com.example.moisessongs.domain.DataFailure
import com.example.moisessongs.domain.DataFailureException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

internal fun Throwable.asDomainFailure(): DataFailureException = when (this) {
    is DataFailureException -> this
    else -> DataFailureException(when (this) {
        is HttpException -> if (code() == 429) DataFailure.RATE_LIMIT else DataFailure.SERVICE
        is SQLiteException -> DataFailure.STORAGE
        is IOException -> DataFailure.CONNECTION
        else -> DataFailure.UNEXPECTED
    }, this)
}

internal suspend fun <T> dataCall(block: suspend () -> T): T = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    throw failure.asDomainFailure()
}
