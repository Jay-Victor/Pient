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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
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
import com.pient.app.data.AiConfigStore
import com.pient.app.data.BillingMode
import com.pient.app.data.ModelDayUsage
import com.pient.app.data.ModelPricing
import com.pient.app.data.PricingCurrency
import com.pient.app.data.UsageStore
import com.pient.app.data.fromCny
import com.pient.app.data.toCny
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.components.PientInputBox
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

/**
 * 模型配色池（deepseek 品牌蓝为主色系）：按模型在台账中的首次出现顺序取色——
 * 切换时间范围/筛选模型不变色，模型数量超过池长时循环取色。
 */
private val MODEL_PALETTE = listOf(
    Color(0xFF4D6BFE), Color(0xFF8B5CF6), Color(0xFF22D3EE), Color(0xFFF59E0B),
    Color(0xFF10B981), Color(0xFFEF4444), Color(0xFFEC4899), Color(0xFF84CC16),
)

/** 某模型的图例/柱体/进度条颜色（order = 台账中的模型顺序） */
private fun modelColor(name: String, order: List<String>): Color {
    val i = order.indexOf(name)
    return MODEL_PALETTE[(if (i < 0) 0 else i) % MODEL_PALETTE.size]
}

/** 堆叠柱的一段（单柱内一个色块） */
private data class StackSegment(val label: String, val color: Color, val value: Double)

/**
 * 模型用量信息（2026-09-01 制作，结构参照 deepseek 开放平台用量页；三版）：
 * 时间维度选择器 + 模型选择器（2026-09-01 新增，同款式卡片 + v 箭头列表）→ 三卡 →
 * Token / 费用 两张堆叠柱状图卡（单模型时按输入/输出堆叠，全部模型按模型堆叠）。
 * 数据为**真实用量台账**（2026-09-11 起，UsageStore：每次回复记一笔；
 * 金额按「模型费用信息」配置的单价计算，未配单价的模型金额为 0）。
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
    // 模型定价弹窗（Operit 式：用量页里点模型 → 编辑定价和计费方式）+ 汇率输入态
    var pricingModel by remember { mutableStateOf<String?>(null) }
    var rateInput by remember { mutableStateOf(AiConfigStore.usdToCnyRate.toString()) }

    // ── 真实数据源（2026-09-11 起替换 UsageMock 演示数据）──
    // 用量台账：每次回复记一笔（UsageStore，落盘 usage.json）；
    // 费用不落盘、按「模型费用信息」里配置的单价实时计算 → 改单价金额立即重算。
    val recordCount = UsageStore.records.size
    val pricingState = AiConfigStore.pricing.toMap()
    val rateState = AiConfigStore.usdToCnyRate
    val modelNames = remember(recordCount) { UsageStore.modelOrder() }
    val daily = remember(recordCount, pricingState, rateState) { UsageStore.daily() }
    val firstDate = remember(recordCount) { UsageStore.firstDate() }
    // 选中模型已从台账消失（记录被清空/换服务商）：回退「全部模型」
    if (model != null && model !in modelNames) model = null

    val today = LocalDate.now()
    val (start, end) = remember(range, customStart, customEnd, today, firstDate) {
        when (range) {
            UsageRange.ALL -> (firstDate ?: today) to today
            UsageRange.TODAY -> today to today
            UsageRange.YESTERDAY -> today.minusDays(1) to today.minusDays(1)
            UsageRange.LAST_7 -> today.minusDays(6) to today
            UsageRange.LAST_30 -> today.minusDays(29) to today
            UsageRange.WEEK -> today.minusDays((today.dayOfWeek.value - 1).toLong()) to today
            UsageRange.MONTH -> today.withDayOfMonth(1) to today
            UsageRange.CUSTOM -> customStart to customEnd
        }
    }
    // X 轴 = 所选时间范围的**每一天**（无记录的日期补零成空柱槽）。
    // 2026-09-11 用户报「选了非今天的时间维度，图里只有一天」：此前直接用台账里有记录的
    // 日期当轴（UsageStore.daily() 只含出现过的日期），于是近30天/近7天也只剩一两根柱。
    val days = remember(daily, start, end) {
        if (start != null && end != null && !start.isAfter(end)) {
            val out = ArrayList<Pair<LocalDate, Map<String, ModelDayUsage>>>()
            var d = start
            while (!d.isAfter(end)) {
                out += d to (daily[d] ?: emptyMap())
                d = d.plusDays(1)
            }
            out
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
                    cost += u.cost
                }
            }
        }
        Triple(tokens, requests, cost)
    }

    val rangeText = if (start != null && end != null) "${fmtDate(start)} ~ ${fmtDate(end)}"
    else "请选择时间范围"

    // 选中模型的计费方式（单模型视图的图例/费用柱随计费方式变：按次计费不拆输入/输出）
    val selectedBillingMode = remember(daily, model) {
        val m = model
        if (m == null) null else daily.values.asSequence().mapNotNull { it[m] }.firstOrNull()?.billingMode
    }
    // 是否有 USD 计价模型的费用（有则显示汇率折算提示；Operit settings_rate_applied_hint 同款）
    val hasUsdCost = remember(days) {
        days.any { (_, perModel) ->
            perModel.any { (_, u) -> u.currency == PricingCurrency.USD && u.cost > 0.0 }
        }
    }

    // 堆叠数据：全部模型 → 按模型；单模型 → 按输入/输出
    val tokenStacks = remember(days, model, modelNames) {
        days.map { (_, perModel) ->
            if (model == null) {
                modelNames.mapNotNull { name ->
                    val v = perModel[name]?.tokens ?: return@mapNotNull null
                    if (v <= 0L) null else StackSegment(name, modelColor(name, modelNames), v.toDouble())
                }
            } else {
                val m = model ?: return@map emptyList()
                val u = perModel[m] ?: return@map emptyList()
                val c = modelColor(m, modelNames)
                listOf(
                    StackSegment("输入", c.copy(alpha = 0.45f), u.inputTokens.toDouble()),
                    StackSegment("输出", c, u.outputTokens.toDouble()),
                )
            }
        }
    }
    val costStacks = remember(days, model, modelNames, selectedBillingMode) {
        days.map { (_, perModel) ->
            if (model == null) {
                modelNames.mapNotNull { name ->
                    val u = perModel[name] ?: return@mapNotNull null
                    if (u.cost <= 0.0) null else StackSegment(name, modelColor(name, modelNames), u.cost)
                }
            } else {
                val m = model ?: return@map emptyList()
                val u = perModel[m] ?: return@map emptyList()
                val c = modelColor(m, modelNames)
                if (selectedBillingMode == BillingMode.COUNT) {
                    // 按次计费：无输入/输出拆分，单段显示（Operit 按次计费只算每次请求价）
                    listOf(StackSegment("按次", c, u.cost))
                } else {
                    listOf(
                        StackSegment("输入", c.copy(alpha = 0.45f), u.inputCost),
                        StackSegment("输出", c, u.outputCost),
                    )
                }
            }
        }
    }
    val legend = remember(model, modelNames, selectedBillingMode) {
        val m = model
        if (m == null) {
            modelNames.map { it to modelColor(it, modelNames) }
        } else {
            val c = modelColor(m, modelNames)
            if (selectedBillingMode == BillingMode.COUNT) listOf("按次" to c)
            else listOf("输入" to c.copy(alpha = 0.45f), "输出" to c)
        }
    }
    val modelLabel = model ?: "全部模型"

    // 用量排行（模型消耗榜）：每模型 token 总量 + 费用；卡片内分段控制器切换维度
    val rankData = remember(days, modelNames) {
        val tokens = mutableMapOf<String, Long>()
        val costs = mutableMapOf<String, Double>()
        days.forEach { (_, m) ->
            m.forEach { (name, u) ->
                tokens[name] = (tokens[name] ?: 0L) + u.tokens
                costs[name] = (costs[name] ?: 0.0) + u.cost
            }
        }
        modelNames.map { name -> Triple(name, tokens[name] ?: 0L, costs[name] ?: 0.0) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 顶栏（2026-09-06：导出已移除；时区三点已移除）
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
                    // 模型卡独占剩余宽度（fillMax 通栏）
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
                        val options = listOf<String?>(null) + modelNames
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
                // 汇率折算提示（有 USD 计价模型的费用时显示；Operit settings_rate_applied_hint 同款）
                if (hasUsdCost) {
                    Text(
                        "总费用按 1 USD = ${"%.4f".format(AiConfigStore.usdToCnyRate)} CNY 折算",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                }

                // ── Token 用量趋势图卡 ──
                UsageChartCard(
                    title = "Token",
                    rangeText = rangeText,
                    days = days.map { it.first },
                    stacks = tokenStacks,
                    legend = legend,
                    byCost = false,
                )
                Spacer(Modifier.height(12.dp))
                // ── 费用趋势图卡 ──
                UsageChartCard(
                    title = "费用",
                    rangeText = rangeText,
                    days = days.map { it.first },
                    stacks = costStacks,
                    legend = legend,
                    byCost = true,
                )
                Spacer(Modifier.height(12.dp))
                // ── 用量排行（模型消耗榜，卡内 Token/费用 分段切换；点模型行 = 编辑定价和计费方式） ──
                UsageRankingCard(
                    rankData = rankData,
                    rangeText = rangeText,
                    modelNames = modelNames,
                    onEditPricing = { name -> pricingModel = name },
                )
                Spacer(Modifier.height(12.dp))
                // ── 汇率设置卡（Operit 汇率设置同款：美元计费模型按此汇率折算为人民币总费用） ──
                ExchangeRateCard(
                    rateInput = rateInput,
                    onRateInputChange = { rateInput = it },
                    onSave = {
                        val parsed = rateInput.trim().toDoubleOrNull()
                        if (parsed != null && parsed > 0.0) {
                            AiConfigStore.usdToCnyRate = parsed
                            toast(context, "汇率已保存")
                        } else {
                            toast(context, "请输入大于 0 的汇率")
                        }
                    },
                )
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

        // ── 模型定价弹窗（页面级浮层；Operit 式：从用量页模型行进入） ──
        pricingModel?.let { name ->
            ModelPricingDialog(model = name, onDismiss = { pricingModel = null })
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
                // fillMax 时弹性占满剩余：值区有界，超长值（模型名）省略号截断
                // （2026-09-03；fill=false 在窄分配下会退化到 0 宽）
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

/** 用量排行卡（模型消耗榜）：标题右侧 Token/费用 分段控制器；名次徽标 + 模型色点 + 模型名 + 值 + 模型色进度条。
 *  每行可点 = 打开该模型的定价弹窗（Operit「点击编辑定价和计费方式」同款），行下显示计费摘要。 */
@Composable
private fun UsageRankingCard(
    rankData: List<Triple<String, Long, Double>>,
    rangeText: String,
    modelNames: List<String>,
    onEditPricing: (String) -> Unit,
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
            val mColor = modelColor(name, modelNames)
            val value = if (unit == 0) tokens.toDouble() else cost
            val ratio = (value / maxV).toFloat()
            val pricing = AiConfigStore.effectivePricing(providersOfModel(name).firstOrNull().orEmpty(), name)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditPricing(name) },
            ) {
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
            // 计费摘要 + 编辑入口提示（Operit 模型卡：计费方式 chip +「点击编辑定价和计费方式」）
            Text(
                billingSummary(pricing) + " · 点击编辑定价和计费方式",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 30.dp, top = 2.dp),
            )
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
        // 空态：所选范围内无记录（新装/清数据/范围选错）时给一句说明，避免空白图看着像渲染异常
        if (stacks.all { it.isEmpty() }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "所选范围内暂无用量记录 · 对话完成后自动统计",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            StackedBarChart(days, stacks, byCost)
        }
        Spacer(Modifier.height(8.dp))
        // 图例（模型名可能很长：FlowRow 换行 + 单项单行省略。
        // 2026-09-11 用户报「图右下角有竖直的字」= 图例第二个长模型名被挤成一列竖排字）
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .widthIn(max = 168.dp),
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
                    // 首尾标签横向 clamp 在画布内：否则居中绘制会让内容两端各有一半露在
                    // 画布外被裁掉（2026-09-11 用户报「滑到左右两端有遮挡、没显示全」）
                    val step = maxOf(1, ((80.dp / slot)).toInt())
                    val maxX = (size.width - 1f).coerceAtLeast(0f)
                    days.forEachIndexed { i, d ->
                        val show = n <= 7 || i % step == 0 || i == n - 1
                        if (!show) return@forEachIndexed
                        val text = fmtDate(d).take(5)
                        val layout = textMeasurer.measure(AnnotatedString(text), labelStyle)
                        val cx = slot.toPx() * i + slot.toPx() / 2f
                        val labelX = (cx - layout.size.width / 2f)
                            .coerceIn(0f, (maxX - layout.size.width).coerceAtLeast(0f))
                        drawText(layout, topLeft = Offset(labelX, chartH + 4.dp.toPx()))
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

// ───────────────────────────── 模型定价（2026-09-11 按 Operit 的处理方式） ─────────────────────────────

/** 计费摘要文案（排行行副标题；币种用模型原生符号——Operit 模型卡的计费方式 + 单价） */
private fun billingSummary(p: ModelPricing): String {
    val sym = p.currency.symbol
    return when (p.billingMode) {
        BillingMode.TOKEN ->
            "按Token计费 · 输入 ${sym}${fmtPrice(p.inputPerMillion)}/百万 · 输出 ${sym}${fmtPrice(p.outputPerMillion)}/百万"
        BillingMode.COUNT -> "按次计费 · 每次 ${sym}${fmtPrice(p.pricePerRequest)}"
    }
}

/** 单价数字显示：整数不带小数（1）、其余保留原值（0.02） */
private fun fmtPrice(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

/** 该模型挂在哪些服务商下（定价覆盖按 provider:model 存；台账 + 已配置模型列表去重） */
private fun providersOfModel(name: String): List<String> {
    val ledger = UsageStore.records.filter { it.model == name }.map { it.provider }
    val configured = AiConfigStore.configs.filterValues { it.models.contains(name) }.keys.toList()
    return (ledger + configured).distinct()
}

/** 带标签的价格输入行（定价弹窗内） */
@Composable
private fun PriceField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PientInputBox(
            value = value,
            onValueChange = { v -> onValueChange(v.filter { c -> c.isDigit() || c == '.' }) },
            placeholder = "0",
            number = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp),
        )
    }
}

/** 汇率设置卡（Operit ExchangeRateSettingsCard 同款：标题 + 副标题 + 汇率输入 + 保存） */
@Composable
private fun ExchangeRateCard(
    rateInput: String,
    onRateInputChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(
            "汇率设置",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "美元计费模型会按此汇率折算为人民币总费用",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        PientInputBox(
            value = rateInput,
            onValueChange = { v -> onRateInputChange(v.filter { c -> c.isDigit() || c == '.' }) },
            placeholder = "USD → CNY 汇率",
            number = true,
            suffix = "CNY",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                "保存",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onSave)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 模型定价弹窗（Operit「编辑模型定价 - 模型名」同款结构）：
 * 「当前计价币种：CNY」提示 + 计费方式（PientSegmented：按Token计费 / 按次计费）
 * + 价格输入（一律按人民币填写；USD 计价模型保存时经汇率折回原生价存储，与 Operit
 * convertCnyToPricingCurrency 口径一致）+ 保存/取消。
 */
@Composable
private fun ModelPricingDialog(model: String, onDismiss: () -> Unit) {
    val providers = remember(model) { providersOfModel(model) }
    val rate = AiConfigStore.usdToCnyRate
    val current = remember(model, providers, rate) {
        AiConfigStore.effectivePricing(providers.firstOrNull().orEmpty(), model)
    }
    var mode by remember { mutableStateOf(current.billingMode) }
    var inputPrice by remember { mutableStateOf(fmtPrice(toCny(current.inputPerMillion, current.currency, rate))) }
    var cachedPrice by remember { mutableStateOf(fmtPrice(toCny(current.cachedInputPerMillion, current.currency, rate))) }
    var outputPrice by remember { mutableStateOf(fmtPrice(toCny(current.outputPerMillion, current.currency, rate))) }
    var requestPrice by remember { mutableStateOf(fmtPrice(toCny(current.pricePerRequest, current.currency, rate))) }

    fun priceOf(text: String): Double? = text.trim().toDoubleOrNull()?.takeIf { it >= 0.0 }
    val valid = when (mode) {
        BillingMode.TOKEN ->
            priceOf(inputPrice) != null && priceOf(cachedPrice) != null && priceOf(outputPrice) != null
        BillingMode.COUNT -> priceOf(requestPrice) != null
    }

    PientDialog(
        title = "编辑模型定价 - $model",
        onDismiss = onDismiss,
        confirmText = "保存",
        confirmEnabled = valid,
        showClose = false, // 底部已有取消按钮（全项目确认类弹窗口径）
        onConfirm = {
            val pricing = when (mode) {
                BillingMode.TOKEN -> ModelPricing(
                    billingMode = BillingMode.TOKEN,
                    inputPerMillion = fromCny(priceOf(inputPrice) ?: 0.0, current.currency, rate),
                    outputPerMillion = fromCny(priceOf(outputPrice) ?: 0.0, current.currency, rate),
                    cachedInputPerMillion = fromCny(priceOf(cachedPrice) ?: 0.0, current.currency, rate),
                    currency = current.currency,
                )
                BillingMode.COUNT -> current.copy(
                    billingMode = BillingMode.COUNT,
                    pricePerRequest = fromCny(priceOf(requestPrice) ?: 0.0, current.currency, rate),
                )
            }
            // 同名模型挂在多个服务商下时一并写入（覆盖键 = provider:model）
            providers.forEach { p -> AiConfigStore.setPricing(p, model, pricing) }
            onDismiss()
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "当前计价币种：CNY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "计费方式",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            )
            PientSegmented(
                labels = listOf("按Token计费", "按次计费"),
                selected = if (mode == BillingMode.TOKEN) 0 else 1,
                onSelect = { mode = if (it == 0) BillingMode.TOKEN else BillingMode.COUNT },
                modifier = Modifier.fillMaxWidth(),
            )
            if (mode == BillingMode.TOKEN) {
                Text(
                    "设置每百万Token价格（CNY）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                PriceField("输入价格（每百万Token） (CNY)", inputPrice) { inputPrice = it }
                PriceField("缓存输入价格（每百万Token） (CNY)", cachedPrice) { cachedPrice = it }
                PriceField("输出价格（每百万Token） (CNY)", outputPrice) { outputPrice = it }
            } else {
                Text(
                    "设置每次API请求价格（CNY）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                PriceField("单次请求价格（CNY）", requestPrice) { requestPrice = it }
            }
            if (!valid) {
                Text(
                    "请输入有效的非负价格",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
