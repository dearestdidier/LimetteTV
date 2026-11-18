package com.limette.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.limette.tv.R

/**
 * 视频封面。永远不会有黑图：
 *  - 有 URL：加载飞牛影视缩略图 / NAS 本地封面，底层始终垫一张品牌渐变卡片
 *  - 加载失败或无 URL：直接显示渐变卡片（带片名与柠檬图标）
 */
@Composable
fun MediaPoster(
    title: String,
    posterUrl: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    var failed by remember(posterUrl) { mutableStateOf(false) }

    if (posterUrl.isNullOrBlank() || failed) {
        GradientPoster(title = title, modifier = modifier)
        return
    }

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(posterUrl)
            .crossfade(true)
            .build()
    )
    val state = painter.state
    LaunchedEffect(state) {
        if (state is AsyncImagePainter.State.Error) failed = true
    }

    Box(modifier = modifier.clip(RoundedCornerShape(8.dp))) {
        // 加载期间也显示渐变卡片，避免任何一帧黑图
        GradientPoster(title = title, modifier = Modifier.matchParentSize())
        Image(
            painter = painter,
            contentDescription = title,
            contentScale = contentScale,
            modifier = Modifier.matchParentSize()
        )
    }
}

@Composable
fun GradientPoster(title: String, modifier: Modifier = Modifier) {
    val seed = (title.hashCode() and 0x7FFFFFFF) % 5
    val palettes = listOf(
        listOf(Color(0xFF4FC3A1), Color(0xFF1B6E63)),
        listOf(Color(0xFFFDDD0E), Color(0xFF9A7A05)),
        listOf(Color(0xFF7C6CF0), Color(0xFF2A2360)),
        listOf(Color(0xFFF28A5C), Color(0xFF7A3A2A)),
        listOf(Color(0xFF43A6E8), Color(0xFF17436B))
    )
    val colors = palettes[seed]
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo_round),
            contentDescription = null,
            modifier = Modifier.size(84.dp),
            alpha = 0.22f
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 3,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 14.dp)
        )
    }
}
