package com.example.moisessongs.ui.theme

import androidx.compose.ui.unit.dp

/** Shared spacing and component geometry from the phone design. Values stay density independent. */
object AppSpacing {
    val none = 0.dp
    val tiny = 4.dp
    val detail = 6.dp
    val small = 8.dp
    val medium = 12.dp
    val inputVertical = 14.dp
    val regular = 16.dp
    val large = 20.dp
    val screen = 24.dp
    val extraLarge = 32.dp
    val section = 48.dp
    val emptyVertical = 56.dp
}

object AppCorners {
    val small = 8.dp
    val medium = 12.dp
    val sheet = 16.dp
    val artwork = 20.dp
}

object AppSizes {
    val icon = 24.dp
    val smallIcon = 20.dp
    val skipIcon = 36.dp
    val touchTarget = 48.dp
    val progressStroke = 2.dp
    val emptyArtwork = 64.dp
    val splashArtwork = 100.dp
    val largeArtworkThreshold = 120.dp
    val albumArtwork = 120.dp
    val rowArtwork = 52.dp
    val compactRowArtwork = 44.dp
    val songRow = 68.dp
    val compactSongRow = 60.dp
    val sheetHandleArea = 14.dp
    val sheetHandleWidth = 56.dp
    val sheetHandleHeight = 5.dp
    val sheetAction = 56.dp
    val playerArtwork = 264.dp
    val playerArtworkWidthInset = 80.dp
    val playButton = 72.dp
    val playIconWidth = 18.6074.dp
    val playIconHeight = 21.0787.dp
    val timelineThumb = 16.dp
    val timelineTrack = 4.dp
}

object AppLayout {
    val contentMaxWidth = 720.dp
    val playerControlsMaxWidth = 600.dp
    val compactPlayerHeight = 600.dp
    const val LARGE_FONT_SCALE = 1.3f
    const val PLAYER_ARTWORK_VERTICAL_BIAS = -.2f
}

object AppOpacity {
    const val DISABLED = .35f
    const val SECONDARY = .7f
    const val MEDIUM = .6f
    const val SUBTLE = .4f
    const val SURFACE = .2f
}
