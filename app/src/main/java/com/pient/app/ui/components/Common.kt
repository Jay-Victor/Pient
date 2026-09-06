package com.pient.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pient.app.ui.theme.DarkBrandPurple
import com.pient.app.ui.theme.LightBrandPurple
import com.pient.app.ui.theme.LocalPientIsDark
import com.pient.app.ui.theme.PientPanel

// ─────────────────────────────────────────────────────────────
// 连续滑轨（2026-08-31：背景设置模糊/亮度专用通用组件）
// 视觉逐项对齐 ThinkingLevelSlider（模型选择器思考程度同款）：
// 12dp 圆角轨道 + 主色→品牌紫渐变填充 + 24dp 圆角方形拇指（光晕 + 0.5dp 边框）；
// 差异：连续取值（无档位指示点）、步进 = 量程/50。
// ─────────────────────────────────────────────────────────────
@Composable
fun PientSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    dots: Int = 0,                       // 档位节点数（>1 时在轨道上画等距小圆点；0 = 无，背景设置滑轨不用）
    customTrackBrush: Brush? = null,     // 自定义轨道画刷（非 null 时轨道与填充同用该画刷；主题色彩虹条用）
) {
    val purple = if (LocalPientIsDark.current) DarkBrandPurple else LightBrandPurple
    val density = LocalDensity.current

    val trackHeight = 12.dp
    val thumbSize = 24.dp
    val thumbRadius = 6.dp
    val dotRadius = 2.5.dp
    val dotRing = 1.dp
    val thumbPx = with(density) { thumbSize.toPx() }
    val trackHpx = with(density) { trackHeight.toPx() }

    val span = valueRange.endInclusive - valueRange.start
    // 无动画直接渲染（2026-08-31 三轮修正）：连续滑轨首要诉求是绝对跟手。
    // 历史：①animateFloatAsState 拖动中 200ms 追赶 = 灵敏度低；②拖动中 snapTo + 松手 animateTo，
    // 因每帧重启 LaunchedEffect 协程、最后一步 snapTo 未完成即松手，animateTo 从中间位置补动画 = 快速滑动回弹。
    // M3 Slider 本身无过渡动画；方向键步进瞬时跳档可接受。
    val p = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    // DrawScope 非 Composable 上下文：色值在 Canvas 外解析
    val trackBase = MaterialTheme.colorScheme.outlineVariant
    val primaryBase = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dotRingColor = MaterialTheme.colorScheme.surface
    val glow = lerp(primaryBase, purple, p)
    val step = span / 50f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false
                else when (event.key) {
                    Key.DirectionLeft, Key.DirectionDown -> {
                        onValueChange((value - step).coerceIn(valueRange.start, valueRange.endInclusive)); true
                    }
                    Key.DirectionRight, Key.DirectionUp -> {
                        onValueChange((value + step).coerceIn(valueRange.start, valueRange.endInclusive)); true
                    }
                    else -> false
                }
            }
            .pointerInput(valueRange) {
                detectDragGestures(
                    onDragStart = { pos ->
                        onValueChange(valueAt(pos.x, size.width.toFloat(), thumbPx, valueRange))
                    },
                    onDrag = { change, _ ->
                        onValueChange(valueAt(change.position.x, size.width.toFloat(), thumbPx, valueRange))
                    },
                )
            },
    ) {
        val wPx = with(density) { maxWidth.toPx() }
        val pad = thumbPx / 2 // 首尾偏移补偿（同思考程度滑轨）
        val usable = wPx - pad * 2
        val fillW = (usable * p + pad).coerceAtLeast(1f)

        // 轨道：底色（或自定义画刷，如主题色彩虹条）+ 填充（主色 → 紫 lerp；自定义画刷时填充同用该画刷）
        Canvas(Modifier.matchParentSize()) {
            val cy = this.size.height / 2
            val trackBrush = customTrackBrush
            if (trackBrush != null) {
                drawRoundRect(
                    brush = trackBrush,
                    topLeft = Offset(pad, cy - trackHpx / 2),
                    size = Size(usable, trackHpx),
                    cornerRadius = CornerRadius(trackHpx / 2),
                )
            } else {
                drawRoundRect(
                    color = trackBase,
                    topLeft = Offset(pad, cy - trackHpx / 2),
                    size = Size(usable, trackHpx),
                    cornerRadius = CornerRadius(trackHpx / 2),
                )
            }
            drawRoundRect(
                brush = trackBrush
                    ?: Brush.horizontalGradient(0f to primaryBase, 1f to purple),
                topLeft = Offset(pad, cy - trackHpx / 2),
                size = Size(fillW, trackHpx),
                cornerRadius = CornerRadius(trackHpx / 2),
            )

            // 档位节点（dots > 1）：等距小圆点（surface 外圈 + onSurfaceVariant 点，同思考程度滑轨）
            if (dots > 1) {
                val dotR = with(density) { dotRadius.toPx() }
                val ringR = dotR + with(density) { dotRing.toPx() }
                for (i in 0 until dots) {
                    val dx = pad + usable * i / (dots - 1).toFloat()
                    drawCircle(color = dotRingColor, radius = ringR, center = Offset(dx, cy))
                    drawCircle(color = dotColor, radius = dotR, center = Offset(dx, cy))
                }
            }
        }

        // 拇指：圆角方形 + 光晕 + 边框 + 内部径向光（同思考程度滑轨）
        val thumbX = pad + usable * p - thumbPx / 2
        Box(
            modifier = Modifier
                .offset(x = with(density) { thumbX.toDp() })
                .align(Alignment.CenterStart)
                .size(thumbSize)
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(thumbRadius),
                    ambientColor = glow.copy(alpha = 0.55f),
                    spotColor = glow.copy(alpha = 0.32f),
                )
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(thumbRadius))
                .border(0.5.dp, glow, RoundedCornerShape(thumbRadius)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(thumbSize)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to glow.copy(alpha = 0.85f),
                        0.6f to glow.copy(alpha = 0.25f),
                        1f to Color.Transparent,
                        radius = thumbPx * 1.6f,
                    ),
                    radius = thumbPx * 1.6f,
                    center = Offset(this.size.width / 2, this.size.height / 2),
                )
            }
        }
    }
}

private fun valueAt(x: Float, width: Float, thumbPx: Float, range: ClosedFloatingPointRange<Float>): Float {
    val usable = width - thumbPx
    if (usable <= 0f) return range.start
    val p = ((x - thumbPx / 2) / usable).coerceIn(0f, 1f)
    return range.start + (range.endInclusive - range.start) * p
}

// ─────────────────────────────────────────────────────────────
// 状态徽标（运行中/思考中/压缩中/空闲 —— 数据源 get_state）
// ─────────────────────────────────────────────────────────────
@Composable
fun StatusBadge(label: String, color: Color, dotColor: Color = color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), CircleShape)
            .border(1.dp, color.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Box(Modifier.size(6.dp).background(dotColor, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ─────────────────────────────────────────────────────────────
// 两段分段控制器（全局/项目 —— SegmentedButton 语义）
// ─────────────────────────────────────────────────────────────
@Composable
fun PientSegmented(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?>? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    // 固定高度：中文字形度量（ascent+descent）大于 lineHeight 时会撑高行盒，
                    // 导致中/英文标签两段高度不一致（2026-09-01 用量页 Token vs 费用实测 37px/45px）
                    .height(34.dp)
                    .background(
                        if (sel) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                        shape,
                    )
                    .border(
                        if (sel) 1.dp else 0.dp,
                        if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else Color.Transparent,
                        shape,
                    )
                    .clickable(onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    icons?.getOrNull(i)?.let { icon ->
                        Icon(
                            icon, null,
                            tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 设置行（图标 + 名称 + 右箭头 / 说明）
// ─────────────────────────────────────────────────────────────
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    trailing: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 14.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        Icon(
            Icons.Outlined.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 设置开关行
// ─────────────────────────────────────────────────────────────
@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    desc: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (desc != null) {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 通用居中模态弹窗（玻璃容器；取消次要 + 确定主色）
// ─────────────────────────────────────────────────────────────
@Composable
fun PientDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String = "确定",
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    showClose: Boolean = true,
    showCancel: Boolean = true, // false = 单按钮（仅确认）；「详细信息」等纯展示弹窗用
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        PientPanel(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(horizontal = 24.dp)
                .clickable(
                    onClick = {},
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ),
            shape = MaterialTheme.shapes.medium,
        ) {
        Column(
            modifier = Modifier
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (showClose) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Outlined.Close, "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            content()
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showCancel) {
                    PientButton("取消", onClick = onDismiss, primary = false, modifier = Modifier.weight(1f))
                }
                PientButton(confirmText, onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.weight(1f))
            }
        }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 主/次按钮（高 40dp，圆角 12dp）
// ─────────────────────────────────────────────────────────────
@Composable
fun PientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    height: Int = 40,
) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (primary) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .height(dp(height))
            .background(
                if (enabled) bg else if (primary) bg.copy(alpha = 0.3f) else Color.Transparent,
                shape,
            )
            .border(
                if (primary) 0.dp else 1.dp,
                if (primary) Color.Transparent else if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) fg else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun dp(v: Int) = v.dp

// ─────────────────────────────────────────────────────────────
// 卡片内分隔线（块/条目之间：outlineVariant 50% 透明，两端各缩进 14dp）
// 上下不再自带留白：到内容的间隙 = 内容自身 padding（与内容到卡片边框距离一致，
// Operit SettingsGroup 同款；此前 vertical 10dp 使线到文字 ~22dp vs 边框 12dp，用户 2026-08-31 指出偏大）
// ─────────────────────────────────────────────────────────────
@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

// ─────────────────────────────────────────────────────────────
// 项目详细信息（2026-09-02 侧边栏首创；2026-09-03 移至公共组件供项目管理页复用）：
// 位置 / 大小 / 修改时间
// ─────────────────────────────────────────────────────────────

data class ProjectInfo(val size: String, val modified: String)

/** 递归统计项目文件夹大小与最近修改时间；目录不存在时返回 0 B / — */
fun computeProjectInfo(context: android.content.Context, project: com.pient.app.data.Project): ProjectInfo {
    var bytes = 0L
    var modified = 0L
    val uriStr = project.uri
    if (uriStr != null) {
        // SAF 项目：DocumentFile 递归（listFiles 依赖持久化授权）
        val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, android.net.Uri.parse(uriStr))
        fun walk(d: androidx.documentfile.provider.DocumentFile) {
            for (child in d.listFiles()) {
                if (child.isDirectory) walk(child) else {
                    bytes += child.length()
                    modified = maxOf(modified, child.lastModified())
                }
            }
        }
        try {
            if (doc != null) {
                walk(doc)
                modified = maxOf(modified, doc.lastModified())
            }
        } catch (e: Exception) {
            // 授权缺失/IO 异常：保持 0 B
        }
    } else {
        val dir = java.io.File(project.path)
        if (dir.exists()) {
            dir.walkTopDown().forEach { f ->
                if (f.isFile) {
                    bytes += f.length()
                    modified = maxOf(modified, f.lastModified())
                }
            }
            modified = maxOf(modified, dir.lastModified())
        }
    }
    val sizeText = if (bytes > 0 || (uriStr == null && java.io.File(project.path).exists())) formatSize(bytes) else "0 B"
    val timeText = if (modified > 0) {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(modified))
    } else {
        "—"
    }
    return ProjectInfo(sizeText, timeText)
}

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}

@Composable
fun DetailRow(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 分区标题（设置/模型配置分区卡片头）
// ─────────────────────────────────────────────────────────────
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            Icon(
                icon, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 权限请求弹窗（三选：仅本次允许 / 始终允许 / 拒绝；高危 = 红色破坏变体）
// ─────────────────────────────────────────────────────────────
@Composable
fun PermissionRequestDialog(
    toolName: String,
    paramSummary: String,
    dangerous: Boolean = false,
    onAllowOnce: () -> Unit,
    onAlwaysAllow: () -> Unit,
    onDeny: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = if (dangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        PientPanel(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(horizontal = 24.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
        Column(
            modifier = Modifier
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(accent.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (dangerous) "⚠" else "🔧", fontSize = 16.sp)
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        "权限请求",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (dangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "工具 $toolName 请求执行权限",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                paramSummary,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = com.pient.app.ui.theme.MonoFont,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
                    .padding(10.dp),
            )
            if (dangerous) {
                Text(
                    "高危操作：可能修改系统状态，请确认参数无误",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PientButton("仅本次允许", onClick = onAllowOnce, modifier = Modifier.fillMaxWidth())
                PientButton("始终允许（写入例外组）", onClick = onAlwaysAllow, primary = false, modifier = Modifier.fillMaxWidth())
                PientButton(
                    "拒绝",
                    onClick = onDeny,
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        }
    }
}
