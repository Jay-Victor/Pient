package com.pient.app.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pient.app.data.Msg
import com.pient.app.data.SettingsStore
import com.pient.app.data.ToolStatus
import com.pient.app.ui.components.MarkdownText
import com.pient.app.ui.components.PermissionRequestDialog
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.theme.MonoFont
import com.pient.app.ui.theme.PientPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 消息区（P1 核心，设计计划 3.3）：
 * 用户消息 = 主色浅底胶囊右对齐；助手 = 无底卡片 Markdown；
 * 思考块可折叠 + 级别徽标；工具调用等宽小卡；工具结果缩略；
 * 压缩条目 / 分支切换条 / usage 统计。消息区禁止玻璃。
 */
@Composable
fun ChatMessages(
    messages: List<Msg>,
    isStreaming: Boolean,
    streamDraft: String,
    onMessageLongPress: ((Int, Rect) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showPermDemo by remember { mutableStateOf(false) }
    var locatorOpen by remember { mutableStateOf(false) }
    // 各消息气泡的根坐标（长按菜单锚点；LazyColumn 回收后需重新上报）
    val bubbleBounds = remember { mutableStateMapOf<Int, Rect>() }
    // 长按超时配置（消息长按 fork 检测用）
    val viewConfig = LocalViewConfiguration.current

    // 定位器显示时机（Operit 参考）：用户滑动（含惯性滚动）时显示，停止滚动 1.2s 后隐藏
    var navigatorVisible by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collectLatest { inProgress ->
                if (inProgress) {
                    navigatorVisible = true
                } else {
                    delay(1200)
                    navigatorVisible = false
                }
            }
    }

    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.totalItemsCount - 1
            info.visibleItemsInfo.lastOrNull()?.index ?: 0 >= last - 1
        }
    }

    // 定位器进度：当前可见首项在全部消息中的位置
    val locatorProgress by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            if (total <= 1) 0f
            else (listState.firstVisibleItemIndex.toFloat() / (total - 1).toFloat()).coerceIn(0f, 1f)
        }
    }

    // 流式 / 新消息：位于底部时自动跟随（不打断用户上滚浏览）
    LaunchedEffect(messages.size, streamDraft.length) {
        if (atBottom && listState.layoutInfo.totalItemsCount > 0) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(messages.size, key = { it }) { idx ->
                val msg = messages[idx]
                // 长按 fork 入口：仅 User/Assistant 气泡响应（2026-09-02 分支功能设计 §4.1）
                val longPressable = msg is Msg.User || msg is Msg.Assistant
                Box(
                    Modifier
                        .onGloballyPositioned { bubbleBounds[idx] = it.boundsInRoot() }
                        .then(
                            if (longPressable) Modifier.pointerInput(idx) {
                                // ★ 不能 detectTapGestures：其 awaitFirstDown 默认
                                // requireUnconsumed=true，而 AI 回答内 MarkdownText 的
                                // ClickableText（链接点击）会 consume down → 父级手势
                                // 永远不启动（长按 AI 消息不弹菜单的根因）。
                                // 改用 requireUnconsumed=false + AwaitPointerEventScope
                                // 成员的 withTimeout 计时，与子级手势解耦且不消费事件
                                // （链接点击/思考卡展开不受影响）。
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitFirstDown(requireUnconsumed = false)
                                        try {
                                            withTimeout(viewConfig.longPressTimeoutMillis) {
                                                waitForUpOrCancellation()
                                            }
                                        } catch (_: PointerEventTimeoutCancellationException) {
                                            onMessageLongPress?.invoke(
                                                idx,
                                                bubbleBounds[idx] ?: Rect.Zero,
                                            )
                                        }
                                    }
                                }
                            } else Modifier,
                        ),
                ) {
                    MessageCard(msg, onRequestPermission = { showPermDemo = true })
                }
            }
            if (isStreaming) {
                item { StreamingCard(streamDraft) }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }

        // 回到底部（滚动中才出现）
        if (!atBottom) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                        CircleShape,
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable(onClick = {
                        scope.launch {
                            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                        }
                    }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.ExpandLess, "回到底部",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // 消息定位器（Operit 参考：右缘胶囊 + 进度线点；滑动即显示，停止 1.2s 后隐藏）
        if (navigatorVisible) {
            val bubbleColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
            val anchorLineColor = MaterialTheme.colorScheme.outlineVariant
            val anchorDotColor = MaterialTheme.colorScheme.primary
            val navigatorBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            val navigatorShape = RoundedCornerShape(
                topStart = 14.dp,
                bottomStart = 14.dp,
                topEnd = 8.dp,
                bottomEnd = 8.dp,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(onClick = { locatorOpen = true }),
            ) {
                // 指向右缘的小三角箭头
                Canvas(
                    modifier = Modifier
                        .offset(x = (-1).dp)
                        .size(width = 8.dp, height = 16.dp),
                ) {
                    val arrowPath = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path = arrowPath, color = bubbleColor)
                }
                // 进度胶囊：竖线 + 圆点
                Box(
                    modifier = Modifier
                        .clip(navigatorShape)
                        .background(bubbleColor)
                        .border(1.dp, navigatorBorderColor, navigatorShape),
                ) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(56.dp)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.size(width = 6.dp, height = 32.dp)) {
                            val centerX = size.width / 2f
                            val topY = 2.dp.toPx()
                            val bottomY = size.height - 2.dp.toPx()
                            val dotCenterY = topY + (bottomY - topY) * locatorProgress
                            drawLine(
                                color = anchorLineColor,
                                start = Offset(centerX, topY),
                                end = Offset(centerX, bottomY),
                                strokeWidth = 1.5.dp.toPx(),
                            )
                            drawCircle(
                                color = anchorDotColor,
                                radius = 3.dp.toPx(),
                                center = Offset(centerX, dotCenterY),
                            )
                        }
                    }
                }
            }
        }
    }

    // 消息定位弹窗：列表预览 + 点击跳转
    if (locatorOpen) {
        val currentIndex = listState.firstVisibleItemIndex
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim)
                .clickable(onClick = { locatorOpen = false }),
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
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "消息定位",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .heightIn(max = 340.dp),
                    ) {
                        itemsIndexed(messages, key = { i, _ -> i }) { idx, msg ->
                            val isCurrent = idx == currentIndex
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                        else Color.Transparent,
                                    )
                                    .clickable(onClick = {
                                        locatorOpen = false
                                        scope.launch {
                                            listState.animateScrollToItem(idx)
                                        }
                                    })
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    "${idx + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFont),
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(30.dp),
                                )
                                Text(
                                    locatorPreview(msg),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                                )
                            }
                        }
                    }
                    PientButton(
                        "取消",
                        onClick = { locatorOpen = false },
                        primary = false,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }
        }
    }

    // 权限请求弹窗（原型演示：工具调用时的三选授权，设计计划第 7 章）
    if (showPermDemo) {
        Box(Modifier.fillMaxSize()) {
            PermissionRequestDialog(
                toolName = "bash",
                paramSummary = "rm -rf /data/local/tmp/pient-test/ && echo done",
                dangerous = true,
                onAllowOnce = { showPermDemo = false },
                onAlwaysAllow = { showPermDemo = false },
                onDeny = { showPermDemo = false },
                onDismiss = { showPermDemo = false },
            )
        }
    }
}

@Composable
private fun MessageCard(msg: Msg, onRequestPermission: () -> Unit) {
    when (msg) {
        is Msg.User -> UserBubble(msg)
        is Msg.Assistant -> AssistantCard(msg)
        is Msg.Thinking -> ThinkingCard(msg)
        is Msg.ToolCall -> ToolCallCard(msg, onRequestPermission)
        is Msg.ToolResult -> ToolResultCard(msg)
        is Msg.Compaction -> CompactionCard(msg)
        is Msg.BranchBar -> BranchBarCard(msg)
    }
}

// ───────────────────────────── 长按消息菜单（fork 入口） ─────────────────────────────

/**
 * 长按消息气泡的上下文菜单（2026-09-02 分支功能设计 §4.1）：
 * 「从此处创建新会话」= fork（官方 RPC 原型）；「复制」= mock Toast。
 * 锚定气泡：默认在气泡下方，近屏幕底部时翻转到上方；点外关闭无 scrim（外层处理）。
 */
@Composable
fun ForkContextMenu(
    anchor: Rect,
    forkEnabled: Boolean,
    onFork: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenW = configuration.screenWidthDp.dp
    val screenH = configuration.screenHeightDp.dp
    val menuW = 208.dp
    val menuH = 96.dp // 两行 44dp + 上下 4dp padding
    val x = with(density) { anchor.left.toDp() }.coerceIn(8.dp, screenW - menuW - 8.dp)
    val belowY = with(density) { anchor.bottom.toDp() } + 8.dp
    val y = if (belowY + menuH < screenH - 8.dp) belowY
    else (with(density) { anchor.top.toDp() } - menuH - 8.dp).coerceAtLeast(8.dp)
    PientPanel(
        modifier = modifier
            .offset(x = x, y = y)
            .width(menuW),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            MenuRow(
                icon = Icons.Outlined.ForkRight,
                label = "从此处创建新会话",
                enabled = forkEnabled,
                onClick = onFork,
            )
            MenuRow(
                icon = Icons.Outlined.ContentCopy,
                label = "复制",
                enabled = true,
                onClick = onCopy,
            )
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Icon(
            icon,
            null,
            tint = if (enabled) MaterialTheme.colorScheme.onBackground
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onBackground
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

// ───────────────────────────── 用户消息 ─────────────────────────────

@Composable
private fun UserBubble(msg: Msg.User) {
    val bubble = SettingsStore.bubbleStyle == com.pient.app.data.BubbleStyle.BUBBLE
    val shape = if (bubble) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    else RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), shape)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (msg.attachments.isNotEmpty()) {
                msg.attachments.forEach { att ->
                    Text(
                        att.kind.emoji + att.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

// ───────────────────────────── 助手消息 ─────────────────────────────

@Composable
private fun AssistantCard(msg: Msg.Assistant) {
    Column(Modifier.fillMaxWidth()) {
        MarkdownText(msg.markdown, modifier = Modifier.padding(top = 2.dp))
        if (msg.usage != null) {
            Text(
                "in ${tok(msg.usage.inTokens)} · out ${tok(msg.usage.outTokens)} · " +
                    "cache ${tok(msg.usage.cacheTokens)} · \$${msg.usage.costUsd}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = MonoFont,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.95f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun tok(n: Int): String {
    val k = n / 1000
    return if (k > 0) "${k}.${(n % 1000) / 100}k" else "$n"
}

// ───────────────────────────── 思考块 ─────────────────────────────

@Composable
private fun ThinkingCard(msg: Msg.Thinking) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { expanded = !expanded })
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                Icons.Outlined.Psychology, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                "思考",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 6.dp),
            )
            Text(
                msg.level,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = MonoFont,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 1.dp),
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        if (expanded) {
            Text(
                msg.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            )
        }
    }
}

// ───────────────────────────── 工具调用 ─────────────────────────────

@Composable
private fun ToolCallCard(msg: Msg.ToolCall, onRequestPermission: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { expanded = !expanded })
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                Icons.Outlined.Build, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                msg.name,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = MonoFont,
                modifier = Modifier.padding(start = 6.dp),
            )
            Text(
                msg.params,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = MonoFont,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            when (msg.status) {
                ToolStatus.RUNNING -> CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                ToolStatus.DONE -> Icon(
                    Icons.Outlined.Check, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                ToolStatus.FAILED -> Icon(
                    Icons.Outlined.Close, null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (expanded && msg.detail != null) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
                Text(
                    msg.detail,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFont, fontSize = 11.sp, lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "演示：权限请求弹窗 →",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable(onClick = onRequestPermission),
                )
            }
        }
    }
}

// ───────────────────────────── 工具结果 ─────────────────────────────

@Composable
private fun ToolResultCard(msg: Msg.ToolResult) {
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) msg.full ?: msg.preview else msg.preview.lines().take(4).joinToString("\n")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "↳ 结果",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (msg.full != null) {
                Text(
                    if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = { expanded = !expanded }),
                )
            }
        }
        Text(
            shown,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFont, fontSize = 12.sp, lineHeight = 18.sp),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ───────────────────────────── 压缩条目 ─────────────────────────────

@Composable
private fun CompactionCard(msg: Msg.Compaction) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { expanded = !expanded })
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                Icons.Outlined.Summarize, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                "上下文已压缩",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 6.dp),
            )
            Text(
                "前 ${tok(msg.tokensBefore)} · 节省 ${tok(msg.saved)}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = MonoFont,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "查看摘要",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded) {
            Text(
                msg.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            )
        }
    }
}

// ───────────────────────────── 分支切换条 ─────────────────────────────

@Composable
private fun BranchBarCard(msg: Msg.BranchBar) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            Icons.Outlined.SwapHoriz, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            msg.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        Text(
            "${msg.branchCount} 个分支 · 查看",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ───────────────────────────── 流式输出卡 ─────────────────────────────

/** 消息定位预览文案（换行折叠为空格，超长由列表行 Ellipsis 截断） */
private fun locatorPreview(msg: Msg): String = when (msg) {
    is Msg.User -> msg.text.replace('\n', ' ')
    is Msg.Assistant -> msg.markdown.replace('\n', ' ')
    is Msg.Thinking -> "思考 · ${msg.level}"
    is Msg.ToolCall -> "工具 · ${msg.name}"
    is Msg.ToolResult -> "结果 · ${msg.preview.replace('\n', ' ')}"
    is Msg.Compaction -> "上下文已压缩 · 节省 ${msg.saved} tokens"
    is Msg.BranchBar -> "分支 · ${msg.label}"
}

@Composable
private fun StreamingCard(draft: String) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursorAlpha",
    )
    Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        MarkdownText(draft)
        Text(
            "▍",
            color = MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
