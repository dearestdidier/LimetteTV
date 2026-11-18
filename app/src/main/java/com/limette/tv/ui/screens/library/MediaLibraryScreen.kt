package com.limette.tv.ui.screens.library

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.limette.tv.domain.model.MediaSource
import com.limette.tv.ui.MainViewModel
import com.limette.tv.ui.components.FocusableBox
import com.limette.tv.ui.components.ScreenHeader
import com.limette.tv.ui.components.TopAction
import com.limette.tv.ui.theme.BackgroundDark
import com.limette.tv.ui.theme.PrimaryYellow
import com.limette.tv.ui.theme.SurfaceDark
import com.limette.tv.ui.theme.TextMuted
import com.limette.tv.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun MediaLibraryScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val sources by viewModel.sources.collectAsState()
    val scope = rememberCoroutineScope()
    var editor by remember { mutableStateOf<MediaSource?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<MediaSource?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        ScreenHeader("媒体库", "可同时启用 SMB / WebDAV / 飞牛影视媒体源，首页聚合展示全部视频", onBack)

        Row(modifier = Modifier.padding(horizontal = 28.dp)) {
            Button(
                onClick = { editor = null; showEditor = true },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow, contentColor = Color.Black)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("添加媒体源")
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = viewModel::refresh,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = TextPrimary)) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text("重新扫描")
            }
            Spacer(Modifier.width(16.dp))
            if (message != null) {
                Text(message.orEmpty(), color = PrimaryYellow, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterVertically))
            }
        }

        Spacer(Modifier.height(12.dp))

        if (sources.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有添加媒体源", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("支持 SMB 共享、WebDAV 与飞牛影视账号，可同时添加多个", color = TextMuted)
                }
            }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 28.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sources, key = { it.id }) { source ->
                    SourceRow(
                        source = source,
                        onToggle = { enabled ->
                            scope.launch { viewModel.setSourceEnabled(source.id, enabled) }
                        },
                        onEdit = { editor = source; showEditor = true },
                        onDelete = { confirmDelete = source }
                    )
                }
            }
        }
    }

    if (showEditor) {
        SourceEditorDialog(
            initial = editor,
            onDismiss = { showEditor = false },
            onTest = { draft ->
                scope.launch {
                    val result = viewModel.testSource(draft)
                    message = result.fold(
                        onSuccess = { "连接成功，发现 $it 个条目" },
                        onFailure = { "连接失败：${it.message}" }
                    )
                }
                Unit
            },
            onSave = { draft ->
                scope.launch {
                    viewModel.addOrUpdateSource(draft)
                    message = "已保存「${draft.name}」"
                    showEditor = false
                }
            }
        )
    }

    confirmDelete?.let { target ->
        Dialog(onDismissRequest = { confirmDelete = null }) {
            Column(
                modifier = Modifier
                    .background(SurfaceDark, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
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
private fun SourceRow(
    source: MediaSource,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    FocusableBox(onClick = { onToggle(!source.enabled) }, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        source.typeLabel(),
                        color = PrimaryYellow,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .background(PrimaryYellow.copy(alpha = 0.15f),
                                androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(source.displayUrl(), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Text(if (source.enabled) "已启用" else "未启用", color = if (source.enabled) PrimaryYellow else TextMuted,
                style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = source.enabled, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = PrimaryYellow)
            )
            Spacer(Modifier.width(12.dp))
            TopAction(Icons.Default.Edit, "编辑", onEdit)
            Spacer(Modifier.width(8.dp))
            TopAction(Icons.Default.Delete, "删除", onDelete)
        }
    }
}
