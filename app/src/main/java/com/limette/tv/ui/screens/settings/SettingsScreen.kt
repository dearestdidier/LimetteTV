package com.limette.tv.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.window.Dialog
import com.limette.tv.BuildConfig
import com.limette.tv.R
import com.limette.tv.domain.model.MediaSource
import com.limette.tv.ui.MainViewModel
import com.limette.tv.ui.components.ScreenHeader
import com.limette.tv.ui.components.SettingSwitchRow
import com.limette.tv.ui.screens.library.SourceEditorDialog
import com.limette.tv.ui.theme.BackgroundDark
import com.limette.tv.ui.theme.PrimaryYellow
import com.limette.tv.ui.theme.SurfaceDark
import com.limette.tv.ui.theme.TextMuted
import com.limette.tv.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val rememberPosition by viewModel.rememberPlaybackPosition.collectAsState(initial = true)
    val autoSkip by viewModel.autoSkipIntroOutro.collectAsState(initial = true)
    val seekSeconds by viewModel.seekDurationSeconds.collectAsState(initial = 15)
    val sources by viewModel.sources.collectAsState()
    val scope = rememberCoroutineScope()

    var hint by remember { mutableStateOf<String?>(null) }
    var libraryHint by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<MediaSource?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<MediaSource?>(null) }

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

            // ---------- 媒体库设置 ----------
            SectionCard("媒体库设置") {
                MediaLibrarySettingsSection(
                    sources = sources,
                    libraryHint = libraryHint,
                    onAdd = { editor = null; showEditor = true },
                    onEdit = { source -> editor = source; showEditor = true },
                    onDelete = { source -> confirmDelete = source }
                )
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

    if (showEditor) {
        SourceEditorDialog(
            initial = editor,
            onDismiss = { showEditor = false },
            onTest = { draft ->
                scope.launch {
                    val result = viewModel.testSource(draft)
                    libraryHint = result.fold(
                        onSuccess = { "连接成功，发现 $it 个条目" },
                        onFailure = { "连接失败：${it.message}" }
                    )
                }
                Unit
            },
            onSave = { draft ->
                scope.launch {
                    viewModel.addOrUpdateSource(draft)
                    libraryHint = "已保存「${draft.name}」"
                    showEditor = false
                }
            }
        )
    }

    confirmDelete?.let { target ->
        Dialog(onDismissRequest = { confirmDelete = null }) {
            Column(
                modifier = Modifier
                    .background(SurfaceDark, RoundedCornerShape(12.dp))
                    .padding(24.dp)
            ) {
                Text("删除媒体源？", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text("「${target.name}」及其已索引的视频将从媒体库中移除。",
                    color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(20.dp))
                Row {
                    Button(onClick = { confirmDelete = null },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = TextPrimary)) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = {
                        scope.launch { viewModel.removeSource(target.id) }
                        libraryHint = "已删除「${target.name}」"
                        confirmDelete = null
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFef4444))) {
                        Text("删除")
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaLibrarySettingsSection(
    sources: List<MediaSource>,
    libraryHint: String?,
    onAdd: () -> Unit,
    onEdit: (MediaSource) -> Unit,
    onDelete: (MediaSource) -> Unit
) {
    val enabledSources = sources.filter { it.enabled }

    Button(
        onClick = onAdd,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow, contentColor = Color.Black)
    ) {
        Icon(Icons.Default.Add, null)
        Spacer(Modifier.width(8.dp))
        Text("添加媒体源")
    }
    Spacer(Modifier.height(10.dp))

    if (enabledSources.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Folder, "无媒体源", modifier = Modifier.size(48.dp).padding(bottom = 12.dp), tint = TextMuted)
            Text("暂无已启用的媒体源", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("点击上方「添加媒体源」添加 SMB、WebDAV 或飞牛影视源", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            enabledSources.forEach { source ->
                SourceDirectoryCard(source = source, onEdit = { onEdit(source) }, onDelete = { onDelete(source) })
                Spacer(Modifier.height(12.dp))
            }

            val totalSources = sources.size
            val enabledCount = enabledSources.size
            val disabledCount = totalSources - enabledCount

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "共 $totalSources 个媒体源 · 已启用 $enabledCount 个 · 未启用 $disabledCount 个",
                    style = MaterialTheme.typography.bodySmall, color = TextMuted
                )
            }
        }
    }

    if (!libraryHint.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(libraryHint, color = PrimaryYellow, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SourceDirectoryCard(
    source: MediaSource,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(PrimaryYellow.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            source.typeLabel(),
                            color = PrimaryYellow,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(source.displayUrl(), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { expanded = !expanded },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = PrimaryYellow)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "折叠" else "展开",
                        tint = PrimaryYellow,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = TextPrimary)
                ) {
                    Icon(Icons.Default.Edit, "编辑", tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color(0xFFef4444))
                ) {
                    Icon(Icons.Default.Delete, "删除", tint = Color(0xFFef4444), modifier = Modifier.size(20.dp))
                }
            }
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                InfoRow("服务器地址", source.host)
                when (source.type) {
                    MediaSource.SourceType.SMB -> {
                        InfoRow("共享名", source.shareName)
                        if (source.smbRootPath.isNotBlank()) InfoRow("根目录", source.smbRootPath)
                    }
                    MediaSource.SourceType.WEBDAV -> InfoRow("路径", source.path)
                    MediaSource.SourceType.FENIU -> InfoRow("FN ID", source.username)
                }
                InfoRow("用户名", source.username)
                InfoRow("端口", source.port.toString())
                InfoRow("协议", source.protocol.uppercase())
                Spacer(Modifier.height(8.dp))
                InfoRow("状态", if (source.enabled) "已启用" else "未启用")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Text(value?.ifEmpty { "未设置" } ?: "未设置", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
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
