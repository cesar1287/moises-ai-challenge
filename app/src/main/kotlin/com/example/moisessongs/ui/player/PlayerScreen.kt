package com.example.moisessongs.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moisessongs.R
import com.example.moisessongs.domain.Song
import com.example.moisessongs.domain.playback.PlaybackPolicy
import com.example.moisessongs.domain.playback.PlaybackProgress
import com.example.moisessongs.domain.playback.PlaybackState
import com.example.moisessongs.ui.common.*
import com.example.moisessongs.ui.theme.AppCorners
import com.example.moisessongs.ui.theme.AppLayout
import com.example.moisessongs.ui.theme.AppOpacity
import com.example.moisessongs.ui.theme.AppSizes
import com.example.moisessongs.ui.theme.AppSpacing
import com.example.moisessongs.ui.theme.MutedText
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PlayerScreen(
    state: PlaybackState,
    queue: List<Song>,
    historyFailed: Boolean,
    onBack: () -> Unit,
    onOptions: (Song) -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onRepeat: () -> Unit,
    onStore: (String) -> Unit,
    progress: StateFlow<PlaybackProgress>? = null,
) {
    val song = state.song
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            ScreenHeader(stringResource(R.string.now_playing), onBack) {
                if (song != null) AssetButton(R.drawable.ic_more, stringResource(R.string.more_options, song.title), { onOptions(song) })
            }
            if (song == null) {
                EmptyState(stringResource(R.string.nothing_playing), stringResource(R.string.discover_body))
            } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val shortScreen = maxHeight < AppLayout.compactPlayerHeight || LocalDensity.current.fontScale > AppLayout.LARGE_FONT_SCALE
                val artSize = minOf(AppSizes.playerArtwork, maxWidth - AppSizes.playerArtworkWidthInset)
                val index = queue.indexOfFirst { it.id == song.id }
                Column(Modifier.fillMaxSize().then(if (shortScreen) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    val coverModifier = if (shortScreen) Modifier.fillMaxWidth().padding(vertical = AppSpacing.extraLarge)
                        else Modifier.fillMaxWidth().weight(1f)
                    Box(coverModifier, contentAlignment = if (shortScreen) Alignment.Center else BiasAlignment(0f, AppLayout.PLAYER_ARTWORK_VERTICAL_BIAS)) {
                        Artwork(song.artworkUrl, artSize, radius = AppCorners.artwork)
                    }
                    PlayerControls(state, historyFailed, index > 0, index >= 0 && index < queue.lastIndex,
                        onToggle, onPrevious, onNext, onSeek, onRepeat, onStore, progress)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerControls(
    state: PlaybackState, historyFailed: Boolean, hasPrevious: Boolean, hasNext: Boolean,
    onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    onSeek: (Long) -> Unit, onRepeat: () -> Unit, onStore: (String) -> Unit,
    progress: StateFlow<PlaybackProgress>?,
) {
    val song = state.song ?: return
    val toggleDescription = stringResource(if (state.playing || state.buffering) R.string.pause else R.string.play)
    val repeatDescription = stringResource(R.string.repeat_preview)
    val repeatState = stringResource(if (state.repeat) R.string.repeat_on else R.string.repeat_off)
    val previousDescription = stringResource(R.string.previous_song)
    val nextDescription = stringResource(R.string.next_song)
    Column(Modifier.widthIn(max = AppLayout.playerControlsMaxWidth).fillMaxWidth().padding(horizontal = AppSpacing.screen, vertical = AppSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.tiny)) {
            Text(song.title, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = AppOpacity.SECONDARY),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (song.previewUrl == null || state.failed || state.buffering) {
            Text(stringResource(when {
                song.previewUrl == null -> R.string.preview_unavailable
                state.failed -> R.string.preview_failed
                else -> R.string.preview_buffering
            }), style = MaterialTheme.typography.bodySmall, color = MutedText,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        key(song.id) { PlayerTimeline(state, progress, onSeek) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                Box(Modifier.size(AppSizes.playButton).clip(CircleShape).background(Color.White.copy(alpha = AppOpacity.SURFACE))
                    .clickable(enabled = song.previewUrl != null, role = Role.Button, onClick = onToggle)
                    .semantics { contentDescription = toggleDescription }, contentAlignment = Alignment.Center) {
                    when {
                        state.buffering -> CircularProgressIndicator(Modifier.size(AppSizes.icon), strokeWidth = AppSizes.progressStroke)
                        state.playing -> FigmaIcon(R.drawable.ic_pause)
                        else -> FigmaIcon(R.drawable.ic_play, width = AppSizes.playIconWidth, height = AppSizes.playIconHeight)
                    }
                }
                IconButton(onClick = onPrevious, enabled = hasPrevious,
                    modifier = Modifier.semantics { contentDescription = previousDescription }) {
                    FigmaIcon(R.drawable.ic_previous, Modifier.alpha(if (hasPrevious) 1f else AppOpacity.DISABLED), width = AppSizes.skipIcon, scaleY = -1f)
                }
                IconButton(onClick = onNext, enabled = hasNext,
                    modifier = Modifier.semantics { contentDescription = nextDescription }) {
                    FigmaIcon(R.drawable.ic_next, Modifier.alpha(if (hasNext) 1f else AppOpacity.DISABLED), width = AppSizes.skipIcon, rotation = 180f)
                }
            }
            IconToggleButton(checked = state.repeat, onCheckedChange = { onRepeat() }, modifier = Modifier.semantics {
                contentDescription = repeatDescription; stateDescription = repeatState
            }) {
                FigmaIcon(R.drawable.ic_repeat, Modifier.alpha(if (state.repeat) 1f else AppOpacity.MEDIUM))
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            song.storeUrl?.let { url ->
                TextButton(onClick = { onStore(url) }, contentPadding = PaddingValues(horizontal = AppSpacing.medium, vertical = AppSpacing.none)) {
                    Text(stringResource(R.string.view_itunes), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(stringResource(R.string.itunes_attribution), style = MaterialTheme.typography.bodySmall, color = MutedText)
            if (historyFailed) Text(stringResource(R.string.history_failed), style = MaterialTheme.typography.bodySmall, color = MutedText)
        }
    }
}

/** Only this composition observes the 250 ms playback clock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerTimeline(state: PlaybackState, progress: StateFlow<PlaybackProgress>?, onSeek: (Long) -> Unit) {
    val clock = progress?.collectAsStateWithLifecycle()?.value ?: PlaybackProgress(state.positionMs, state.durationMs)
    val songId = state.song?.id
    var seekFraction by remember(songId) { mutableStateOf<Float?>(null) }
    val fraction = if (clock.durationMs > 0) clock.positionMs.toFloat() / clock.durationMs else 0f
    val animatedFraction by animateFloatAsState(fraction.coerceIn(0f, 1f),
        animationSpec = if (state.playing) tween(PlaybackPolicy.PROGRESS_INTERVAL_MS, easing = LinearEasing) else snap(), label = UiAnimationLabels.PREVIEW_PROGRESS)
    val position = seekFraction?.let { (it * clock.durationMs).toLong() } ?: clock.positionMs
    val timelineDescription = stringResource(R.string.preview_timeline)
    val locale = LocalConfiguration.current.locales[0]
    val resources = LocalResources.current
    // Animation recomposes this scope each frame; format labels only when whole seconds change.
    val elapsed = remember(wholeSeconds(position), locale) { formatTime(position, locale) }
    val remainingMs = (clock.durationMs - position).coerceAtLeast(0)
    val remaining = remember(wholeSeconds(remainingMs), locale) { formatTime(remainingMs, locale) }
    val duration = remember(wholeSeconds(clock.durationMs), locale) { formatTime(clock.durationMs, locale) }
    val timelineState = remember(resources, locale, elapsed, duration) {
        resources.getString(R.string.preview_position, elapsed, duration)
    }
    val remainingText = remember(resources, locale, remaining) {
        resources.getString(R.string.time_remaining, remaining)
    }
    Column {
        Slider(value = (seekFraction ?: animatedFraction).coerceIn(0f, 1f),
            onValueChange = { seekFraction = it },
            onValueChangeFinished = { seekFraction?.let { onSeek((it * clock.durationMs).toLong()) }; seekFraction = null },
            enabled = clock.durationMs > 0 && !state.failed,
            modifier = Modifier.fillMaxWidth().heightIn(min = AppSizes.touchTarget).semantics {
                contentDescription = timelineDescription
                stateDescription = timelineState
            },
            // Slider semantics follow its measured content, not just outer minimum-size padding.
            thumb = { Box(Modifier.size(width = AppSizes.timelineThumb, height = AppSizes.touchTarget), contentAlignment = Alignment.Center) {
                Box(Modifier.size(AppSizes.timelineThumb).background(Color.White, CircleShape))
            } },
            track = { slider ->
                Box(Modifier.fillMaxWidth().height(AppSizes.timelineTrack).clip(CircleShape).background(Color.White.copy(alpha = AppOpacity.SUBTLE))) {
                    Box(Modifier.fillMaxWidth(slider.value).fillMaxHeight().background(Color.White.copy(alpha = AppOpacity.MEDIUM)))
                }
            })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(elapsed, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr), color = MutedText)
            Text(remainingText,
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr), color = MutedText)
        }
    }
}
