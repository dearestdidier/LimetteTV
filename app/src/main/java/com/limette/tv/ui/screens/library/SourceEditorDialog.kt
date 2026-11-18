package com.limette.tv.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.limette.tv.domain.model.MediaSource
import com.limette.tv.ui.theme.PrimaryYellow
import com.limette.tv.ui.theme.SurfaceDark
import com.limette.tv.ui.theme.TextMuted
import com.limette.tv.ui.theme.TextPrimary
import java.util.UUID

/** 新增 / 编辑媒体源：SMB、WebDAV、飞牛影视三套表单。 */
@Composable
fun SourceEditorDialog(
    initial: MediaSource?,
    onDismiss: () -> Unit,
    onTest: (MediaSource) -> Unit,
    onSave: (MediaSource) -> Unit
) {
    var type by remember { mutableStateOf(initial?.type ?: MediaSource.SourceType.SMB) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var host by remember { mutableStateOf(initial?.host.orEmpty()) }
    var share by remember { mutableStateOf(initial?.shareName.orEmpty()) }
    var domain by remember { mutableStateOf(initial?.domain.orEmpty()) }
    var smbRoot by remember { mutableStateOf(initial?.smbRootPath.orEmpty()) }
    var protocol by remember {
        mutableStateOf(if (initial?.protocol == "https") "https" else "http")
    }
    var port by remember {
        mutableStateOf(
            (initial?.port?.takeIf { it > 0 } ?: when (initial?.type) {
                MediaSource.SourceType.FENIU -> 5666
                MediaSource.SourceType.SMB -> 445
                else -> 8080
            }).toString()
        )
    }
    var path by remember { mutableStateOf(initial?.path?.takeIf { it != "/" }.orEmpty()) }
    var username by remember { mutableStateOf(initial?.username.orEmpty()) }
    var password by remember { mutableStateOf(initial?.password.orEmpty()) }

    fun defaultPort(): Int = when (type) {
        MediaSource.SourceType.FENIU -> 5666
        MediaSource.SourceType.SMB -> 445
        MediaSource.SourceType.WEBDAV -> if (protocol == "https") 443 else 80
    }

    fun draft(): MediaSource = MediaSource(
        id = initial?.id ?: UUID.randomUUID().toString(),
        name = name.trim().ifBlank {
            when (type) {
                MediaSource.SourceType.SMB -> "SMB-${host.trim()}"
                MediaSource.SourceType.WEBDAV -> "WebDAV-${host.trim()}"
                MediaSource.SourceType.FENIU -> "飞牛影视-${host.trim()}"
            }
        },
        type = type,
        enabled = initial?.enabled ?: true,
        protocol = if (type == MediaSource.SourceType.SMB) "http" else protocol,
        host = host.trim(),
        port = port.toIntOrNull() ?: defaultPort(),
        path = if (type == MediaSource.SourceType.WEBDAV) "/" + path.trim().trim('/') else "/",
        username = username.trim(),
        password = password,
        shareName = if (type == MediaSource.SourceType.SMB) share.trim().trim('/') else "",
        domain = if (type == MediaSource.SourceType.SMB) domain.trim() else "",
        smbRootPath = if (type == MediaSource.SourceType.SMB) smbRoot.trim().trim('/') else "",
        selectedPaths = initial?.selectedPaths ?: emptyList(),
        createdAt = initial?.createdAt ?: System.currentTimeMillis()
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .width(600.dp)
                .heightIn(max = 660.dp)
                .background(SurfaceDark, RoundedCornerShape(14.dp))
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (initial == null) "添加媒体源" else "编辑媒体源",
                style = MaterialTheme.typography.titleLarge, color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text("所有连接信息只保存在本机，不会上传。", color = TextMuted,
                style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeButton("SMB 共享", type == MediaSource.SourceType.SMB) {
                    type = MediaSource.SourceType.SMB
                    port = "445"
                }
                TypeButton("WebDAV", type == MediaSource.SourceType.WEBDAV) {
                    type = MediaSource.SourceType.WEBDAV
                    port = "8080"
                }
                TypeButton("飞牛影视", type == MediaSource.SourceType.FENIU) {
                    type = MediaSource.SourceType.FENIU
                    port = "5666"
                }
            }

            Spacer(Modifier.height(14.dp))
            LabeledField("名称（自定义）", name) { name = it }

            if (type == MediaSource.SourceType.FENIU) {
                LabeledField("服务地址 / IP", host, "例如 192.168.1.10") { host = it }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { protocol = if (protocol == "http") "https" else "http" },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2a2a2e), contentColor = PrimaryYellow)
                    ) { Text(protocol.uppercase()) }
                    LabeledField("端口", port, "默认 5666", Modifier.width(160.dp)) {
                        port = it.filter { c -> c.isDigit() }
                    }
                }
                Text(
                    "填写飞牛 NAS 上「影视」服务的地址与端口，App 会登录并读取已刮削的影视库。",
                    color = TextMuted, style = MaterialTheme.typography.bodySmall
                )
            } else {
                LabeledField("服务器地址 / IP", host, "例如 192.168.1.10") { host = it }
            }

            if (type == MediaSource.SourceType.SMB) {
                LabeledField("共享名", share, "例如 media 或 电影") { share = it }
                LabeledField("根目录（可选）", smbRoot, "留空表示扫描整个共享") { smbRoot = it }
                LabeledField("域（可选）", domain, "一般留空即可") { domain = it }
            } else if (type == MediaSource.SourceType.WEBDAV) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { protocol = if (protocol == "http") "https" else "http" },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2a2a2e), contentColor = PrimaryYellow)
                    ) { Text(protocol.uppercase()) }
                    LabeledField("端口", port, "例如 8080 / 5666", Modifier.width(160.dp)) {
                        port = it.filter { c -> c.isDigit() }
                    }
                }
                LabeledField("路径（可选）", path, "例如 dav 或 媒体/电影") { path = it }
            }

            LabeledField("用户名", username) { username = it }
            LabeledField("密码", password) { password = it }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onTest(draft()) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2a2a2e), contentColor = TextPrimary)
                ) { Text("测试连接") }
                Button(
                    onClick = { onSave(draft()) },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryYellow, contentColor = Color.Black)
                ) { Text("保存") }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2a2a2e), contentColor = TextPrimary)
                ) { Text("取消") }
            }
        }
    }
}

@Composable
private fun TypeButton(label: String, active: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) PrimaryYellow else Color(0xFF2a2a2e),
            contentColor = if (active) Color.Black else TextPrimary
        )
    ) { Text(label) }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    hint: String = "",
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (hint.isBlank()) null else { { Text(hint, color = TextMuted) } },
        singleLine = true,
        modifier = modifier.fillMaxWidth().padding(vertical = 5.dp)
    )
}
