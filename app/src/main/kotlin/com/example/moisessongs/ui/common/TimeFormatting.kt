package com.example.moisessongs.ui.common

import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TIME_PATTERN = "%d:%02d"
private const val SECONDS_PER_MINUTE = 60

internal fun wholeSeconds(milliseconds: Long): Long =
    TimeUnit.MILLISECONDS.toSeconds(milliseconds.coerceAtLeast(0))

fun formatTime(milliseconds: Long, locale: Locale = Locale.ROOT): String {
    val seconds = wholeSeconds(milliseconds)
    return String.format(locale, TIME_PATTERN, seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}
