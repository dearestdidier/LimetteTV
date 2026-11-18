package com.limette.tv.ui.screens.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.limette.tv.data.local.database.entity.WebDavMediaEntity
import com.limette.tv.domain.service.FavoriteItem
import com.limette.tv.ui.MainViewModel
import com.limette.tv.ui.components.FocusableBox
import com.limette.tv.ui.components.MediaPoster
import com.limette.tv.ui.components.ScreenHeader
import com.limette.tv.ui.theme.BackgroundDark
import com.limette.tv.ui.theme.TextMuted
import com.limette.tv.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onPlay: (WebDavMediaEntity, Long) -> Unit
) {
    val favorites by viewModel.favorites.collectAsState()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        ScreenHeader("我的收藏", "共 ${favorites.size} 项", onBack)
        if (favorites.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有收藏任何视频", color = TextMuted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(148.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(favorites, key = { it.mediaId }) { item ->
                    FavoriteTile(item) {
                        scope.launch {
                            viewModel.findMedia(item.mediaId)?.let { entity ->
                                onPlay(entity, viewModel.startPositionFor(entity.id))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteTile(item: FavoriteItem, onClick: () -> Unit) {
    FocusableBox(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            MediaPoster(
                title = item.title,
                posterUrl = item.coverImageUrl(),
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
            )
            Spacer(Modifier.height(8.dp))
            Text(item.title, maxLines = 2, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
    }
}
