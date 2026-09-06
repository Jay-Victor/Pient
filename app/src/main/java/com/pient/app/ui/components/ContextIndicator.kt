package com.pient.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pient.app.ui.theme.DarkWarn
import com.pient.app.ui.theme.LightWarn
import com.pient.app.ui.theme.LocalPientIsDark

/**
 * 上下文指示器收起态（设计计划 3.4.2，Operit UsageRing 同源）：
 * 环形用量环 + 百分比；>75% 警告黄、>90% 破坏红、其余主色。
 * 点击展开上下文用量卡（弹窗由 ChatScreen 以浮层呈现，2026-08-28 重构）。
 */
@Composable
fun ContextIndicator(
    percent: Float,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val warn = if (LocalPientIsDark.current) DarkWarn else LightWarn
    val ringColor = when {
        percent > 90f -> MaterialTheme.colorScheme.error
        percent > 75f -> warn
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        UsageRing(percent = percent, color = ringColor, size = 18.dp, stroke = 2.5.dp)
        Text(
            "${percent.toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = ringColor,
        )
    }
}

/**
 * 环形用量环
 */
@Composable
fun UsageRing(
    percent: Float,
    color: androidx.compose.ui.graphics.Color,
    size: androidx.compose.ui.unit.Dp,
    stroke: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    // DrawScope 非 Composable 上下文：色值必须在 Canvas 外解析
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.size(size)) {
        val strokePx = stroke.toPx()
        val inset = strokePx / 2
        val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(strokePx, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * (percent.coerceIn(0f, 100f) / 100f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(strokePx, cap = StrokeCap.Round),
        )
    }
}
