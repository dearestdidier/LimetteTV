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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.limette.tv.BuildConfig
import com.limette.tv.R
import com.limette.tv.data.preferences.FeiNiuConfig
import com.limette.tv.ui.MainViewModel
import com.limette.tv.ui.components.ScreenHeader
import com.limette.tv.ui.components.SettingSwitchRow
import com.limette.tv.ui.theme.BackgroundDark
import com.limette.tv.ui.theme.PrimaryYellow
import com.limette.tv.ui.theme.SurfaceDark
import com.limette.tv.ui.theme.TextMuted
import com.limette.tv.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val config by viewModel.feiNiuConfig.collectAsState()
    val rememberPosition by viewModel.rememberPlaybackPosition.collectAsState(initial = true)
    val autoSkip by viewModel.autoSkipIntroOutro.collectAsState(initial = true)
    val seekSeconds by viewModel.seekDurationSeconds.collectAsState(initial = 15)

    var enabled by remember(config) { mutableStateOf(config.enabled) }
    var baseUrl by remember(config) { mutableStateOf(config.baseUrl) }
    var username by remember(config) { mutableStateOf(config.username) }
    var password by remember(config) { mutableStateOf(config.password) }
    var hint by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("设置", "纯本地 NAS 播放 · 不依赖任何在线刮削服务", onBack)

        Column(modifier = Modifier.padding(horizontal = 28.dp)) {

            // ---------- 飞牛影视缩略图 ----------
            SectionCard("飞牛影视缩略图（可选）") {
                Text(
                    "填写 NAS 上飞牛影视的地址与账号，App 会读取已刮削好的海报作为首页封面。" +
                        "不配置时自动使用 NAS 目录内封面图或品牌渐变卡片，首页不会出现黑图。",
                    color = TextMuted, style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = enabled, onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black, checkedTrackColor = PrimaryYellow
                        )
                    )
                }
                Spacer(Modifier.height(8.dp))
                SettingsField("服务地址", baseUrl, "例如 http://192.168.1.10:5666") { baseUrl = it }
                SettingsField("账号", username, "飞牛影视登录账号") { username = it }
                SettingsField("密码", password, "飞牛影视登录密码") { password = it }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            scope.launch {
                                viewModel.saveFeiNiuConfig(
                                    FeiNiuConfig(enabled, baseUrl, username, password)
                                )
                                hint = "已保存，正在刷新缩略图索引"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryYellow, contentColor = Color.Black
                        )
                    ) { Text("保存并刷新") }
                    Spacer(Modifier.width(14.dp))
                    if (hint != null) {
                        Text(hint.orEmpty(), color = PrimaryYellow, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

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
                            "适配飞牛 OS 的纯本地 NAS 播放器 · SMB / WebDAV · 局域网内完成全部读取",
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

@Composable
private fun SettingsField(label: String, value: String, hint: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(hint, color = TextMuted) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    )
}
