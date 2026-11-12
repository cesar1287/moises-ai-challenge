package com.example.moisessongs.ui.common

import com.example.moisessongs.domain.DataFailure
import com.example.moisessongs.domain.DataFailureException

enum class UiFailure { CONNECTION, RATE_LIMIT, SERVICE, UNEXPECTED }

fun Throwable.toUiFailure(): UiFailure = when ((this as? DataFailureException)?.failure) {
    DataFailure.CONNECTION -> UiFailure.CONNECTION
    DataFailure.RATE_LIMIT -> UiFailure.RATE_LIMIT
    DataFailure.SERVICE -> UiFailure.SERVICE
    else -> UiFailure.UNEXPECTED
}
