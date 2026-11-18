package com.limette.tv.ui.screens.search

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.limette.tv.data.local.database.entity.WebDavMediaEntity
import com.limette.tv.ui.MainViewModel
import com.limette.tv.ui.components.FocusableBox
import com.limette.tv.ui.components.MediaPoster
import com.limette.tv.ui.components.ScreenHeader
import com.limette.tv.ui.theme.BackgroundDark
import com.limette.tv.ui.theme.TextMuted
import com.limette.tv.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onPlay: (WebDavMediaEntity, Long) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val videos by viewModel.allVideos.collectAsState()
    val scope = rememberCoroutineScope()
    val results = remember(query, videos) {
        val q = query.trim().lowercase()
        if (q.isBlank()) emptyList()
        else videos.filter {
            it.title.lowercase().contains(q) || it.fileName.lowercase().contains(q) ||
                it.filePath.lowercase().contains(q)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        ScreenHeader("搜索", "在已添加的媒体源中查找视频文件", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("输入片名或文件名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)
        )
        Spacer(Modifier.height(12.dp))
        if (results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isBlank()) "输入关键词开始搜索" else "没有找到匹配的视频",
                    color = TextMuted
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(148.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(results, key = { it.id }) { media ->
                    FocusableBox(
                        onClick = {
                            scope.launch { onPlay(media, viewModel.startPositionFor(media.id)) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            MediaPoster(
                                title = media.title, posterUrl = media.posterUrl,
                                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(media.title, maxLines = 2, style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}
