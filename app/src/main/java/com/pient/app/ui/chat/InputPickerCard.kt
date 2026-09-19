package com.pient.app.ui.chat

import com.pient.app.data.i18n.L
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pient.app.runtime.PiCommands
import com.pient.app.ui.components.PientSegmented
import com.pient.app.ui.theme.MonoFont
import com.pient.app.ui.theme.PientPanel

/**
 * 输入栏的**命令词候选卡**：输入 `/` → 技能卡
 * （分段：全局技能 / 项目技能）→ 选中插 `/skill:<名字> `。
 *
 * 与 @ 引用卡同款外壳（`PientPanel` + 268.8dp 宽 + 屏高 40% 上限 + 贴 dock 上方左对齐），
 * 分段控制器**复用技能页/插件页的同一个 `PientSegmented`**（同态同色）。
 *
 * 为什么 `/` 只在**整条消息以 `/` 开头**时弹（`findPickerQueryAt` 的硬口径）：
 * pi 只在消息以 `/skill:<名字>` / `/命令` 开头时才展开/执行。
 */

/** 输入栏候选卡统一宽度（与 @ 引用卡同款：左距屏 6dp、左对齐浮层） */
internal val InputPickerCardWidth = 268.8.dp

/** 候选卡高度上限 = 屏幕高 40%（与 @ 引用卡 / 模型选择器同口径） */
@Composable
internal fun pickerCardMaxHeight(): Dp = (LocalConfiguration.current.screenHeightDp * 0.40f).dp

/**
 * 光标处正在输入的**命令词查询**（`/` 技能卡）。
 * 只在「整条消息以 [trigger] 开头」时成立；[endExclusive] = 命令词终点（第一个空白之前），
 * 选中候选时把 `[0, endExclusive)` 整词替换为插入文本（命令词之后的参数原样保留）。
 */
data class PickerQuery(
    val trigger: Char,
    val endExclusive: Int,
    /** 触发符与光标之间的筛选串（可能为空 = 刚敲下触发符） */
    val text: String,
)

/**
 * 光标处是否有命令词查询。三种情况都返回 null：
 * 文本不以 [trigger] 开头 / 光标已越过命令词（在参数里）/ 选区非折叠。
 */
fun findPickerQueryAt(value: TextFieldValue, trigger: Char): PickerQuery? {
    if (!value.selection.collapsed) return null
    val text = value.text
    if (text.isEmpty() || text[0] != trigger) return null
    val cursor = value.selection.start.coerceIn(0, text.length)
    if (cursor < 1) return null
    var end = 1
    while (end < text.length && !text[end].isWhitespace()) end++
    if (cursor > end) return null
    return PickerQuery(trigger, end, text.substring(1, cursor))
}

/**
 * 候选筛选：名称前缀命中 > 名称包含 > 说明/来源包含，同级保持原顺序（sortedBy 稳定，与
 * `filterMentionFiles` 同一套排序口径）。查询串为空 = 原样列出。
 */
internal fun <T> filterPickerRows(
    rows: List<T>,
    query: String,
    nameOf: (T) -> String,
    descOf: (T) -> String,
): List<T> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return rows
    return rows.mapNotNull { row ->
        val name = nameOf(row).lowercase()
        val desc = descOf(row).lowercase()
        val rank = when {
            name.startsWith(q) -> 0
            name.contains(q) -> 1
            desc.contains(q) -> 2
            else -> return@mapNotNull null
        }
        rank to row
    }.sortedBy { it.first }.map { it.second }
}

/**
 * 选中候选后的新文本：把**整条命令词** `[0, endExclusive)` 换成 [insert]（如 `/skill:foo `），
 * 命令词之后的参数原样保留（此时不再多补一个空格）；光标落在插入文本末尾 ——
 * 与 @ 引用卡的点选同口径（唯一差别是 @ 的格式在 `mentionTextFor` 里，这里是 pi 命令名本身）。
 */
fun applyPickerInsert(text: String, endExclusive: Int, insert: String): TextFieldValue {
    val tail = text.substring(endExclusive.coerceIn(0, text.length))
    val merged = if (tail.isBlank()) insert else insert.trimEnd() + tail
    return TextFieldValue(merged, selection = TextRange(merged.length))
}

/**
 * 输入框里已完成的 `/命令` token（整条消息以 `/` 开头、且命令词命中 pi 命令面）。
 *
 * 与 @ 引用同一口径：**只认 pi 真认的命令**（[items] = `PiCommands.items`，即 `/` 卡的数据源）——
 * 半截（`/ski`）或未知名字不命中，也就不高亮、不出 pill（不给「看着像命令、发出去不生效」的假块）。
 * [endExclusive] = 命令词末尾（第一个空白之前；前导 `/` 算进 token）。
 */
data class SlashTokenMatch(
    val item: PiCommands.Item,
    val endExclusive: Int,
)

/** 见 [SlashTokenMatch]；[items] 未加载时一律不命中（冷启动手打 `/skill:…` 就没有块） */
fun findSlashToken(text: String, items: List<PiCommands.Item>): SlashTokenMatch? {
    if (items.isEmpty() || text.isEmpty() || text[0] != '/') return null
    var end = 1
    while (end < text.length && !text[end].isWhitespace()) end++
    val word = text.substring(1, end)
    if (word.isEmpty()) return null
    return items.firstOrNull { it.name == word }?.let { SlashTokenMatch(it, end) }
}
/** `/` 技能卡：全局 / 项目 分段 + 技能列表（名字 + 说明），选中插 `/skill:<名字> ` */
@Composable
fun SkillPickerCard(
    skills: List<PiCommands.Item>,     // 已按分段筛过
    segment: Int,
    onSegment: (Int) -> Unit,
    query: String,
    loading: Boolean,                  // 正在向 pi 要命令面
    ready: Boolean,                    // pi 通道有没有答上（false = 未就绪，卡片如实说）
    bottomOffset: Dp,
    onPick: (PiCommands.Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    PickerCardFrame(
        title = L.chat.skillPicker,
        segment = segment,
        onSegment = onSegment,
        bottomOffset = bottomOffset,
        modifier = modifier,
    ) {
        when {
            !ready -> PickerEmpty(if (loading) L.chat.loadingCommands else L.chat.piUnready)
            skills.isEmpty() -> PickerEmpty(
                if (query.isBlank()) {
                    if (segment == 0) L.chat.emptyGlobalSkills else L.chat.emptyProjectSkills
                } else L.chat.noMatchingSkills,
            )
            else -> LazyColumn(Modifier.padding(top = 4.dp).weight(1f, fill = false)) {
                items(skills, key = { it.name }) { item ->
                    PickerRow(
                        title = item.label,
                        subtitle = item.description,
                        onClick = { onPick(item) },
                    )
                }
            }
        }
    }
}

// ─────────────────────────── 内部组件 ───────────────────────────

/** 卡片外壳：小标题 + 分段控制器 + 内容（列表/空态） */
@Composable
private fun PickerCardFrame(
    title: String,
    segment: Int,
    onSegment: (Int) -> Unit,
    bottomOffset: Dp,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    PientPanel(
        modifier = modifier
            .padding(start = 6.dp, bottom = bottomOffset)
            .width(InputPickerCardWidth)
            .heightIn(max = pickerCardMaxHeight()),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
            PientSegmented(
                labels = listOf(L.common.global, L.common.project),
                selected = segment,
                onSelect = onSegment,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            content()
        }
    }
}

/** 卡片里的空态/未就绪一行（与 @ 引用卡的空态同款弱化小字） */
@Composable
private fun PickerEmpty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** 单行候选（技能）：名称 mono + 说明一行省略 */
@Composable
private fun PickerRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
) {
    val base = Modifier
        .fillMaxWidth()
        .heightIn(min = 40.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base)
            .padding(start = 12.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFont, fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}
