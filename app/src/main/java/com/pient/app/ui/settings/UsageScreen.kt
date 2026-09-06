package com.pient.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pient.app.data.ModelDayUsage
import com.pient.app.data.UsageMock
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.components.PientSegmented
import com.pient.app.ui.theme.PientPanel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** 时间维度（deepseek 开放平台用量页同款维度） */
private enum class UsageRange(val label: String) {
    ALL("全部"), TODAY("今天"), YESTERDAY("昨天"),
    LAST_7("近7天"), LAST_30("近30天"), WEEK("本周"), MONTH("本月"), CUSTOM("自定义"),
}

/** 各模型图例色（deepseek 品牌蓝为主色系） */
private val modelColors = mapOf(
    "deepseek-chat" to Color(0xFF4D6BFE),
    "deepseek-reasoner" to Color(0xFF8B5CF6),
    "claude-sonnet-4-5" to Color(0xFF22D3EE),
)

/** 堆叠柱的一段（单柱内一个色块） */
private data class StackSegment(val label: String, val color: Color, val value: Double)

/**
 * 模型用量信息（2026-09-01 制作，结构参照 deepseek 开放平台用量页；三版）：
 * 时间维度选择器 + 模型选择器（2026-09-01 新增，同款式卡片 + v 箭头列表）→ 三卡 →
 * Token / 费用 两张堆叠柱状图卡（单模型时按输入/输出堆叠，全部模型按模型堆叠）。
 * 数据为 UsageMock 演示数据；导出为原型占位（选择器行模型卡右侧）。
 */
@Composable
fun UsageScreen(nav: NavController) {
    val context = LocalContext.current
    var range by remember { mutableStateOf(UsageRange.LAST_30) }
    var rangeMenu by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf<String?>(null) }   // null = 全部模型
    var modelMenu by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var customStart by remember { mutableStateOf<LocalDate?>(null) }
    var customEnd by remember { mutableStateOf<LocalDate?>(null) }

    val today = LocalDate.now()
    val (start, end) = remember(range, customStart, customEnd, today) {
        when (range) {
            UsageRange.ALL -> today.minusDays((UsageMock.totalDays - 1).toLong()) to today
            UsageRange.TODAY -> today to today
            UsageRange.YESTERDAY -> today.minusDays(1) to today.minusDays(1)
            UsageRange.LAST_7 -> today.minusDays(6) to today
            UsageRange.LAST_30 -> today.minusDays(29) to today
            UsageRange.WEEK -> today.minusDays((today.dayOfWeek.value - 1).toLong()) to today
            UsageRange.MONTH -> today.withDayOfMonth(1) to today
            UsageRange.CUSTOM -> customStart to customEnd
        }
    }
    val days = remember(start, end) {
        if (start != null && end != null && !start.isAfter(end)) {
            UsageMock.daily.entries
                .filter { !it.key.isBefore(start) && !it.key.isAfter(end) }
                .sortedBy { it.key }
        } else emptyList()
    }
    val totals = remember(days, model) {
        var tokens = 0L
        var requests = 0L
        var cost = 0.0
        days.forEach { (_, perModel) ->
            perModel.forEach { (name, u) ->
                if (model == null || model == name) {
                    tokens += u.tokens
                    requests += u.requests
                    cost += u.tokens * priceOf(name) / 1_000_000.0
                }
            }
        }
        Triple(tokens, requests, cost)
    }

    val rangeText = if (start != null && end != null) "${fmtDate(start)} ~ ${fmtDate(end)}"
    else "请选择时间范围"

    // 堆叠数据：全部模型 → 按模型；单模型 → 按输入/输出
    val tokenStacks = remember(days, model) {
        days.map { (_, perModel) ->
            if (model == null) {
                UsageMock.models.mapNotNull { m ->
                    val v = perModel[m.name]?.tokens ?: return@mapNotNull null
                    if (v <= 0L) null else StackSegment(m.name, modelColors[m.name] ?: Color.Gray, v.toDouble())
                }
            } else {
                val m = model ?: return@map emptyList()
                val u = perModel[m] ?: return@map emptyList()
                val c = modelColors[m] ?: Color.Gray
                listOf(
                    StackSegment("输入", c.copy(alpha = 0.45f), u.inputTokens.toDouble()),
                    StackSegment("输出", c, u.outputTokens.toDouble()),
                )
            }
        }
    }
    val costStacks = remember(days, model) {
        days.map { (_, perModel) ->
            if (model == null) {
                UsageMock.models.mapNotNull { m ->
                    val u = perModel[m.name] ?: return@mapNotNull null
                    val v = u.tokens * priceOf(m.name) / 1_000_000.0
                    if (v <= 0.0) null else StackSegment(m.name, modelColors[m.name] ?: Color.Gray, v)
                }
            } else {
                val m = model ?: return@map emptyList()
                val u = perModel[m] ?: return@map emptyList()
                val c = modelColors[m] ?: Color.Gray
                val price = priceOf(m)
                listOf(
                    StackSegment("输入", c.copy(alpha = 0.45f), u.inputTokens * price / 1_000_000.0),
                    StackSegment("输出", c, u.outputTokens * price / 1_000_000.0),
                )
            }
        }
    }
    val legend = remember(model) {
        if (model == null) {
            UsageMock.models.map { it.name to (modelColors[it.name] ?: Color.Gray) }
        } else {
            val c = modelColors[model] ?: Color.Gray
            listOf("输入" to c.copy(alpha = 0.45f), "输出" to c)
        }
    }
    val modelLabel = model ?: "全部模型"

    // 用量排行（模型消耗榜）：每模型 token 总量 + 费用；卡片内分段控制器切换维度
    val rankData = remember(days) {
        val tokens = mutableMapOf<String, Long>()
        val costs = mutableMapOf<String, Double>()
        days.forEach { (_, m) ->
            m.forEach { (name, u) ->
                tokens[name] = (tokens[name] ?: 0L) + u.tokens
                costs[name] = (costs[name] ?: 0.0) + u.tokens * priceOf(name) / 1_000_000.0
            }
        }
        UsageMock.models.map { m -> Triple(m.name, tokens[m.name] ?: 0L, costs[m.name] ?: 0.0) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 顶栏（2026-09-03：导出放回顶栏右侧；时区三点已移除）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Icon(
                    Icons.Outlined.ArrowBack, "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = { nav.popBackStack() }),
                )
                Text(
                    "模型用量信息",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
                // 导出
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(onClick = { toast(context, "账单导出开发中") })
                        .padding(horizontal = 6.dp, vertical = 7.dp),
                ) {
                    Icon(
                        Icons.Outlined.FileDownload, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "导出",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 3.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
            ) {
                // ── 时间维度 + 模型选择器（2026-09-01 合并为同一行） ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    SelectorCard(
                        label = "时间维度",
                        value = range.label,
                        expanded = rangeMenu,
                        onArrowClick = { rangeMenu = true },
                        onDismiss = { rangeMenu = false },
                    ) {
                        UsageRange.entries.forEach { r ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        r.label,
                                        color = if (r == range) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onBackground,
                                    )
                                },
                                trailingIcon = {
                                    if (r == range) Text("✓", color = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    rangeMenu = false
                                    if (r == UsageRange.CUSTOM) showCustom = true else range = r
                                },
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    // 2026-09-03：导出已移回顶栏；模型卡独占剩余宽度（fillMax 通栏）
                    SelectorCard(
                        label = "模型",
                        value = modelLabel,
                        expanded = modelMenu,
                        onArrowClick = { modelMenu = true },
                        onDismiss = { modelMenu = false },
                        // 模型卡占剩余宽度 + fillMax 通栏：长模型名收缩省略（2026-09-03）
                        modifier = Modifier.weight(1f),
                        fillMax = true,
                    ) {
                        val options = listOf<String?>(null) + UsageMock.models.map { it.name }
                        options.forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        m ?: "全部模型",
                                        color = if (m == model) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onBackground,
                                    )
                                },
                                trailingIcon = {
                                    if (m == model) Text("✓", color = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    model = m
                                    modelMenu = false
                                },
                            )
                        }
                    }
                }

                // ── 三卡：消费金额 / API请求次数 / Tokens ──
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    StatCard("消费金额", formatMoney(totals.third), Modifier.weight(1f))
                    StatCard("API请求次数", formatCount(totals.second), Modifier.weight(1f))
                    StatCard("Tokens", formatCompact(totals.first), Modifier.weight(1f))
                }

                // ── Token 用量趋势图卡 ──
                UsageChartCard(
                    title = "Token",
                    rangeText = rangeText,
                    days = days.map { it.key },
                    stacks = tokenStacks,
                    legend = legend,
                    byCost = false,
                )
                Spacer(Modifier.height(12.dp))
                // ── 费用趋势图卡 ──
                UsageChartCard(
                    title = "费用",
                    rangeText = rangeText,
                    days = days.map { it.key },
                    stacks = costStacks,
                    legend = legend,
                    byCost = true,
                )
                Spacer(Modifier.height(12.dp))
                // ── 用量排行（模型消耗榜，卡内 Token/费用 分段切换） ──
                UsageRankingCard(rankData = rankData, rangeText = rangeText)
            }
        }

        // ── 自定义时间范围弹窗（页面级） ──
        if (showCustom) {
            CustomRangeDialog(
                start = customStart,
                end = customEnd,
                onStart = { customStart = it },
                onEnd = { customEnd = it },
                onConfirm = {
                    val s = customStart
                    val e = customEnd
                    if (s != null && e != null && !s.isAfter(e)) {
                        range = UsageRange.CUSTOM
                        showCustom = false
                    } else {
                        toast(context, "请选择完整的开始与结束日期")
                    }
                },
                onDismiss = { showCustom = false },
            )
        }
    }
}

// ───────────────────────────── 组件 ─────────────────────────────

/** 选择卡（时间维度/模型同款）：「标签」+ 竖线 + 当前值 + v 箭头；整卡可点弹出列表，箭头随展开切换指向。
 *  fillMax=true：卡片 Row 通栏 + 值弹性占满剩余（超长省略号），用于占据剩余宽度的模型卡；
 *  false：内容紧凑包裹（时间卡）。 */
@Composable
private fun SelectorCard(
    label: String,
    value: String,
    expanded: Boolean,
    onArrowClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    fillMax: Boolean = false,
    menuContent: @Composable () -> Unit,
) {
    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .then(if (fillMax) Modifier.fillMaxWidth() else Modifier)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                .clickable(onClick = onArrowClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                Modifier
                    .padding(horizontal = 10.dp)
                    .width(1.dp)
                    .height(14.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
                // fillMax 时弹性占满剩余：值区有界，超长值（模型名）省略号截断，
                // 卡片宽度恒定不挤掉右侧导出（2026-09-03；fill=false 在窄分配下会退化到 0 宽）
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (fillMax) Modifier.weight(1f).padding(end = 6.dp)
                else Modifier.padding(end = 6.dp),
            )
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                "选择$label",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
        ) {
            menuContent()
        }
    }
}

/** 统计卡片：标签 + 数值 */
@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

/** 用量排行卡（模型消耗榜）：标题右侧 Token/费用 分段控制器；名次徽标 + 模型色点 + 模型名 + 值 + 模型色进度条 */
@Composable
private fun UsageRankingCard(
    rankData: List<Triple<String, Long, Double>>,
    rangeText: String,
) {
    var unit by remember { mutableStateOf(1) }   // 0 = Token，1 = 费用（默认费用）
    val sorted = remember(rankData, unit) {
        if (unit == 0) rankData.sortedByDescending { it.second }
        else rankData.sortedByDescending { it.third }
    }
    val maxV = if (unit == 0) (sorted.maxOfOrNull { it.second } ?: 1L).toDouble()
    else (sorted.maxOfOrNull { it.third } ?: 1.0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "用量排行",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            PientSegmented(
                labels = listOf("Token", "费用"),
                selected = unit,
                onSelect = { unit = it },
                modifier = Modifier.width(150.dp),
            )
            Text(
                rangeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        sorted.forEachIndexed { i, (name, tokens, cost) ->
            val mColor = modelColors[name] ?: Color.Gray
            val value = if (unit == 0) tokens.toDouble() else cost
            val ratio = (value / maxV).toFloat()
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 名次徽标：前 3 主色底、其余弱色
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            if (i < 3) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            CircleShape,
                        ),
                ) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (i < 3) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 模型色点（与图例呼应）
                Box(
                    Modifier
                        .padding(start = 10.dp)
                        .size(8.dp)
                        .background(mColor, CircleShape),
                )
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
                Text(
                    if (unit == 0) formatCompact(tokens) else formatMoney(cost),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            // 模型色占比进度条
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 10.dp)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(1.5.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(ratio)
                        .height(3.dp)
                        .background(mColor, RoundedCornerShape(1.5.dp)),
                )
            }
        }
    }
}

/** 用量趋势图卡：标题 + 范围 + 堆叠柱状图 + 图例 */
@Composable
private fun UsageChartCard(
    title: String,
    rangeText: String,
    days: List<LocalDate>,
    stacks: List<List<StackSegment>>,
    legend: List<Pair<String, Color>>,
    byCost: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                rangeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        StackedBarChart(days, stacks, byCost)
        Spacer(Modifier.height(8.dp))
        // 图例
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            legend.forEach { (name, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(color, CircleShape),
                    )
                    Text(
                        name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        }
    }
}

/**
 * 堆叠柱状图（2026-09-01 二版：支持左右滑动——天数多时按最小柱槽宽度展开，
 * 图表区横向滚动，Y 轴及刻度固定在左侧不随之滑动；初始定位到最右=最新数据）：
 * 柱总高 = 该时间点总体用量；柱内色块 = 各段贡献（自下而上堆叠，顶层色块顶部圆角）。
 */
@Composable
private fun StackedBarChart(
    days: List<LocalDate>,
    stacks: List<List<StackSegment>>,
    byCost: Boolean,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    val maxRaw = stacks.maxOfOrNull { col -> col.sumOf { it.value } } ?: 0.0
    val maxVal = remember(maxRaw) { niceCeil(maxRaw) }

    val labelW = 42.dp
    val labelH = 18.dp
    val chartHeight = 200.dp
    val minSlot = 12.dp   // 每根柱最小占宽（含间距），保证天数多时可读
    val scrollState = rememberScrollState()
    var selected by remember { mutableStateOf<Int?>(null) }   // 点击柱 → 弹窗索引
    // 初始滚到最右（最新数据在右端）：等首帧布局完成后 scrollTo
    LaunchedEffect(days.size) {
        withFrameNanos { }
        if (scrollState.maxValue > 0) scrollState.scrollTo(scrollState.maxValue)
    }

    Row(modifier.fillMaxWidth().height(chartHeight)) {
        // 左侧固定 Y 轴（刻度 + 文字；底部留 labelH 空白与滚动区 X 轴标签区对齐）
        Canvas(Modifier.width(labelW).fillMaxSize()) {
            val chartH = size.height - labelH.toPx()
            for (i in 0..4) {
                val y = chartH - chartH * i / 4f
                val v = maxVal * i / 4
                val text = if (byCost) "¥${formatAxis(v)}" else formatAxis(v)
                val layout = textMeasurer.measure(AnnotatedString(text), axisStyle)
                drawText(
                    layout,
                    topLeft = Offset(labelW.toPx() - 6.dp.toPx() - layout.size.width, y - layout.size.height / 2f),
                )
            }
        }
        // 右侧可滚动图表区（网格线随内容滚动）
        BoxWithConstraints(Modifier.weight(1f)) {
            val viewportW = constraints.maxWidth
            val n = stacks.size
            if (viewportW <= 0 || n <= 0) return@BoxWithConstraints
            val density = LocalDensity.current
            val slot = maxOf(with(density) { viewportW.toDp() } / n, minSlot)
            val contentW = slot * n
            Box(
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState),
            ) {
                Canvas(
                    Modifier
                        .width(contentW)
                        .fillMaxHeight()
                        .pointerInput(stacks) {
                            detectTapGestures { offset ->
                                // 命中判定 = 柱体矩形（|x-柱心|≤柱宽/2 且 y 在柱顶..柱底）；
                                // 点柱间空隙/柱上方空白/X 轴标签区 = 关闭浮层（2026-09-01 修复）
                                val slotPx = slot.toPx()
                                val barHalf = slotPx * 0.62f / 2f
                                val idx = (offset.x / slotPx).toInt().coerceIn(0, n - 1)
                                val cx = slotPx * idx + slotPx / 2f
                                val col = stacks.getOrNull(idx)
                                val chartHpx = (chartHeight - labelH).toPx()
                                val total = col?.sumOf { it.value } ?: 0.0
                                val topPx = (chartHpx - total / maxVal * chartHpx).toFloat()
                                val hit = col != null &&
                                    kotlin.math.abs(offset.x - cx) <= barHalf &&
                                    offset.y in topPx..chartHpx
                                selected = if (hit) {
                                    if (selected == idx) null else idx
                                } else null
                            }
                        },
                ) {
                    val chartH = size.height - labelH.toPx()
                    // 网格线
                    for (i in 0..4) {
                        val y = chartH - chartH * i / 4f
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }
                    // 柱体
                    if (maxVal > 0) {
                        val slotPx = slot.toPx()
                        val barW = slotPx * 0.62f
                        stacks.forEachIndexed { i, col ->
                            val cx = slotPx * i + slotPx / 2f
                            // 自底向上连续推导边界（prevBottom 单一来源），避免各段 top/h
                            // 独立浮点计算造成亚像素舍入缝隙（2026-09-01 用户报色块间细缝）
                            var prevBottom = chartH
                            col.forEachIndexed { si, seg ->
                                val h = (seg.value / maxVal * chartH).toFloat()
                                val top = prevBottom - h
                                // 相邻段共享同一边界值 + 0.5px 重叠，覆盖抗锯齿边缘透底；
                                // 全部直角（2026-09-02 用户定：不要圆角，色块直角对齐）
                                val drawH = (prevBottom - top) + 0.5f
                                drawRect(
                                    color = seg.color,
                                    topLeft = Offset(cx - barW / 2f, top),
                                    size = Size(barW, drawH),
                                )
                                prevBottom = top
                            }
                        }
                    }
                    // X 轴日期标签（按 ~80dp 一根的密度标注，滚动时标签随内容移动）
                    val step = maxOf(1, ((80.dp / slot)).toInt())
                    days.forEachIndexed { i, d ->
                        val show = n <= 7 || i % step == 0 || i == n - 1
                        if (!show) return@forEachIndexed
                        val cx = slot.toPx() * i + slot.toPx() / 2f
                        val text = fmtDate(d).take(5)
                        val layout = textMeasurer.measure(AnnotatedString(text), labelStyle)
                        drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, chartH + 4.dp.toPx()))
                    }
                }
                // 柱数据浮层（点击柱后显示；随内容滚动，位于滚动 Box 内）
                selected?.let { i ->
                    val segs = stacks.getOrNull(i) ?: return@let
                    val total = segs.sumOf { it.value }
                    val slotPx = with(density) { slot.toPx() }
                    val chartHpx = with(density) { (chartHeight - labelH).toPx() }
                    // tooltip 是滚动内容的子项：绘制用内容坐标；横向 clamp 需按视口坐标（内容 x - 滚动量）
                    val cxContent = slotPx * i + slotPx / 2f
                    val topPx = (chartHpx - total / maxVal * chartHpx).toFloat()
                    BarTooltip(
                        cxViewportPx = cxContent - scrollState.value,
                        scrollPx = scrollState.value.toFloat(),
                        topPx = topPx,
                        viewportPx = viewportW.toFloat(),
                        chartHPx = chartHpx,
                        date = days.getOrNull(i),
                        segs = segs,
                        total = total,
                        byCost = byCost,
                    )
                }
            }
        }
    }
}

/** 柱状图点击浮层：日期 + 各段数值 + 合计；默认柱顶上方，放不下则柱顶下方，横向 clamp 不越界 */
@Composable
private fun BarTooltip(
    cxViewportPx: Float,
    scrollPx: Float,
    topPx: Float,
    viewportPx: Float,
    chartHPx: Float,
    date: LocalDate?,
    segs: List<StackSegment>,
    total: Double,
    byCost: Boolean,
) {
    val density = LocalDensity.current
    val width = 172.dp
    val widthPx = with(density) { width.toPx() }
    // 估算高度：日期行 + n 段行 + 分隔 + 合计行 + 上下 padding
    val estHpx = with(density) { (16 + 18 + segs.size * 20 + 10 + 18 + 20).dp.toPx() }
    // 横向 clamp 用视口坐标，绘制（offset）用内容坐标（视口 + 滚动量）
    val xViewportPx = (cxViewportPx - widthPx / 2f).coerceIn(0f, viewportPx - widthPx)
    val xPx = xViewportPx + scrollPx
    val abovePx = topPx - estHpx - with(density) { 6.dp.toPx() }
    val yPx = if (abovePx >= 0f) abovePx else (topPx + with(density) { 6.dp.toPx() }).coerceAtMost(chartHPx - estHpx)
    PientPanel(
        modifier = Modifier
            .offset {
                IntOffset(xPx.toInt(), yPx.toInt())
            }
            .width(width),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            date?.let {
                Text(
                    "${it.year}-${"%02d".format(it.monthValue)}-${"%02d".format(it.dayOfMonth)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
            }
            segs.forEach { seg ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(seg.color, CircleShape),
                    )
                    Text(
                        seg.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(start = 6.dp),
                    )
                    Text(
                        if (byCost) formatMoney(seg.value) else formatCompact(seg.value.toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "合计",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (byCost) formatMoney(total) else formatCompact(total.toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 自定义时间范围弹窗：两行日期字段（各自弹系统 DatePicker）+ 确定/取消 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(
    start: LocalDate?,
    end: LocalDate?,
    onStart: (LocalDate) -> Unit,
    onEnd: (LocalDate) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pickFor by remember { mutableStateOf<Int?>(null) }   // 0 = 开始，1 = 结束
    PientDialog(
        title = "自定义时间范围",
        onDismiss = onDismiss,
        confirmText = "确定",
        onConfirm = onConfirm,
        showClose = false,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DateField("开始日期", start) { pickFor = 0 }
            DateField("结束日期", end) { pickFor = 1 }
        }
    }
    val picking = pickFor ?: return
    val initial = if (picking == 0) start else end
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.let { it.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
    )
    DatePickerDialog(
        onDismissRequest = { pickFor = null },
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { ms ->
                    val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    if (picking == 0) onStart(date) else onEnd(date)
                }
                pickFor = null
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = { pickFor = null }) { Text("取消") }
        },
    ) {
        DatePicker(state = state)
    }
}

/** 日期字段行（自定义弹窗内） */
@Composable
private fun DateField(label: String, value: LocalDate?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            value?.let { "${it.year}-${"%02d".format(it.monthValue)}-${"%02d".format(it.dayOfMonth)}" } ?: "请选择",
            style = MaterialTheme.typography.bodyMedium,
            color = if (value != null) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ───────────────────────────── 工具 ─────────────────────────────

private fun priceOf(name: String): Float =
    UsageMock.models.firstOrNull { it.name == name }?.pricePerM ?: 0f

/** 金额：¥ 千分位两位小数；≥1 万缩写为 x.xx万 */
private fun formatMoney(v: Double): String =
    if (v >= 10_000) String.format("¥%.2f万", v / 10_000)
    else String.format("¥%,.2f", v)

/** 数量千分位 */
private fun formatCount(v: Long): String = String.format("%,d", v)

/** 紧凑数值：1.2M / 560.4K / 123 */
private fun formatCompact(v: Long): String = when {
    v >= 1_000_000 -> String.format("%.1fM", v / 1_000_000.0)
    v >= 1_000 -> String.format("%.1fK", v / 1_000.0)
    else -> v.toString()
}

/** Y 轴刻度紧凑格式（Token/费用通用） */
private fun formatAxis(v: Double): String = when {
    v >= 1_000_000 -> String.format("%.1fM", v / 1_000_000)
    v >= 1_000 -> String.format("%.0fK", v / 1_000)
    else -> String.format("%.0f", v)
}

/** 取整到 1/2/2.5/5 × 10^k 的“好看”上限 */
private fun niceCeil(v: Double): Double {
    if (v <= 0) return 1.0
    val exp = floor(log10(v)).toInt()
    val base = 10.0.pow(exp)
    val m = v / base
    val nice = when {
        m <= 1.0 -> 1.0
        m <= 2.0 -> 2.0
        m <= 2.5 -> 2.5
        m <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * base
}

private fun fmtDate(d: LocalDate): String = "${"%02d".format(d.monthValue)}-${"%02d".format(d.dayOfMonth)}"

private fun toast(context: android.content.Context, msg: String) {
    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
}
