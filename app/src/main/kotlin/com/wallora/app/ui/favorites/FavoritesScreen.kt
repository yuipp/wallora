package com.wallora.app.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wallora.app.domain.model.Wallpaper
import com.wallora.app.domain.usecase.SaveProgress
import com.wallora.app.ui.components.EmptyState
import com.wallora.app.ui.components.WallpaperThumbCard
import com.wallora.app.ui.components.adaptive.gridColumns

@Composable
fun FavoritesScreen(
    contentPadding: PaddingValues,
    onWallpaperClick: (Wallpaper) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle(emptyList())
    val saveProgress by viewModel.saveProgress.collectAsStateWithLifecycle()
    val columns = gridColumns()

    if (favorites.isEmpty()) {
        EmptyState()
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SaveAllBar(
                    count = favorites.size,
                    progress = saveProgress,
                    onSave = { viewModel.saveAll(favorites) },
                    onDismiss = viewModel::dismissSaveResult,
                )
            }
            items(favorites, key = { it.globalKey }) { wallpaper ->
                WallpaperThumbCard(
                    wallpaper = wallpaper,
                    onClick = { onWallpaperClick(wallpaper) },
                )
            }
        }
    }
}

/** Header above the grid: "Save all" button, running progress, or the finished-run summary. */
@Composable
private fun SaveAllBar(
    count: Int,
    progress: SaveProgress?,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (progress == null) {
            FilledTonalButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Save all to gallery ($count)")
            }
        } else if (progress.running) {
            Text(
                "Saving ${progress.done}/${progress.total}…",
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { progress.done.toFloat() / progress.total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    buildString {
                        append("Saved ${progress.saved}")
                        if (progress.skipped > 0) append(", already there ${progress.skipped}")
                        if (progress.failed > 0) append(", failed ${progress.failed}")
                        append(" — Pictures/Wallora")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("OK") }
            }
        }
    }
}
