package com.example.moisessongs.domain

enum class DataFailure { CONNECTION, RATE_LIMIT, SERVICE, STORAGE, UNEXPECTED }

class DataFailureException(val failure: DataFailure, cause: Throwable) : Exception(failure.name, cause)
