package com.example.moisessongs.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.example.moisessongs.R
import com.example.moisessongs.domain.Song
import com.example.moisessongs.domain.largeArtworkUrl
import com.example.moisessongs.ui.theme.AppCorners
import com.example.moisessongs.ui.theme.AppOpacity
import com.example.moisessongs.ui.theme.AppSizes
import com.example.moisessongs.ui.theme.AppSpacing
import com.example.moisessongs.ui.theme.MutedText
import com.example.moisessongs.ui.theme.SearchSurface
import com.example.moisessongs.ui.theme.SecondaryText

@Composable
fun FigmaIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, width: Dp = AppSizes.icon,
    height: Dp = width, rotation: Float = 0f, scaleY: Float = 1f) {
    Image(painter = painterResource(icon), contentDescription = null,
        modifier = modifier.size(width, height).graphicsLayer { rotationZ = rotation; this.scaleY = scaleY })
}

@Composable
fun AssetButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, iconSize: Dp = AppSizes.icon) {
    IconButton(onClick = onClick, enabled = enabled,
        modifier = modifier.semantics { contentDescription = description }) {
        FigmaIcon(icon, Modifier.alpha(if (enabled) 1f else AppOpacity.DISABLED), width = iconSize)
    }
}

@Composable
fun Artwork(url: String?, size: Dp, modifier: Modifier = Modifier, radius: Dp = AppCorners.small) {
    val largeUrl = remember(url, size) {
        if (size >= AppSizes.largeArtworkThreshold) largeArtworkUrl(url) else url
    }
    var useOriginal by remember(url, size) { mutableStateOf(false) }
    // CDN sizing is best-effort; retain the catalog's original URL as the fallback.
    AsyncImage(model = if (useOriginal) url else largeUrl, contentDescription = null,
        placeholder = painterResource(R.drawable.musical_note),
        error = painterResource(R.drawable.musical_note), contentScale = ContentScale.Crop,
        onError = { if (largeUrl != url) useOriginal = true },
        modifier = modifier.size(size).clip(RoundedCornerShape(radius)).background(SearchSurface))
}

@Composable
fun SongRow(song: Song, onClick: () -> Unit, onOptions: (() -> Unit)? = null, compact: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = if (compact) AppSizes.compactSongRow else AppSizes.songRow)
        .padding(horizontal = if (compact) AppSpacing.large else AppSpacing.screen), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clip(RoundedCornerShape(AppCorners.small))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.play), onClick = onClick).padding(vertical = AppSpacing.small),
            verticalAlignment = Alignment.CenterVertically) {
            Artwork(song.artworkUrl, if (compact) AppSizes.compactRowArtwork else AppSizes.rowArtwork)
            Spacer(Modifier.width(AppSpacing.regular))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.tiny)) {
                Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = SecondaryText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onOptions != null) AssetButton(R.drawable.ic_more_muted, stringResource(R.string.more_options, song.title),
            onOptions, iconSize = AppSizes.smallIcon)
    }
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = AppSpacing.large, bottom = AppSpacing.small).heightIn(min = AppSizes.touchTarget)
        .padding(horizontal = if (onBack == null) AppSpacing.screen else AppSpacing.small), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) AssetButton(R.drawable.ic_back, stringResource(R.string.back), onBack)
        Text(title, modifier = Modifier.weight(1f).semantics { heading() },
            style = if (onBack == null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun LoadingState(label: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(AppSpacing.extraLarge), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.regular)) {
        CircularProgressIndicator(Modifier.size(AppSizes.icon), strokeWidth = AppSizes.progressStroke)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MutedText)
    }
}

@Composable
fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.extraLarge, vertical = AppSpacing.emptyVertical),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        Image(painterResource(R.drawable.musical_note), null, Modifier.size(AppSizes.emptyArtwork))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MutedText,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun FailureBanner(failure: UiFailure, savedLabel: String? = null, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen, vertical = AppSpacing.small)
        .clip(RoundedCornerShape(AppCorners.medium)).background(SearchSurface).padding(AppSpacing.regular)
        .semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(stringResource(when (failure) {
            UiFailure.CONNECTION -> R.string.connection_error
            UiFailure.RATE_LIMIT -> R.string.rate_limit_error
            UiFailure.SERVICE -> R.string.service_error
            UiFailure.UNEXPECTED -> R.string.unexpected_error
        }), style = MaterialTheme.typography.bodyMedium)
        if (savedLabel != null) Text(savedLabel, color = MutedText,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = AppSpacing.detail))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}
