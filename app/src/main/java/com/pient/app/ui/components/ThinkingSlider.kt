package com.pient.app.ui.components

import com.pient.app.data.i18n.L
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pient.app.data.ThinkingLevel
import com.pient.app.ui.theme.DarkBrandPurple
import com.pient.app.ui.theme.LightBrandPurple
import com.pient.app.ui.theme.LocalPientIsDark
import kotlin.math.roundToInt

/**
 * 思考程度离散滑块（2026-08-27 重设计；**2026-09-17 改档位 = pi 报的可用档位**，照 pi-web 口径）：
 * - 停位数 = 该模型实际可用档位数（`get_available_thinking_levels` 去掉 `off`）：deepseek 是 4 档、
 *   手写 `thinkingLevelMap` 砍档后可能只剩 1–2 档 —— 旧版固定五档 + 等距映射会在这些模型上
 *   出现两个停位落到同一档（「拖了没变化」）；
 * - 档位名 = [ThinkingLevel.labelOf]（pi 有而应用枚举没有的名字原样显示，如 `max`）；
 * - 只有一档时**不画滑轨**（画了也没得选），调用方改走「单档」说明分支；
 * - 布局：标题行（思考程度 + 当前档位名称）→ 极简水平滑道（档位指示点，首末档位于两端）
 * - 轨道填充 = 主色 → 品牌紫全范围渐变（端点固定，填充越宽渐变越完整；2026-08-27 增强）
 * - 拇指：24dp 圆角方形（6dp 圆角）+ 0.5dp 边框 + 随档位渐变光晕（2026-08-27 放大 1.5x）
 * - 交互：点按轨道 / 拖拽 / 方向键（←→↑↓）
 * - 动效：填充宽与色 200ms 过渡
 */
@Composable
fun ThinkingLevelSlider(
    /** 停位（pi 的档位字面量，不含 `off`；长度 ≥ 2 —— 单档由调用方另画） */
    levels: List<String>,
    /** 当前选中档位（pi 回读的真值优先，其次用户偏好） */
    selected: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 该服务商是否支持档位调节（2026-09-12）：`false` 时滑轨整体降透明度并**关闭拖拽/键盘交互**
     * （档位值仍保留，重新选回支持档位的服务商即恢复）——避免「调了没有任何效果」的死控件错觉。
     */
    enabled: Boolean = true,
) {
    val n = levels.size
    val idx = levels.indexOf(selected).coerceAtLeast(0)
    val purple = if (LocalPientIsDark.current) DarkBrandPurple else LightBrandPurple
    val density = LocalDensity.current

    val trackHeight = 12.dp
    val thumbSize = 24.dp
    val thumbRadius = 6.dp
    val dotRadius = 2.5.dp
    val dotRing = 1.dp
    val thumbPx = with(density) { thumbSize.toPx() }
    val trackHpx = with(density) { trackHeight.toPx() }

    val rawP = idx / (n - 1).toFloat()
    val p by animateFloatAsState(rawP, tween(200), label = "sliderFill")

    Column(modifier.alpha(if (enabled) 1f else 0.45f)) {
        // 标题行：思考程度 + 当前档位名称
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                L.common.thinkingLevel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                ThinkingLevel.labelOf(selected),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // 滑块主体（可聚焦，支持方向键；点按/拖拽）
        // DrawScope 非 Composable 上下文：色值在 Canvas 外解析
        val trackBase = MaterialTheme.colorScheme.outlineVariant
        val primaryBase = MaterialTheme.colorScheme.primary
        val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
        val dotRingColor = MaterialTheme.colorScheme.surface
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .then(
                    if (!enabled) Modifier
                    else Modifier
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) false
                            else when (event.key) {
                                Key.DirectionLeft, Key.DirectionDown -> {
                                    onChange(levels[(idx - 1).coerceAtLeast(0)]); true
                                }
                                Key.DirectionRight, Key.DirectionUp -> {
                                    onChange(levels[(idx + 1).coerceAtMost(n - 1)]); true
                                }
                                else -> false
                            }
                        }
                        // 点按轨道直接跳到该档（2026-09-17 补：注释一直写着「点按/拖拽」，但只接了拖拽 ——
                        // 真机上 `input tap` 落在滑轨上毫无反应。tap 与 drag 分两个 pointerInput，互不干扰）
                        .pointerInput(n) {
                            detectTapGestures { pos ->
                                onChange(levelAt(pos.x, size.width.toFloat(), thumbPx, levels))
                            }
                        }
                        .pointerInput(n) {
                            detectDragGestures(
                                onDragStart = { pos -> onChange(levelAt(pos.x, size.width.toFloat(), thumbPx, levels)) },
                                onDrag = { change, _ -> onChange(levelAt(change.position.x, size.width.toFloat(), thumbPx, levels)) },
                            )
                        },
                ),
        ) {
            val wPx = with(density) { maxWidth.toPx() }
            val pad = thumbPx / 2 // 首尾偏移补偿（Codex: 填充宽 = p% + 16 - 16p/100 px）
            val usable = wPx - pad * 2
            val fillW = (usable * p + pad).coerceAtLeast(1f)

            // 轨道：底色 + 填充（主色 → 紫 lerp）
            // 注意：Canvas 必须 matchParentSize 给足高度，否则 0 高度会被裁剪、轨道完全不可见
            Canvas(Modifier.matchParentSize()) {
                val cy = this.size.height / 2
                drawRoundRect(
                    color = trackBase,
                    topLeft = Offset(pad, cy - trackHpx / 2),
                    size = Size(usable, trackHpx),
                    cornerRadius = CornerRadius(trackHpx / 2),
                )
                // 填充渐变：端点固定 主色 → 品牌紫 全范围（随填充加宽渐次显现；
                // 档位 p 处填充色 = lerp(主色,紫,p)，与拇指光晕色恒等）
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        0f to primaryBase,
                        1f to purple,
                    ),
                    topLeft = Offset(pad, cy - trackHpx / 2),
                    size = Size(fillW, trackHpx),
                    cornerRadius = CornerRadius(trackHpx / 2),
                )

                // 档位指示点：首末档位于滑道左右两端（点中心 = 各档位位置）
                val dotR = with(density) { dotRadius.toPx() }
                val ringR = dotR + with(density) { dotRing.toPx() }
                levels.forEachIndexed { i, _ ->
                    val frac = i / (n - 1).toFloat()
                    val dx = pad + usable * frac
                    drawCircle(color = dotRingColor, radius = ringR, center = Offset(dx, cy))
                    drawCircle(color = dotColor, radius = dotR, center = Offset(dx, cy))
                }
            }

            // 拇指：圆角方形 + 光晕 + 边框
            val glow = lerp(primaryBase, purple, p)
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
                // 内部光晕：径向渐变随档位渐变
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
}

/** 点按/拖拽落点 → 档位字面量（x 归一化后取最近停位） */
private fun levelAt(x: Float, width: Float, thumbPx: Float, levels: List<String>): String {
    val usable = width - thumbPx
    if (usable <= 0f) return levels.first()
    val p = ((x - thumbPx / 2) / usable).coerceIn(0f, 1f)
    // **四舍五入**取最近停位（2026-09-17 真机发现：原先是 `.toInt()` 截断 → 每档的判定区间被整体左移半步，
    // 最末一档只剩「正好点在最右端」才选得中 —— 实测点最末档的点心却落到倒数第二档 xhigh）
    val idx = (p * (levels.size - 1)).roundToInt().coerceIn(0, levels.size - 1)
    return levels[idx]
}
