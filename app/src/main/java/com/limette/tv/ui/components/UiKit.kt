package com.limette.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.limette.tv.ui.theme.PrimaryYellow
import com.limette.tv.ui.theme.SurfaceDark
import com.limette.tv.ui.theme.TextMuted
import com.limette.tv.ui.theme.TextPrimary

/** 遥控器友好的可聚焦卡片，聚焦时高亮描边。 */
@Composable
fun FocusableBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    backgroundColor: Color = SurfaceDark,
    content: @Composable BoxScope.(focused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (focused) PrimaryYellow.copy(alpha = 0.16f) else backgroundColor)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) PrimaryYellow else Color.White.copy(alpha = 0.08f),
                shape = shape
            )
            .clickable { onClick() }
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        content = { content(focused) }
    )
}

@Composable
fun SectionTitle(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        if (!trailing.isNullOrBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(text = trailing, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

@Composable
fun TopAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (focused) PrimaryYellow else SurfaceDark)
            .clickable { onClick() }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (focused) Color.Black else TextPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = if (focused) Color.Black else TextPrimary,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FocusableBox(onClick = onBack, modifier = Modifier.size(44.dp)) {
            Text("‹", color = TextPrimary, style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            if (!subtitle.isNullOrBlank()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) PrimaryYellow.copy(alpha = 0.12f) else SurfaceDark)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = PrimaryYellow)
        )
    }
}

@Composable
fun BottomNav(items: List<Pair<String, ImageVector>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF101014))
            .padding(horizontal = 28.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, (label, icon) ->
            val active = index == selected
            var focused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        when {
                            focused -> PrimaryYellow
                            active -> PrimaryYellow.copy(alpha = 0.18f)
                            else -> SurfaceDark
                        }
                    )
                    .clickable { onSelect(index) }
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon, contentDescription = label,
                    tint = if (focused) Color.Black else TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    color = if (focused) Color.Black else TextPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
