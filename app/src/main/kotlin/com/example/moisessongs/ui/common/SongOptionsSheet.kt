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
import androidx.compose.ui.unit.dp
import com.example.moisessongs.R
import com.example.moisessongs.domain.Song
import com.example.moisessongs.ui.theme.SheetSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsSheet(song: Song, onDismiss: () -> Unit, onAlbum: (Long) -> Unit) {
    val optionsTitle = stringResource(R.string.song_options)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetSurface,
        modifier = Modifier.semantics { paneTitle = optionsTitle },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = { Box(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.Center) {
            FigmaIcon(R.drawable.ic_grabber, width = 56.dp, height = 5.dp)
        } }) {
        Column(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                .clickable(enabled = song.albumId != null, role = Role.Button) { song.albumId?.let(onAlbum) }
                .heightIn(min = 56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FigmaIcon(R.drawable.ic_album)
                Text(stringResource(if (song.albumId != null) R.string.view_album else R.string.album_unavailable),
                    style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
