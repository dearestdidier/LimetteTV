package com.limette.tv.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limette.tv.ui.theme.PrimaryYellow
import com.limette.tv.ui.theme.SurfaceDark
import com.limette.tv.ui.theme.TextMuted
import com.limette.tv.ui.theme.TextPrimary

/** 遥控器友好的可聚焦卡片，聚焦时高亮描边、缩放、光晕效果。 */
@Composable
fun FocusableBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    backgroundColor: Color = SurfaceDark,
    content: @Composable BoxScope.(focused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )
    val elevation by animateFloatAsState(
        targetValue = if (focused) 8f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )
    val borderWidth by animateFloatAsState(
        targetValue = if (focused) 3f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (focused) PrimaryYellow.copy(alpha = 0.2f) else backgroundColor)
            .border(
                width = borderWidth.dp,
                color = if (focused) PrimaryYellow else Color.White.copy(alpha = 0.08f),
                shape = shape
            )
            .clickable { onClick() }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
            },
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
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )
    
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (focused) PrimaryYellow else SurfaceDark)
            .clickable { onClick() }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
            },
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
    val borderWidth by animateFloatAsState(
        targetValue = if (focused) 2f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) PrimaryYellow.copy(alpha = 0.16f) else SurfaceDark)
            .border(
                width = borderWidth.dp,
                color = if (focused) PrimaryYellow else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
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
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black, 
                checkedTrackColor = PrimaryYellow,
                uncheckedThumbColor = if (focused) Color.Black else TextMuted,
                uncheckedTrackColor = if (focused) PrimaryYellow.copy(alpha = 0.5f) else SurfaceDark
            )
        )
    }
}

/**
 * 底部 DOCK 导航栏：居中悬浮、半透明大圆角胶囊背景，与原版 LomenTV 视觉一致。
 * 每个入口采用「图标在上、文字在下」的竖排布局；遥控器聚焦时整项高亮为黄色，带缩放动画。
 */
@Composable
fun BottomNav(items: List<Pair<String, ImageVector>>, selected: Int, onSelect: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(40.dp))
                .background(Color.White.copy(alpha = 0.16f))
                .padding(horizontal = 26.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, (label, icon) ->
                val active = index == selected
                var focused by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(
                    targetValue = if (focused) 1.12f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                )
                
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            when {
                                focused -> PrimaryYellow
                                active -> PrimaryYellow.copy(alpha = 0.22f)
                                else -> Color.Transparent
                            }
                        )
                        .clickable { onSelect(index) }
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .padding(horizontal = 22.dp, vertical = 8.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        icon, contentDescription = label,
                        tint = if (focused) Color.Black else if (active) PrimaryYellow else TextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        color = if (focused) Color.Black else if (active) PrimaryYellow else TextPrimary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
