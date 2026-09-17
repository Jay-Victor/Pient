package com.pient.app.ui.chat

import com.pient.app.data.i18n.L
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
import com.pient.app.data.AiConfigStore
import com.pient.app.data.ChatState
import com.pient.app.data.ContextPolicy
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.theme.DarkCategoryConversation
import com.pient.app.ui.theme.LightCategoryConversation
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
    /** 手动压缩上下文（pi `compact`）；null = 不显示该动作 */
    onCompact: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val used = chatState.windowTokens
    val max = chatState.maxWindowTokens
    // 用量是否已知（pi 压缩后还没有新回复时给不出 tokens；2026-09-16）
    val known = chatState.contextUsageKnown
    val windowLabel = if (max > 0) formatCompact(max) else "—"

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
                    L.chat.contextUsage,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (known) "~${formatCompact(used)} / $windowLabel Tokens" else "? / $windowLabel Tokens",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = MonoFont,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ② 已用百分比（Hermes copy.percentFull；数值 = pi 的 contextUsage.percent）
            Text(
                if (known) L.chat.contextUsedPercent(chatState.contextPercent.toInt()) else L.chat.contextUsedUnknown,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 6.dp),
            )

            // ③ 单段进度条（2026-09-16 用户拍板：**分类明细整块删掉、进度条保留**）：
            // 宽度 = pi 的 contextUsage.percent；未知时留空条（不编数字）
            val usedFrac = (chatState.contextPercent / 100f).coerceIn(0f, 1f)
            val barColor = usageColor()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .drawWithCache {
                        val w = size.width * usedFrac
                        onDrawBehind {
                            if (w > 0f) drawRect(color = barColor, size = Size(w, size.height))
                        }
                    },
            )

            // ⑤ 压缩（**pi 原生**，2026-09-15 收口）：触发线 = `估算 tokens > 上下文窗口 − reserveTokens`
            // （pi 在 settings.json 的 `compaction` 里自己判，App 不参与）；这里只做等价换算展示。
            // 右侧「压缩上下文」= 移动端对桌面端 `/compact` 的等价入口（走 pi RPC `compact`），随时可按。
            val cfg = chatState.selectedModel?.provider?.let { AiConfigStore.configs[it] }
            // 窗口逐模型（pi `models[].contextWindow`）：触发线按当前模型自己的窗口算。
            // 用 `name`（模型列表条目原文，如 `mock-model=快速`）查 —— `AiModel.id` 是
            // `provider/条目` 形态，直接查 modelSettings 会落空（settingOf 只剥 `=别名`）。
            val limitK = cfg?.settingOf(chatState.selectedModel?.name.orEmpty())
                ?.ctxLenK?.trim()?.toIntOrNull() ?: 0
            val threshold = cfg?.let {
                ContextPolicy.autoCompactThresholdPercent(it.reserveTokensValue, limitK)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                Text(
                    when {
                        cfg == null -> L.chat.compactionNoProvider
                        !cfg.compactionEnabled -> L.chat.autoCompactOff
                        threshold != null -> L.chat.autoCompactAt(threshold)
                        else -> L.chat.autoCompactNearLimit
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                if (onCompact != null) {
                    PientButton(
                        text = if (chatState.compacting) L.chat.compacting else L.chat.compactContext,
                        onClick = { if (!chatState.compacting) onCompact() },
                        primary = false,
                        height = 28,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * 进度条颜色（2026-09-16）：只剩单段「上下文占用」——用原「对话」分类色，
 * 即 Hermes `--context-usage-conversation` 的语义映射（青）。
 */
@Composable
private fun usageColor(): Color =
    if (LocalPientIsDark.current) DarkCategoryConversation else LightCategoryConversation

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
