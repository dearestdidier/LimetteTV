package com.limette.tv.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.limette.tv.BuildConfig
import com.limette.tv.R
import com.limette.tv.ui.MainViewModel
import com.limette.tv.ui.components.ScreenHeader
import com.limette.tv.ui.components.SettingSwitchRow
import com.limette.tv.ui.theme.BackgroundDark
import com.limette.tv.ui.theme.PrimaryYellow
import com.limette.tv.ui.theme.SurfaceDark
import com.limette.tv.ui.theme.TextMuted
import com.limette.tv.ui.theme.TextPrimary

@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val rememberPosition by viewModel.rememberPlaybackPosition.collectAsState(initial = true)
    val autoSkip by viewModel.autoSkipIntroOutro.collectAsState(initial = true)
    val seekSeconds by viewModel.seekDurationSeconds.collectAsState(initial = 15)
    var hint by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("设置", "纯本地 NAS 播放 · 局域网内完成全部读取", onBack)

        Column(modifier = Modifier.padding(horizontal = 28.dp)) {

            // ---------- 播放设置 ----------
            SectionCard("播放设置") {
                SettingSwitchRow(
                    title = "记忆续播",
                    subtitle = "自动保存播放进度，下次从断点继续",
                    checked = rememberPosition,
                    onCheckedChange = viewModel::setRememberPlaybackPosition
                )
                SettingSwitchRow(
                    title = "自动跳过片头片尾",
                    subtitle = "按剧集系列记忆跳过时长",
                    checked = autoSkip,
                    onCheckedChange = viewModel::setAutoSkipIntroOutro
                )
                Spacer(Modifier.height(8.dp))
                Text("快进 / 快退时长", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30, 60).forEach { seconds ->
                        val active = seekSeconds == seconds
                        Button(
                            onClick = { viewModel.setSeekDurationSeconds(seconds) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (active) PrimaryYellow else SurfaceDark,
                                contentColor = if (active) Color.Black else TextPrimary
                            )
                        ) { Text("${seconds}s") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.clearWatchHistory()
                        hint = "已清空播放记录"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = TextPrimary)
                ) { Text("清空最近播放记录") }
                if (hint != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(hint.orEmpty(), color = PrimaryYellow, style = MaterialTheme.typography.bodySmall)
                }
            }

            // ---------- 关于 ----------
            SectionCard("关于") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_logo_round),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Limette TV", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Text(
                            "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            color = TextMuted, style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "适配飞牛 OS 的本地 NAS 播放器 · SMB / WebDAV / 飞牛影视",
                            color = TextMuted, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(SurfaceDark, RoundedCornerShape(12.dp))
            .padding(18.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = PrimaryYellow)
        Spacer(Modifier.height(10.dp))
        content()
    }
}
