package com.example.moisessongs.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.moisessongs.R
import com.example.moisessongs.domain.Song
import com.example.moisessongs.ui.theme.AppCorners
import com.example.moisessongs.ui.theme.AppSizes
import com.example.moisessongs.ui.theme.AppSpacing
import com.example.moisessongs.ui.theme.SheetSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsSheet(song: Song, onDismiss: () -> Unit, onAlbum: (Long) -> Unit) {
    val optionsTitle = stringResource(R.string.song_options)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetSurface,
        modifier = Modifier.semantics { paneTitle = optionsTitle },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = AppCorners.sheet, topEnd = AppCorners.sheet),
        dragHandle = { Box(Modifier.fillMaxWidth().height(AppSizes.sheetHandleArea), contentAlignment = Alignment.Center) {
            FigmaIcon(R.drawable.ic_grabber, width = AppSizes.sheetHandleWidth, height = AppSizes.sheetHandleHeight)
        } }) {
        Column(Modifier.fillMaxWidth().padding(bottom = AppSpacing.extraLarge), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = AppSpacing.screen, vertical = AppSpacing.small))
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = AppSpacing.screen))
            Spacer(Modifier.height(AppSpacing.screen))
            Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen)
                .clickable(enabled = song.albumId != null, role = Role.Button) { song.albumId?.let(onAlbum) }
                .heightIn(min = AppSizes.sheetAction).padding(horizontal = AppSpacing.small),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.regular)) {
                FigmaIcon(R.drawable.ic_album)
                Text(stringResource(if (song.albumId != null) R.string.view_album else R.string.album_unavailable),
                    style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
