package com.pient.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.data.PientLog
import com.pient.app.data.i18n.L
import com.pient.app.ui.components.PientSearchField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 查看页一次读多少行（够看最近一段；全量在导出文件里） */
private const val VIEW_LINES = 2000

/**
 * 日志查看（2026-09-17，「应用日志管理 → 查看最近日志」）：只读看最近 [VIEW_LINES] 行。
 *
 * 筛选（2026-09-17 第二批，用户拍板 + 同日重做布局）：
 * **一行搞定两个维度** —— 左半「级别」（三档胶囊，高频、1 次点按）· 右半「tag」下拉
 * （低频、全量 tag + 各自行数，不再只摆出现次数前 5 个）；有筛选时行尾出现「重置」。
 * 旧版是两行胶囊平铺（级别行 + tag 行），问题：维度边界看不出来、「全部 / 不限 tag」
 * 两个清除项与真筛选项同权、tag 只取前 5 且没有"更多"入口、白占两行高度。
 *
 * 一条日志 = 一条记录（多行堆栈算同一条，续行跟着首行走，筛选时整条一起进出）——
 * 切段与解析都在 [PientLog.parseRecords]（与导出的范围过滤共用一份）。
 */
@Composable
fun LogViewerScreen(nav: NavController) {
    val context = LocalContext.current
    var records by remember { mutableStateOf<List<PientLog.Record>?>(null) }
    var filter by remember { mutableIntStateOf(FILTER_ALL) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        records = withContext(Dispatchers.IO) {
            PientLog.parseRecords(PientLog.readTail(context, VIEW_LINES))
        }
    }

    // tag 清单来自本次读到的数据（**全量**，按行数降序 + 名称）：不写死 tag 表，加了新 tag 自动出现
    val tags = remember(records) {
        records.orEmpty().mapNotNull { it.tag }
            .groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
    }
    // 数据换了（或原来选中的 tag 消失了）就把 tag 过滤归位
    LaunchedEffect(tags) {
        if (tagFilter != null && tags.none { it.first == tagFilter }) tagFilter = null
    }

    val hasFilter = filter != FILTER_ALL || tagFilter != null || query.isNotEmpty()

    val shown = remember(records, filter, tagFilter, query) {
        val q = query.trim()
        records.orEmpty().filter { rec ->
            when (filter) {
                FILTER_WARN -> rec.level == 'W' || rec.level == 'E'
                FILTER_ERROR -> rec.level == 'E'
                else -> true
            }
        }.filter { rec ->
            tagFilter == null || rec.tag == tagFilter
        }.filter { rec ->
            q.isEmpty() || rec.lines.any { it.contains(q, ignoreCase = true) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack, L.common.back,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = { nav.popBackStack() }),
            )
            Text(
                L.settings.logViewerTitle,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
            if (records != null) {
                Text(
                    L.settings.logViewerCount(shown.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                // 「重置」放顶栏（计数右侧）：筛选行只有 6 个控件就放不下它 —— 留在行里会把
                // tag 胶囊挤出视口（实测两轮）。槽位常留（未筛选时透明色），不留槽时它一出现
                // 会把计数顶左（元素跳动）。透明 ≠ 禁用灰。
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(48.dp)
                        .clickable(enabled = hasFilter) {
                            filter = FILTER_ALL
                            tagFilter = null
                            query = ""
                        },
                ) {
                    Text(
                        L.settings.logFilterReset,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (hasFilter) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            // 未筛选时它只是占位：透明色之外还要对无障碍服务隐藏 —— 否则读屏会念
                            // 一个点不动的「重置」（等价于假控件）；有筛选时必须能念到、能点。
                            .semantics { if (!hasFilter) hideFromAccessibility() },
                    )
                }
            }
        }

        PientSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = L.settings.logSearchHint,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        // ── 筛选行：两个同形选择框（级别 / tag），各占一半宽度 ──
        // 两个维度用同一种控件（当前值 + 下拉箭头），结构对称、行高恒定：不再有「值文字变长就把
        // 控件挤扁/挤出视口」的问题（实测踩过两次：中文长 tag 名把胶囊切在视口外、西语长文案把
        // 选择框压到只剩箭头）。值超长时走省略号，全名在菜单里。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
        ) {
            DimensionLabel(L.settings.logFilterLevelLabel)
            Spacer(Modifier.width(6.dp))
            // 级别框比 tag 框宽（约 1.35 : 1）：级别值是固定三档文案、西语「Advertencia o superior」
            // 最长（实测等宽时会被省略号截断）；tag 名虽然不定长，但实测最长的一款在 1/2.35 宽里放得下。
            LevelFilterField(
                filter = filter,
                onPick = { filter = it },
                modifier = Modifier.weight(1.35f),
            )
            Spacer(Modifier.width(14.dp))
            DimensionLabel(L.settings.logFilterTagLabel)
            Spacer(Modifier.width(6.dp))
            TagFilterField(
                current = tagFilter,
                tags = tags,
                onPick = { tagFilter = it },
                modifier = Modifier.weight(1f),
            )
        }

        if (shown.isNotEmpty()) {
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                itemsIndexed(shown) { _, rec ->
                    Text(
                        rec.lines.joinToString("\n"),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (rec.level == 'E') {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 3.dp),
                    )
                }
            }
        } else {
            Text(
                L.settings.logViewerEmpty,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

private const val FILTER_ALL = 0
private const val FILTER_WARN = 1
private const val FILTER_ERROR = 2

/** 维度标签（「级别」「tag」）：说明这一串控件筛的是哪个维度 —— 结构本身表达信息，不做装饰 */
@Composable
private fun DimensionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/**
 * 筛选选择框（级别 / tag 共用同一形态）：**视觉 36dp 曲面框 + 48dp 命中区**，左对齐当前值、
 * 右端固定箭头，点开是 [menu] 给的菜单。
 *
 * 为什么两个维度都用选择框、不再用一排胶囊：值文字长短随语言与数据变化，按内容宽度的胶囊会
 * 被挤出视口硬切（实测中文长 tag 名）、挤压别的控件（实测西语长文案把选择框压到只剩箭头）。
 * 两个同形选择框各占一半宽度、值超长走省略号，行高恒定、不换行、不裁剪。
 *
 * 打开前先收键盘：Popup 窗口会被 IME 压矮（2026-09-10 实测过的平台行为）。
 */
@Composable
private fun FilterSelectField(
    value: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    menu: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(9.dp)
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(
                    onClick = {
                        keyboard?.hide()
                        open = true
                    },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape,
                    )
                    .border(
                        1.dp,
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape,
                    )
                    .padding(start = 10.dp, end = 8.dp),
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Outlined.KeyboardArrowDown, null,
                    tint = tint,
                    modifier = Modifier.padding(start = 4.dp).size(16.dp),
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            menu { open = false }
        }
    }
}

/** 级别筛选：三档同一个选择框（菜单项复用既有的三条文案键，不新增字符串） */
@Composable
private fun LevelFilterField(
    filter: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        FILTER_ALL to L.settings.logFilterAll,
        FILTER_WARN to L.settings.logFilterWarn,
        FILTER_ERROR to L.settings.logFilterError,
    )
    val current = when (filter) {
        FILTER_WARN -> L.settings.logFilterWarn
        FILTER_ERROR -> L.settings.logFilterError
        else -> L.settings.logFilterAll
    }
    FilterSelectField(
        value = current,
        active = filter != FILTER_ALL,
        modifier = modifier,
    ) { dismiss ->
        options.forEach { (value, label) ->
            DropdownMenuItem(
                text = { Text(label, maxLines = 1) },
                trailingIcon = { if (filter == value) CheckMark() else Box(Modifier.size(16.dp)) },
                onClick = {
                    onPick(value)
                    dismiss()
                },
            )
        }
    }
}

/**
 * tag 筛选：与级别同形的选择框（值 = 当前 tag /「全部 tag」）——点开是全量 tag 菜单，
 * 每项右侧给该 tag 的行数、当前项打 ✓（按行数降序 + 名称，菜单自带滚动）。
 */
@Composable
private fun TagFilterField(
    current: String?,
    tags: List<Pair<String, Int>>,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterSelectField(
        value = current ?: L.settings.logFilterAllTags,
        active = current != null,
        modifier = modifier,
    ) { dismiss ->
        DropdownMenuItem(
            text = { Text(L.settings.logFilterAllTags, maxLines = 1) },
            trailingIcon = { if (current == null) CheckMark() else Box(Modifier.size(16.dp)) },
            onClick = {
                onPick(null)
                dismiss()
            },
        )
        tags.forEach { (tag, count) ->
            DropdownMenuItem(
                text = { Text(tag, maxLines = 1) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$count",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        if (current == tag) CheckMark() else Box(Modifier.size(16.dp))
                    }
                },
                onClick = {
                    onPick(tag)
                    dismiss()
                },
            )
        }
    }
}

@Composable
private fun CheckMark() {
    Icon(
        Icons.Outlined.Check, null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(16.dp),
    )
}
