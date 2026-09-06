package com.pient.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pient.app.data.ChatState
import com.pient.app.data.ContextCategory
import com.pient.app.ui.theme.DarkBrandPurple
import com.pient.app.ui.theme.DarkCategoryConversation
import com.pient.app.ui.theme.DarkCategoryRules
import com.pient.app.ui.theme.DarkWarn
import com.pient.app.ui.theme.LightBrandPurple
import com.pient.app.ui.theme.LightCategoryConversation
import com.pient.app.ui.theme.LightCategoryRules
import com.pient.app.ui.theme.LightWarn
import com.pient.app.ui.theme.LocalPientIsDark
import com.pient.app.ui.theme.MonoFont
import com.pient.app.ui.theme.PientPanel
import java.util.Locale

/**
 * 上下文用量卡（2026-08-28 重设计，参照 Hermes 桌面端 context-usage-panel.tsx）：
 * - 位置与模型选择器浮层一致：贴屏幕右侧（右距屏 6dp）、底部锚定到输入栏上缘，
 *   同宽 268.8dp、同 16dp 圆角 PientPanel，点外关闭（无 scrim）。
 * - 内容自上而下：
 *   ① 标题行：「上下文用量」（左）＋「~已用 / 上限 Tokens」（右，弱化等宽）；
 *   ② 「已用 X%」；
 *   ③ 堆叠进度条（6dp 高、全圆角，轨道 outlineVariant，各分类色段按 token 占比）；
 *   ④ 分类明细（每行：8dp 色块（2dp 圆角）＋ 分类名（弱化）＋ token 数（等宽））。
 * - 已移除「自动压缩」入口（用户决策 2026-08-28）。
 * - 分类色 = Hermes --context-usage-* 语义映射 GitHub 色系（见 theme/Color.kt 注释）。
 * 数据源：get_state / 后端 context breakdown（UI 原型阶段为 ChatState mock）。
 */
@Composable
fun ContextUsageCard(
    chatState: ChatState,
    bottomOffset: Dp = 8.dp, // 弹窗底部到屏幕底的距离（锚定到输入栏上缘，与模型选择器同口径）
    modifier: Modifier = Modifier,
) {
    val categories = chatState.contextCategories
    val used = chatState.windowTokens
    val max = chatState.maxWindowTokens

    PientPanel(
        modifier = modifier
            .padding(end = 6.dp, bottom = bottomOffset)
            .width(268.8.dp), // 与模型选择器浮层同宽（336dp 的 4/5）
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            // ① 标题行（Hermes copy.title + copy.tokenSummary）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "上下文用量",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "~${formatCompact(used)} / ${formatCompact(max)} Tokens",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = MonoFont,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ② 已用百分比（Hermes copy.percentFull）
            Text(
                "已用 ${chatState.contextPercent.toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 6.dp),
            )

            // ③ 堆叠进度条（Hermes ContextUsageBar：h-1.5 rounded-full，轨道 stroke-tertiary）
            // 单画布绘制：float 宽度按 token 占比精确到亚像素，避免 weight+Box 方案
            // 的整数取整（硬边界无 AA、尾部 1px 残缝）
            val totalTokens = categories.sumOf { it.tokens }.coerceAtLeast(1).toFloat()
            val segmentColors = categories.map { categoryColor(it.id) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .drawWithCache {
                        val segWidths = categories.map { size.width * it.tokens / totalTokens }
                        onDrawBehind {
                            var x = 0f
                            categories.forEachIndexed { i, cat ->
                                drawRect(
                                    color = segmentColors[i],
                                    topLeft = Offset(x, 0f),
                                    size = Size(segWidths[i], size.height),
                                )
                                x += segWidths[i]
                            }
                        }
                    },
            )

            // ④ 分类明细（Hermes category list：色块 8px/2px 圆角 + 标签 + token 数）
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                categories.forEach { cat ->
                    CategoryRow(cat)
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(cat: ContextCategory) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(categoryColor(cat.id)),
        )
        Text(
            cat.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        Text(
            formatCompact(cat.tokens),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = MonoFont,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** 分类色：Hermes --context-usage-* 语义 → Pient GitHub 色系映射 */
@Composable
private fun categoryColor(id: String): Color {
    val dark = LocalPientIsDark.current
    return when (id) {
        "tool_definitions" -> if (dark) DarkBrandPurple else LightBrandPurple // 紫
        "skills" -> if (dark) DarkWarn else LightWarn                         // 黄
        "rules" -> if (dark) DarkCategoryRules else LightCategoryRules         // 绿
        "conversation" -> if (dark) DarkCategoryConversation else LightCategoryConversation // 青
        else -> MaterialTheme.colorScheme.onSurfaceVariant                    // 系统提示词=灰
    }
}

/** Hermes compactNumber 同规则：999→"999"，1000→"1k"，1230→"1.2k"，10000→"10k"，1.5M */
private fun formatCompact(value: Int): String {
    if (value <= 0) return "0"
    fun scaled(v: Float, suffix: String): String =
        String.format(Locale.US, "%.1f", v).removeSuffix(".0") + suffix
    return when {
        value >= 999_950 -> scaled(value / 1_000_000f, "M")
        value >= 1_000 -> scaled(value / 1_000f, "k")
        else -> value.toString()
    }
}
