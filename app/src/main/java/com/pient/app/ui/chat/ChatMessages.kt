package com.pient.app.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pient.app.data.Attachment
import com.pient.app.data.Msg
import com.pient.app.data.SettingsStore
import com.pient.app.data.ToolStatus
import com.pient.app.ui.components.MarkdownText
import com.pient.app.ui.components.PermissionRequestDialog
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.theme.LocalPientIsDark
import com.pient.app.ui.theme.LocalPientUserBubble
import com.pient.app.ui.theme.MonoFont
import com.pient.app.ui.theme.PientPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 消息区（P1 核心，设计计划 3.3；2026-09-08 消息框/回复样式重设计对齐 pi-web/pi/Operit）：
 * 用户消息 = userBubble 令牌底右对齐胶囊（Operit 20/4/20/20 尾角、85% 宽、44dp 最小高；
 * FLAT 模式 pi-web 12dp 圆角 + accent 20% 边框）；附件 chip 气泡上方（Operit AttachmentTag）；
 * 助手 = 无底卡片 Markdown + pi-web 模型标签行/usage 行；思考块/工具卡 pi-web 几何与绿红语义；
 * 工具卡内嵌成对结果（多级折叠：一级工具卡、二级结果区，2026-09-08）；压缩条目 / usage 统计。
 * 消息区禁止玻璃。
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

    // 进入会话（首次组合 / 切换会话）默认滚到消息最底部（2026-09-09 用户定：
    // 恢复会话后视口停在最上方不符合使用习惯）。以列表引用判切换——
    // 同一会话内的消息增删由下方跟随滚动 effect 处理，这里只认列表换新。
    var lastList by remember { mutableStateOf<List<Msg>?>(null) }
    LaunchedEffect(messages) {
        if (messages !== lastList) {
            lastList = messages
            withFrameNanos { } // 等一帧布局完成再取 total（否则是旧值）
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) listState.scrollToItem(total - 1)
        }
    }
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
            val lastVisible = info.visibleItemsInfo.lastOrNull()
            // 旧判定（lastVisible.index >= last-1）在最后一条消息比视口高时恒真：
            // 视口无论停在哪，最后可见条目都是它 → 回到底部按钮永不出现（2026-09-09 修复）。
            // 新判定 = 最后可见条目是列表末条（含 4dp 尾 spacer）且其底边贴近视口底（容差 16px）。
            if (lastVisible == null) info.totalItemsCount <= 1
            else lastVisible.index >= last - 1 &&
                lastVisible.offset + lastVisible.size <= info.viewportEndOffset + 16
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

    // 流式 / 新消息跟随滚动。2026-09-09 三轮修复（用户报障「发送后页面不上滑」）：
    // 1. LaunchedEffect 在新条目完成布局前启动，layoutInfo 还是上一帧旧值——
    //    先 withFrameNanos 等一帧布局完成，再取新 total 滚到底（旧实现滚到旧末尾=不动）；
    // 2. 必须用 scrollToItem 瞬时滚动：animateScrollToItem 会被下一次重启（每个流式增量
    //    都重启 effect）取消在半途 → 视口越拖越落后，回复完成时已不在底部、不再跟随；
    // 3. 用户主动发送 = 无条件滚到底（用户想看自己的消息与回复；且 IME 弹出会把视口
    //    压矮、让发送瞬间的 atBottom 变 false）；被动到达的回复才遵守「不打断上滚浏览」。
    var prevCount by remember { mutableStateOf(messages.size) }
    LaunchedEffect(messages.size, streamDraft.length, isStreaming) {
        val userJustSent = messages.size > prevCount && messages.lastOrNull() is Msg.User
        prevCount = messages.size
        if (userJustSent || atBottom) {
            withFrameNanos { }
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) listState.scrollToItem(total - 1)
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
                // 成对工具结果（ToolCall 紧跟 ToolResult）已并入工具卡渲染，此处跳过
                if (msg is Msg.ToolResult && idx > 0 && messages[idx - 1] is Msg.ToolCall) return@items
                // 思考块并入 AI 回答块（其后存在助手回答时跳过独立渲染，由助手卡内折叠行承载）
                if (msg is Msg.Thinking && followedByAssistant(messages, idx)) return@items
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
                    MessageCard(
                        msg,
                        toolResult = if (msg is Msg.ToolCall && idx + 1 < messages.size)
                            messages[idx + 1] as? Msg.ToolResult else null,
                        thinking = if (msg is Msg.Assistant) precedingThinking(messages, idx) else null,
                        onRequestPermission = { showPermDemo = true },
                    )
                }
            }
            if (isStreaming) {
                item { StreamingCard(streamDraft) }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }

        // 回到底部（未在底部时出现；2026-09-09 用户定：图标用向下箭头 ↓）
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
                    Icons.Outlined.ExpandMore, "回到底部",
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
private fun MessageCard(
    msg: Msg,
    toolResult: Msg.ToolResult? = null,
    thinking: Msg.Thinking? = null,
    onRequestPermission: () -> Unit,
) {
    when (msg) {
        is Msg.User -> UserBubble(msg)
        is Msg.Assistant -> AssistantCard(msg, thinking)
        is Msg.Thinking -> ThinkingCard(msg)
        is Msg.ToolCall -> ToolCallCard(msg, toolResult, onRequestPermission)
        is Msg.ToolResult -> ToolResultCard(msg)   // 仅未成对的结果走独立卡
        is Msg.Compaction -> CompactionCard(msg)
    }
}

// ───────────────────────────── 思考并入回答块的判定 ─────────────────────────────

/** idx 之后是否存在助手回答（跨工具卡/结果/压缩条目扫描；遇用户消息或另一思考块即止） */
private fun followedByAssistant(messages: List<Msg>, idx: Int): Boolean {
    var i = idx + 1
    while (i < messages.size) {
        when (messages[i]) {
            is Msg.Assistant -> return true
            is Msg.User, is Msg.Thinking -> return false
            else -> i++
        }
    }
    return false
}

/** idx 之前最近的思考块（跨工具卡/结果/压缩条目扫描；遇用户/助手消息即止，无则 null） */
private fun precedingThinking(messages: List<Msg>, idx: Int): Msg.Thinking? {
    var i = idx - 1
    while (i >= 0) {
        when (val m = messages[i]) {
            is Msg.Thinking -> return m
            is Msg.User, is Msg.Assistant -> return null
            else -> i--
        }
    }
    return null
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

/** 附件 chip（Operit AttachmentTag 规格：24dp 高、12dp 圆角、不透明实底 = 气泡色、图标 12dp + 名称 120dp 截断） */
@Composable
private fun AttachmentChip(att: Attachment, bubbleBg: Color) {
    Row(
        modifier = Modifier
            .height(24.dp)
            .background(bubbleBg, RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            attachmentIcon(att.kind), null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            att.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp),
        )
    }
}

/**
 * 用户消息气泡（2026-09-08 重设计，对齐三源）：
 * - 几何（Operit BubbleUserMessageComposable）：右对齐、气泡最大宽 = 可用宽 85%、
 *   BUBBLE 模式圆角 (20,4,20,20)（尾角右上）、最小高 44dp、内边距 12dp、无边框；
 * - FLAT 模式（pi-web UserMessageView）：12dp 圆角、1dp accent 20% 边框、内边距 8×12；
 * - 底色 = userBubble 令牌（Hermes --userBubble 同源，随主色联动）；
 * - 附件 chip 在气泡上方右对齐一行（Operit trailing attachments）。
 */
@Composable
private fun UserBubble(msg: Msg.User) {
    val userBubbleBg = LocalPientUserBubble.current
    val bubble = SettingsStore.bubbleStyle == com.pient.app.data.BubbleStyle.BUBBLE
    val shape = if (bubble) RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp)
    else RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        if (msg.attachments.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                msg.attachments.forEach { att -> AttachmentChip(att, userBubbleBg) }
            }
        }
        BoxWithConstraints {
            val maxBubbleWidth = maxWidth * 0.85f
            Column(
                modifier = Modifier
                    .widthIn(max = maxBubbleWidth)
                    .then(if (bubble) Modifier.defaultMinSize(minHeight = 44.dp) else Modifier)
                    .background(userBubbleBg, shape)
                    .then(
                        if (bubble) Modifier
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), shape),
                    )
                    .padding(
                        if (bubble) PaddingValues(12.dp)
                        else PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ),
            ) {
                Text(
                    msg.text,
                    style = userTextStyle(),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

// ───────────────────────────── 助手消息 ─────────────────────────────

/**
 * 助手回复（2026-09-08 重设计，对齐 pi / pi-web）：无气泡卡片、纯 Markdown 直排；
 * 头部模型标签行（pi-web：11sp、弱化色、下距 4dp）；底部 usage 行（pi-web 顺序 in·out·cache·$、11sp）。
 * 2026-09-08 思考并入回答块（用户定）：模型标签下接「思考」+ v/^ 折叠行，展开显示思考文本，
 * 其下直接接 markdown 正文（思考不再单独成卡）。
 */
@Composable
private fun AssistantCard(msg: Msg.Assistant, thinking: Msg.Thinking? = null) {
    Column(Modifier.fillMaxWidth()) {
        if (!msg.model.isNullOrBlank()) {
            Text(
                msg.model,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (thinking != null) {
            ThinkingFold(thinking)
        }
        MarkdownText(msg.markdown, modifier = Modifier.padding(top = 2.dp))
        if (msg.usage != null) {
            Text(
                "in ${tok(msg.usage.inTokens)} · out ${tok(msg.usage.outTokens)} · " +
                    "cache ${tok(msg.usage.cacheTokens)} · \$${msg.usage.costUsd}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.95f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * 回答内思考折叠行（2026-09-08 用户定）：一行「思考」+ 级别徽标 + v/^ 箭头，
 * 整行点击折叠/展开；展开后思考文本以弱化色显示，下方直接接回答正文。
 */
@Composable
private fun ThinkingFold(thinking: Msg.Thinking) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = { expanded = !expanded })
            .padding(vertical = 4.dp),
    ) {
        Text(
            "思考",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            thinking.level,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = MonoFont,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 6.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)
                .padding(horizontal = 8.dp, vertical = 1.dp),
        )
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp).size(16.dp),
        )
    }
    if (expanded) {
        Text(
            thinking.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

private fun tok(n: Int): String {
    val k = n / 1000
    return if (k > 0) "${k}.${(n % 1000) / 100}k" else "$n"
}

/**
 * 用户消息正文样式（Hermes user-message 规格）：13sp 字号（--conversation-text-font-size 0.8125rem）、
 * 1.3 行高（--human-msg-line-height）。字号按全局字号设置等比缩放（基准 14sp）。
 */
@Composable
private fun userTextStyle(): TextStyle {
    val base = MaterialTheme.typography.bodyMedium
    return base.copy(
        fontSize = base.fontSize * (13f / 14f),
        lineHeight = 1.3.em,
    )
}

// ───────────────────────────── 思考块 ─────────────────────────────

/**
 * 思考块（2026-09-08 对齐 pi-web ThinkingBlock）：1dp outlineVariant 边框、6dp 圆角、
 * 头部内边距 6×10（pi-web 6px 10px）、展开正文 10/10/8（pi-web 8px 10px）。
 * 保留 Pient 特有：级别徽标（pi thinking 五档）。
 */
@Composable
private fun ThinkingCard(msg: Msg.Thinking) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { expanded = !expanded })
                .padding(horizontal = 10.dp, vertical = 6.dp),
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
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
            )
        }
    }
}

// ───────────────────────────── 工具调用 ─────────────────────────────

/**
 * 工具调用卡（2026-09-08 对齐 pi-web ToolCallBlock）：
 * - 成功绿 / 失败红语义：边框 25%/45%、底 4%/5%（pi-web rgba(34,197,94,…)/rgba(248,113,113,…)）
 *   映射到 Pient GitHub 绿 #3FB950/#1A7F37 与 error 令牌；RUNNING 中性；
 * - 7dp 圆角（pi-web 7）、头部内边距 6×10、工具名 mono 11sp 600、参数 mono 11sp、间距 7dp；
 * - 展开详情 12sp/18sp（pi-web pre 12px/1.5）。
 * 多级折叠（2026-09-08 用户定：最终回答之外全部可折叠）：
 * 一级 = 工具卡头部；二级 = 卡内嵌的成对结果区（默认收起 4 行预览，可再展开全量）。
 * 保留 Pient 特有：RUNNING/完成/失败状态图标与权限演示入口。
 */
@Composable
private fun ToolCallCard(
    msg: Msg.ToolCall,
    result: Msg.ToolResult?,
    onRequestPermission: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var resultExpanded by remember { mutableStateOf(false) }
    val isDark = LocalPientIsDark.current
    val toolGreen = if (isDark) Color(0xFF3FB950) else Color(0xFF1A7F37)
    val (borderColor, bgColor, nameColor) = when (msg.status) {
        ToolStatus.DONE -> Triple(
            toolGreen.copy(alpha = 0.25f),
            toolGreen.copy(alpha = 0.04f),
            toolGreen,
        )
        ToolStatus.FAILED -> Triple(
            MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.error.copy(alpha = 0.05f),
            MaterialTheme.colorScheme.error,
        )
        ToolStatus.RUNNING -> Triple(
            MaterialTheme.colorScheme.outlineVariant,
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(7.dp))
            .border(1.dp, borderColor, RoundedCornerShape(7.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { expanded = !expanded })
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(
                Icons.Outlined.Build, null,
                tint = nameColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                msg.name,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                fontFamily = MonoFont,
                fontWeight = FontWeight.SemiBold,
                color = nameColor,
                modifier = Modifier.padding(start = 6.dp),
            )
            Text(
                msg.params,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                fontFamily = MonoFont,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 7.dp),
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
        // 展开态（2026-09-08：任何工具调用都可展开——无 detail 时展示完整参数）
        if (expanded) {
            Column(
                modifier = Modifier
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
            ) {
                Text(
                    msg.detail ?: msg.params,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = MonoFont,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (msg.detail != null) {
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
        // 二级折叠：成对结果嵌在工具卡内（默认收起 4 行预览；仅在工具卡展开时可见）
        if (expanded && result != null) {
            val shown = if (resultExpanded) result.full ?: result.preview
            else result.preview.lines().take(4).joinToString("\n")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f))
                    .border(
                        BorderStroke(1.dp, borderColor.copy(alpha = 0.5f)),
                        RoundedCornerShape(bottomStart = 7.dp, bottomEnd = 7.dp),
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { resultExpanded = !resultExpanded })
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        "↳ 结果",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (resultExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        if (resultExpanded) "收起" else "展开",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    shown,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = MonoFont,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    ),
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
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
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f), RoundedCornerShape(7.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(7.dp))
            .padding(12.dp),
    ) {
        // 整行可点切换展开/收起（2026-09-08：任何结果都可折叠；无 full 时展开显示完整预览）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { expanded = !expanded }),
        ) {
            Text(
                "↳ 结果",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                if (expanded) "收起" else "展开",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
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

// 已移除（2026-09-08 用户定）：会话内分支在 /tree 画布页展示、会话外分支在会话列表展示，
// 聊天流内不再出现分支卡片。

// ───────────────────────────── 流式输出卡 ─────────────────────────────

/** 消息定位预览文案（换行折叠为空格，超长由列表行 Ellipsis 截断） */
private fun locatorPreview(msg: Msg): String = when (msg) {
    is Msg.User -> msg.text.replace('\n', ' ')
    is Msg.Assistant -> msg.markdown.replace('\n', ' ')
    is Msg.Thinking -> "思考 · ${msg.level}"
    is Msg.ToolCall -> "工具 · ${msg.name}"
    is Msg.ToolResult -> "结果 · ${msg.preview.replace('\n', ' ')}"
    is Msg.Compaction -> "上下文已压缩 · 节省 ${msg.saved} tokens"
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
