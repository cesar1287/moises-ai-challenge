package com.example.moisessongs.ui.common

import androidx.compose.foundation.Image
import androidx.annotation.DrawableRes
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
import androidx.compose.ui.unit.dp
import java.util.Locale
import coil3.compose.AsyncImage
import com.example.moisessongs.R
import com.example.moisessongs.domain.Song
import com.example.moisessongs.domain.largeArtworkUrl
import com.example.moisessongs.ui.theme.MutedText
import com.example.moisessongs.ui.theme.SearchSurface
import com.example.moisessongs.ui.theme.SecondaryText

@Composable
fun FigmaIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, width: Dp = 24.dp,
    height: Dp = width, rotation: Float = 0f, scaleY: Float = 1f) {
    Image(painter = painterResource(icon), contentDescription = null,
        modifier = modifier.size(width, height).graphicsLayer { rotationZ = rotation; this.scaleY = scaleY })
}

@Composable
fun AssetButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, iconSize: Dp = 24.dp) {
    IconButton(onClick = onClick, enabled = enabled,
        modifier = modifier.semantics { contentDescription = description }) {
        FigmaIcon(icon, Modifier.alpha(if (enabled) 1f else .35f), width = iconSize)
    }
}

@Composable
fun Artwork(url: String?, size: Dp, modifier: Modifier = Modifier, radius: Dp = 8.dp) {
    val largeUrl = remember(url, size) {
        if (size >= 120.dp) largeArtworkUrl(url) else url
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
    Row(Modifier.fillMaxWidth().heightIn(min = if (compact) 60.dp else 68.dp)
        .padding(horizontal = if (compact) 20.dp else 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.play), onClick = onClick).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Artwork(song.artworkUrl, if (compact) 44.dp else 52.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = SecondaryText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onOptions != null) AssetButton(R.drawable.ic_more_muted, stringResource(R.string.more_options, song.title),
            onOptions, iconSize = 20.dp)
    }
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp).heightIn(min = 48.dp)
        .padding(horizontal = if (onBack == null) 24.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) AssetButton(R.drawable.ic_back, stringResource(R.string.back), onBack)
        Text(title, modifier = Modifier.weight(1f).semantics { heading() },
            style = if (onBack == null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun LoadingState(label: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MutedText)
    }
}

@Composable
fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.musical_note), null, Modifier.size(64.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MutedText,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun FailureBanner(failure: UiFailure, savedLabel: String? = null, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
        .clip(RoundedCornerShape(12.dp)).background(SearchSurface).padding(16.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(stringResource(when (failure) {
            UiFailure.CONNECTION -> R.string.connection_error
            UiFailure.RATE_LIMIT -> R.string.rate_limit_error
            UiFailure.SERVICE -> R.string.service_error
            UiFailure.UNEXPECTED -> R.string.unexpected_error
        }), style = MaterialTheme.typography.bodyMedium)
        if (savedLabel != null) Text(savedLabel, color = MutedText,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

fun formatTime(milliseconds: Long, locale: Locale = Locale.ROOT): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return String.format(locale, "%d:%02d", seconds / 60, seconds % 60)
}
