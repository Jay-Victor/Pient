package com.pient.app.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pient.app.ui.theme.MonoFont
import com.pient.app.ui.theme.PientPanel

/**
 * Markdown 源码模式的搜索卡（2026-09-11 新增）。
 *
 * 参照 `Refences/Mdcito-1.2.0`（`ui/editor/EditorSearchModal.kt` + `EditorViewModel` 搜索段）
 * 逐值对齐：搜索行（20dp 图标 / 36dp 输入框 / 8dp 圆角 / surfaceVariant 底 / 32dp 清除键 /
 * 28dp 关闭键）、筛选行（28dp FilterChip ×3 + 右侧匹配数）、替换行（36dp 输入框 + 32dp 按钮 ×2）、
 * 结果列表（≤240dp、行距 4dp、行号 28dp + 等宽正文 maxLines 2、命中字 0xFFFFF9C4 / 当前 0xFFFFD54F 色块）。
 * 文案取 Mdcito `strings.xml`：搜索… / 替换为… / 区分大小写 / 全词匹配 / 正则表达式 / 替换 / 全部 / N 个匹配 / 未找到匹配结果。
 *
 * 相对 Mdcito 的差异（Pient 侧需求 + 移动端可读性）：
 * - 高亮块上的文字固定用深墨色：Mdcito 用 onSurface，暗色主题下浅字压黄底不可读（Pient 默认验收暗色）。
 * - 结果行右侧加勾选框：Mdcito 无选择态（只有"当前匹配"）；Pient 支持单选/多选后「替换」只改选中项；
 *   行状态视觉只有勾选一种（勾上才给行底着色），不再按"当前命中"给行底/行内文字换色（会被读成"已勾选"）。
 * - 「全部」= **全选**（把结果行全部勾上，再点一次取消全选），不是 Mdcito 的 `replace_all`「全部替换」；
 *   替换始终只由「替换」键触发，作用集合 = 勾选行（用户 2026-09-11 澄清）。
 * - 搜索行只保留输入框的清空键（Mdcito 另有 28dp 关闭键）；关闭走「点卡片外」或工具栏搜索键（2026-09-11 去重）。
 * - 搜索历史（History）未移植：本轮需求未涉及。
 */

// ───────────────────────────── 匹配引擎（Mdcito buildSearchRegex / buildMatchedLines 同构） ─────────────────────────────

/** 搜索选项（Mdcito SearchState 的检索相关字段） */
internal data class MdSearchOptions(
    val query: String = "",
    val replacement: String = "",
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = false,
    val regex: Boolean = false,
)

/** 命中行（行号从 1 起；ranges = 行内相对范围，matchIndices = 全局命中序号） */
internal data class MdMatchedLine(
    val lineNumber: Int,
    val content: String,
    val ranges: List<IntRange>,
    val matchIndices: List<Int>,
)

/**
 * 检索正则（Mdcito `buildSearchRegex` 同款口径）：
 * 正则模式直接用查询串；否则转义后按全词包 `\b`；默认忽略大小写。查询串非法正则 → null（视作无匹配）。
 */
internal fun mdSearchRegex(options: MdSearchOptions): Regex? {
    if (options.query.isEmpty()) return null
    val pattern = if (options.regex) {
        options.query
    } else {
        val escaped = Regex.escape(options.query)
        if (options.wholeWord) "\\b$escaped\\b" else escaped
    }
    val opts = if (options.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
    return runCatching { Regex(pattern, opts) }.getOrNull()
}

internal fun findMdMatches(text: String, options: MdSearchOptions): List<IntRange> =
    mdSearchRegex(options)?.findAll(text)?.map { it.range }?.toList() ?: emptyList()

/** 命中按行归并（Mdcito `buildMatchedLines` 同款：跨行匹配取与本行的交集） */
internal fun buildMdMatchedLines(text: String, matches: List<IntRange>): List<MdMatchedLine> {
    if (matches.isEmpty()) return emptyList()
    val out = mutableListOf<MdMatchedLine>()
    var lineStart = 0
    text.split('\n').forEachIndexed { index, line ->
        val lineEnd = lineStart + line.length          // 不含换行符（独占）
        val indices = mutableListOf<Int>()
        val ranges = mutableListOf<IntRange>()
        matches.forEachIndexed { mi, r ->
            if (r.first < lineEnd && r.last >= lineStart) {
                indices += mi
                val relStart = (r.first - lineStart).coerceAtLeast(0)
                val relEnd = (r.last - lineStart).coerceAtMost(line.length - 1)
                if (relStart <= relEnd) ranges += relStart..relEnd
            }
        }
        if (indices.isNotEmpty()) out += MdMatchedLine(index + 1, line, ranges, indices)
        lineStart = lineEnd + 1
    }
    return out
}

/** 命中色块（Mdcito SearchResultCard / SearchHighlightText 同值） */
internal val MdMatchHighlight = Color(0xFFFFF9C4)
internal val MdCurrentMatchHighlight = Color(0xFFFFD54F)

/** 色块上的文字色（Mdcito 用 onSurface；Pient 固定深墨色，两支主题下都可读） */
internal val MdMatchInk = Color(0xFF1F2328)

/** 行内命中高亮（Mdcito `buildHighlightedLineContent` 同构） */
internal fun buildMdHighlightedLine(
    line: String,
    ranges: List<IntRange>,
    highlight: Color,
): AnnotatedString = buildAnnotatedString {
    var pos = 0
    for (range in ranges) {
        if (range.first > pos) append(line.substring(pos, range.first.coerceAtMost(line.length)))
        withStyle(SpanStyle(background = highlight, color = MdMatchInk, fontWeight = FontWeight.Medium)) {
            val start = range.first.coerceIn(0, line.length)
            val end = (range.last + 1).coerceIn(0, line.length)
            if (start < end) append(line.substring(start, end))
        }
        pos = range.last + 1
    }
    if (pos < line.length) append(line.substring(pos))
}

// ───────────────────────────── 卡片 ─────────────────────────────

@Composable
internal fun MarkdownSearchCard(
    options: MdSearchOptions,
    onOptionsChange: (MdSearchOptions) -> Unit,
    matchedLines: List<MdMatchedLine>,
    matchCount: Int,
    selectedIndices: Set<Int>,
    onJumpToMatch: (Int) -> Unit,
    /** 勾/取消某一行：传入该行的全部命中序号（一行可含多个命中，勾行 = 整行一起选） */
    onToggleRowSelection: (List<Int>) -> Unit,
    onReplace: () -> Unit,
    /** 「全部」= 全选（把结果行全部勾上）；已全选时再点 = 取消全选 */
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    // 打开即聚焦搜索框（Mdcito 由对话框接管焦点；移动端搜索卡同款）
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    PientPanel(
        modifier = modifier
            .fillMaxWidth()
            // 面板根消费点击：卡片内空白处不穿透到「点外关闭」层
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // ── 搜索输入行 ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Outlined.Search, "搜索",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (options.query.isEmpty()) {
                        Text(
                            "搜索…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    BasicTextField(
                        value = options.query,
                        onValueChange = { onOptionsChange(options.copy(query = it)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .focusRequester(focusRequester),
                    )
                }
                if (options.query.isNotEmpty()) {
                    IconButton(
                        onClick = { onOptionsChange(options.copy(query = "")) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Close, "清空搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── 筛选行：区分大小写 / 全词匹配 / 正则表达式 … 右侧匹配数 ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                ) {
                    MdFilterChip("区分大小写", options.caseSensitive) {
                        onOptionsChange(options.copy(caseSensitive = !options.caseSensitive))
                    }
                    MdFilterChip("全词匹配", options.wholeWord) {
                        onOptionsChange(options.copy(wholeWord = !options.wholeWord))
                    }
                    MdFilterChip("正则表达式", options.regex) {
                        onOptionsChange(options.copy(regex = !options.regex))
                    }
                }
                if (options.query.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$matchCount 个匹配",
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 12.sp,
                        color = if (matchCount > 0) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    )
                }
            }

            // ── 替换行（搜索后出现）：输入框 + [全部][替换] ──
            if (options.query.isNotEmpty()) {
                // 「全部」开关态：所有结果行的命中都被勾上
                val allSelected = matchedLines.isNotEmpty() &&
                    matchedLines.all { row -> row.matchIndices.all { it in selectedIndices } }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (options.replacement.isEmpty()) {
                            Text(
                                "替换为…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                        BasicTextField(
                            value = options.replacement,
                            onValueChange = { onOptionsChange(options.copy(replacement = it)) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        // 「全部」= 全选开关（不是全部替换）：已全选时按钮底色点亮 = 状态反馈
                        onClick = onSelectAll,
                        enabled = matchCount > 0,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (allSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                        ),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    ) {
                        Text("全部", fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(
                        onClick = onReplace,
                        enabled = matchCount > 0,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    ) {
                        Text("替换", fontSize = 12.sp)
                    }
                }
            }

            // ── 结果列表 ──
            if (options.query.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                if (matchedLines.isEmpty()) {
                    Text(
                        "未找到匹配结果",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(matchedLines, key = { it.lineNumber }) { line ->
                            // 勾选态 = 任一命中序号被选中（列表里唯一的「行状态」视觉来源；
                            // 当前命中不再给行底/行内文字换色——它只驱动正文跳转，见「替换」语义与正文高亮）
                            val checked = line.matchIndices.any { it in selectedIndices }
                            MdSearchResultRow(
                                line = line,
                                checked = checked,
                                onJump = {
                                    line.matchIndices.firstOrNull()?.let(onJumpToMatch)
                                },
                                onToggleChecked = { onToggleRowSelection(line.matchIndices) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 筛选键（Mdcito FilterChip 同值：28dp 高、12sp 标签、选中 primaryContainer 底 + primary 描边 + 粗体） */
@Composable
private fun MdFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        },
        modifier = Modifier.height(28.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            selectedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/**
 * 结果行（Mdcito SearchResultCard 同值：8dp 圆角、h10 v6 内边距、行号 28dp、等宽正文 16sp 行高）。
 *
 * 行状态只有一种视觉来源 = **勾选**（行底 primaryContainer 15%）；命中文字一律浅色块 [MdMatchHighlight]。
 * 不含「当前命中」换色：当前命中只驱动正文跳转与正文内深色块，列表里换色会被读成「已勾选」（2026-09-11 用户报）。
 */
@Composable
private fun MdSearchResultRow(
    line: MdMatchedLine,
    checked: Boolean,
    onJump: () -> Unit,
    onToggleChecked: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onJump),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                "${line.lineNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.width(28.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                buildMdHighlightedLine(
                    line.content,
                    line.ranges,
                    MdMatchHighlight,
                ),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = MonoFont,
                    lineHeight = 16.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            // 勾选框：整块 32dp 可点（移动端点击范围红线）
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onToggleChecked),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .border(
                            1.dp,
                            if (checked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(4.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (checked) {
                        Icon(
                            Icons.Outlined.Check, "已选中",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
}
