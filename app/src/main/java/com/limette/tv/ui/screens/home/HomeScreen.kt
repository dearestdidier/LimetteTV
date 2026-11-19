package com.limette.tv.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.limette.tv.R
import com.limette.tv.data.local.database.entity.WebDavMediaEntity
import com.limette.tv.data.repository.ScanState
import com.limette.tv.ui.MainViewModel
import com.limette.tv.ui.components.FocusableBox
import com.limette.tv.ui.components.MediaPoster
import com.limette.tv.ui.components.SectionTitle
import com.limette.tv.ui.components.TopAction
import com.limette.tv.ui.theme.BackgroundDark
import com.limette.tv.ui.theme.PrimaryYellow
import com.limette.tv.ui.theme.SuccessGreen
import com.limette.tv.ui.theme.SurfaceDark
import com.limette.tv.ui.theme.TextMuted
import com.limette.tv.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onPlay: (WebDavMediaEntity, Long) -> Unit
) {
    val videos by viewModel.allVideos.collectAsState()
    val recent by viewModel.recentHistory.collectAsState()
    val scan by viewModel.scanState.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Toast 消息收集
    var toastMessage: String? by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        for (msg in viewModel.toastMessage) {
            toastMessage = msg
            kotlinx.coroutines.delay(3000)
            toastMessage = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {

        // Toast 消息显示
        toastMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .background(SurfaceDark)
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text(msg, style = MaterialTheme.typography.bodyMedium, color = PrimaryYellow)
                }
            }
        }

        // ---- 顶栏：圆形图标 + Limette TV ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo_round),
                contentDescription = "Limette TV",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(46.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Limette TV",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = "本地 NAS 播放器",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(Modifier.weight(1f))
            TopAction(Icons.Default.Refresh, "刷新", viewModel::refresh)
            Spacer(Modifier.width(10.dp))
            TopAction(Icons.Default.Search, "搜索", onOpenSearch)
            Spacer(Modifier.width(10.dp))
            TopAction(Icons.Default.Favorite, "收藏", onOpenFavorites)
            Spacer(Modifier.width(10.dp))
            TopAction(Icons.Default.Settings, "设置", onOpenSettings)
        }

        when (val state = scan) {
            is ScanState.Running -> Text(
                text = "正在扫描 ${state.currentSource} · 已找到 ${state.found} 个视频…",
                color = PrimaryYellow,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 28.dp)
            )
            is ScanState.Failed -> Text(
                text = "扫描失败：${state.message}",
                color = Color(0xFFef4444),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 28.dp)
            )
            is ScanState.SourceError -> Text(
                text = "源「${state.sourceName}」：${state.message}",
                color = Color(0xFFef4444),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 28.dp)
            )
            is ScanState.Done -> Text(
                text = "扫描完成，共找到 ${state.total} 个视频",
                color = SuccessGreen,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 28.dp)
            )
            else -> Unit
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (videos.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_logo_round),
                        contentDescription = null,
                        modifier = Modifier.size(96.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text("媒体库中还没有视频", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "添加 SMB / WebDAV / 飞牛影视媒体源后，首页会直接显示全部视频文件",
                        style = MaterialTheme.typography.bodyMedium, color = TextMuted
                    )
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onOpenLibrary,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryYellow, contentColor = Color.Black
                            )
                        ) { Text("添加媒体源") }
                        Button(
                            onClick = { viewModel.refresh() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceDark, contentColor = TextPrimary
                            )
                        ) { Text("重新扫描") }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(148.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (recent.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("继续观看") }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(recent, key = { it.id }) { item ->
                                    FocusableBox(
                                        onClick = {
                                            scope.launch {
                                                viewModel.findMedia(item.mediaId)?.let { entity ->
                                                    onPlay(entity, item.progress)
                                                }
                                            }
                                        },
                                        modifier = Modifier.width(220.dp)
                                    ) {
                                        Column {
                                            MediaPoster(
                                                title = item.title,
                                                posterUrl = item.posterUrl,
                                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                item.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium, color = TextPrimary
                                            )
                                            if (item.duration > 0) {
                                                LinearProgressIndicator(
                                                    progress = (item.progress.toFloat() / item.duration).coerceIn(0f, 1f),
                                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                                    color = PrimaryYellow,
                                                    trackColor = SurfaceDark
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionTitle(
                            text = "全部视频",
                            trailing = "共 ${videos.size} 个文件 · 来自所有已启用媒体源"
                        )
                    }

                    items(videos, key = { it.id }) { media ->
                        VideoCard(
                            media = media,
                            onPlay = {
                                scope.launch {
                                    onPlay(media, viewModel.startPositionFor(media.id))
                                }
                            },
                            onDetail = { onOpenDetail(media.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCard(
    media: WebDavMediaEntity,
    onPlay: () -> Unit,
    onDetail: () -> Unit
) {
    FocusableBox(onClick = onPlay, modifier = Modifier.fillMaxWidth()) { focused ->
        Column(modifier = Modifier.padding(8.dp)) {
            Box {
                MediaPoster(
                    title = media.title,
                    posterUrl = media.posterUrl,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                )
                if (focused) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                            .background(PrimaryYellow)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("▶ 播放", color = Color.Black, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = media.title,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, color = TextPrimary
            )
            Text(
                text = media.fileName,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall, color = TextMuted
            )
            if (focused) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "查看媒体详情 ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryYellow,
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .background(PrimaryYellow.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clickable(onClick = onDetail)
                )
            }
        }
    }
}
