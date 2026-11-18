package com.limette.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.limette.tv.data.local.database.entity.WebDavMediaEntity
import com.limette.tv.ui.MainViewModel
import com.limette.tv.ui.components.MediaPoster
import com.limette.tv.ui.components.ScreenHeader
import com.limette.tv.ui.theme.BackgroundDark
import com.limette.tv.ui.theme.PrimaryYellow
import com.limette.tv.ui.theme.SurfaceDark
import com.limette.tv.ui.theme.TextMuted
import com.limette.tv.ui.theme.TextPrimary

@Composable
fun DetailScreen(
    viewModel: MainViewModel,
    mediaId: String,
    onBack: () -> Unit,
    onPlay: (WebDavMediaEntity, Long) -> Unit
) {
    var media by remember { mutableStateOf<WebDavMediaEntity?>(null) }
    var startPosition by remember { mutableStateOf(0L) }
    var favorite by remember { mutableStateOf(false) }

    LaunchedEffect(mediaId) {
        media = viewModel.findMedia(mediaId)
        startPosition = viewModel.startPositionFor(mediaId)
        favorite = viewModel.isFavorite(mediaId)
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        ScreenHeader("媒体详情", media?.fileName, onBack)
        val item = media
        if (item == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("未找到该视频", color = TextMuted)
            }
            return@Column
        }

        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
            MediaPoster(
                title = item.title,
                posterUrl = item.posterUrl,
                modifier = Modifier
                    .fillMaxHeight(0.78f)
                    .aspectRatio(2f / 3f)
                    .align(Alignment.CenterVertically)
            )
            Spacer(Modifier.width(28.dp))
            Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.CenterVertically)
            ) {
                Text(item.title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                DetailLine("文件名", item.fileName)
                DetailLine("媒体源", item.libraryId)
                DetailLine("协议", item.source)
                DetailLine("大小", formatSize(item.fileSize))
                item.year?.let { DetailLine("年份", it.toString()) }
                Spacer(Modifier.height(22.dp))
                Row {
                    Button(
                        onClick = { onPlay(item, startPosition) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryYellow, contentColor = Color.Black
                        )
                    ) {
                        Text(if (startPosition > 0) "继续播放" else "开始播放")
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            viewModel.toggleFavoriteInScope(item) { favorite = it }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = TextPrimary)
                    ) { Text(if (favorite) "取消收藏" else "收藏") }
                }
                if (startPosition > 0) {
                    Spacer(Modifier.height(10.dp))
                    Text("上次观看到 ${formatDuration(startPosition)}", color = TextMuted,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text("$label：", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "未知"
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024f / 1024f)
    else -> "%.2f GB".format(bytes / 1024f / 1024f / 1024f)
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    return "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
}
