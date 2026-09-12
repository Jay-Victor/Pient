package com.pient.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.pient.app.data.Attachment
import com.pient.app.data.Msg
import com.pient.app.data.Quote
import com.pient.app.data.SettingsStore
import com.pient.app.data.ToolStatus
import com.pient.app.data.markdownToPlainText
import com.pient.app.ui.components.MarkdownText
import com.pient.app.ui.components.PermissionRequestDialog
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.components.PientSegmented
import com.pient.app.ui.theme.LocalPientIsDark
import com.pient.app.ui.theme.LocalPientUserBubble
import com.pient.app.ui.theme.MonoFont
import com.pient.app.ui.theme.PientPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    listState: LazyListState,
    /** 流式思考文本（思考模式开启时；Hermes 口径的实时预览数据源） */
    streamThinking: String = "",
    /** 本轮思考起点（毫秒；0 = 无）——实时计时用 */
    streamThinkingStartedAt: Long = 0L,
    /** 本次运行内流式思考块所在下标（-1 = 无）：回答落地后该块保持展开（Hermes live preview） */
    liveThinkingIndex: Int = -1,
    bottomInset: Dp = 0.dp,
    /**
     * 上屏窗口起点（长会话防护，2026-09-12）：< startIndex 的更早消息不进列表，
     * 列表首行改为「显示更早的消息」胶囊按钮；0 = 全部消息都在窗口内。
     */
    startIndex: Int = 0,
    /** 点「显示更早的消息」：返回本次新增条数（调用方据此保持视口锚点） */
    onShowEarlier: () -> Int = { 0 },
    onOpenLocator: () -> Unit,
    onMessageLongPress: ((Int, Rect) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var showPermDemo by remember { mutableStateOf(false) }

    // 进入会话（首次组合 / 切换会话 / 分支换叶）默认落在消息最底部。
    //
    // ★ 必须在**首次测量之前**就定位（2026-09-12 修复「打开 Pient 一瞬显示『显示更早的消息』
    //   按钮和最旧的那批消息」）：旧实现在 LaunchedEffect 里 `withFrameNanos` 后再 scrollToItem，
    //   于是**首帧一定画在列表顶部**（长会话就是按钮 + 窗口里最旧的消息），下一帧才跳到底部。
    //   真机上这一两帧肉眼可见（页面像先错位再归位）；模拟器录屏只有 ~12fps、截图 220ms 一张，
    //   抓不到帧不代表没有 —— 代码路径本身决定了它必然发生。
    //   requestScrollToItem 只是登记目标位置、在下次测量生效，所以首帧就已经到底。
    val showEarlier = startIndex > 0
    val itemCount = (if (showEarlier) 1 else 0) + (messages.size - startIndex) +
        (if (isStreaming) 1 else 0) + 1   // + 尾部 spacer
    var lastList by remember { mutableStateOf<List<Msg>?>(null) }
    if (messages !== lastList) {
        lastList = messages
        if (itemCount > 0) listState.requestScrollToItem(itemCount - 1)
    }
    // 各消息气泡的根坐标（长按菜单锚点；LazyColumn 回收后需重新上报）。
    // ★ 必须是**普通 HashMap**而不是 mutableStateMapOf：写入发生在 onGloballyPositioned
    // （布局阶段），而长按回调里读它——用快照 Map 会让每次布局都写状态、又反查到组合里，
    // 每个可见项在每帧都多走一轮组合（长会话卡顿源之一，2026-09-12 修复）。
    // 该 Map 只在长按那一刻被读，不需要参与重组。
    val bubbleBounds = remember { HashMap<Int, Rect>() }
    // 长按超时配置（消息长按 fork 检测用）
    val viewConfig = LocalViewConfiguration.current

    // 定位器显示时机（Operit 参考）：用户滑动（含惯性滚动）时显示，停止滚动 2s 后隐藏
    var navigatorVisible by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collectLatest { inProgress ->
                if (inProgress) {
                    navigatorVisible = true
                } else {
                    delay(2000)
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

    // 定位器进度：当前可见首项在全部消息中的位置（按**绝对**消息下标算，
    // 让进度条在只加载了尾部窗口时也能反映真实位置，2026-09-12）
    val locatorProgress by remember {
        derivedStateOf {
            val total = messages.size
            if (total <= 1) 0f
            else ((startIndex + listState.firstVisibleItemIndex).toFloat() / (total - 1).toFloat())
                .coerceIn(0f, 1f)
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
            // 底部留出输入栏 dock 的高度（2026-09-12：dock 改为覆盖在消息之上的浮层，
            // 消息可滑到 dock 之下，最后一条需能被滚到 dock 上沿之上）
            contentPadding = PaddingValues(
                start = 14.dp,
                top = 10.dp,
                end = 14.dp,
                bottom = 10.dp + bottomInset,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 「显示更早的消息」（长会话防护，2026-09-12；Hermes showEarlier 同款胶囊按钮）：
            // 仅当更早消息被窗口挡住时出现，点击往前翻一页，并把新加载的一页推进视野。
            if (startIndex > 0) {
                item(key = "show-earlier") {
                    // 触控目标 = 整行 48dp（Material 最小触控尺寸），胶囊视觉不变：
                    // 旧实现把 clickable 挂在只含 12dp/4dp 内边距的胶囊上（高 ≈26dp），
                    // 手机上容易点空——与「点几次才有反应」的体感叠加（2026-09-12）。
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                // 翻页后**把刚加载的消息推进视野**：直接滚到列表顶端（第 0 行 = 按钮本身，
                                // 它在翻页前后都是 0 行，所以不必等布局）。
                                //
                                // 为什么不再自己算锚点：
                                // ① Compose 的 LazyColumn 本来就按 key 保持滚动位置——往前面插入条目时，
                                //    原可见项会留在原位；再手动滚一次会与它叠加，视口位置不可控
                                //    （2026-09-12 实测：同一个操作一次位移 291px、另一次纹丝不动）。
                                // ② 就算把位置钉准，新内容也全在视口**上方**——用户点完看不到任何变化，
                                //    真机反馈就是「点了无效、没加载出消息」。滚到顶端则新加载的一页直接可见。
                                val added = onShowEarlier()
                                if (added > 0) scope.launch { listState.scrollToItem(0) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "显示更早的消息",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                                    RoundedCornerShape(50),
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            items(messages.size - startIndex, key = { startIndex + it }) { i ->
                val idx = startIndex + i
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
                    val thinkingIdx = if (msg is Msg.Assistant) precedingThinkingIndex(messages, idx) else -1
                    MessageCard(
                        msg,
                        toolResult = if (msg is Msg.ToolCall && idx + 1 < messages.size)
                            messages[idx + 1] as? Msg.ToolResult else null,
                        thinking = thinkingIdx.takeIf { it >= 0 }?.let { messages[it] as? Msg.Thinking },
                        // 展开初值：刚流式完的思考块（本运行内的 live preview）保持展开，历史载入的收起
                        thinkingExpandedDefault = (thinkingIdx >= 0 && thinkingIdx == liveThinkingIndex) ||
                            (msg is Msg.Thinking && idx == liveThinkingIndex),
                        onRequestPermission = { showPermDemo = true },
                    )
                }
            }
            if (isStreaming) {
                item {
                    StreamingCard(
                        draft = streamDraft,
                        thinking = streamThinking,
                        thinkingStartedAt = streamThinkingStartedAt,
                    )
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }

        // 回到底部（未在底部时出现；2026-09-09 用户定：图标用向下箭头 ↓）
        if (!atBottom) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        // 抬到输入栏 dock 之上（2026-09-12：dock 为浮层）
                        bottom = 16.dp + bottomInset,
                    )
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

        // 消息定位器（Operit 参考：右缘胶囊 + 进度线点；滑动即显示，停止 1.2s 后隐藏。
        // 2026-09-09：①离右缘留 8dp 空隙防误触；②出现=从右缘滑出、消失=滑回右缘）
        AnimatedVisibility(
            visible = navigatorVisible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
        ) {
            val bubbleColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
            val anchorLineColor = MaterialTheme.colorScheme.outlineVariant
            val anchorDotColor = MaterialTheme.colorScheme.primary
            val navigatorBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            // 胶囊形状（2026-09-09 修复不对称）：原 14/14/8/8 左圆角 14dp 使左缘直边
            // 只有 28dp（右缘 40dp），且 14+8=22dp > 胶囊宽 20dp——上下两角弧在顶/底边
            // 中段互相交叉出凹口。对称胶囊的圆角上限 = 宽的一半 = 10dp（两端半圆、左右直边
            // 各 36dp 等长），四角取 10dp。
            val navigatorShape = RoundedCornerShape(10.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clickable(onClick = onOpenLocator),
            ) {
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

// ───────────────────────────── 消息定位弹窗 ─────────────────────────────

/**
 * 消息定位弹窗：定位统计 + 搜索 + 筛选（全部/用户/AI）+ 逐条卡片列表 + 点击跳转。
 * 2026-09-09 从 ChatMessages 内部提升为独立组件、由 ChatScreen 根层 zIndex 3 挂载：
 * 原实现挂在 ChatMessages（顶栏下方消息区 Box）内，弹窗 scrim 的 fillMaxSize 被压到
 * 消息区范围，只压暗消息列表、盖不住顶栏与系统状态栏；提升到页面根层后 scrim 全屏铺开
 * （状态栏图标属系统层绘制，仍在 scrim 之上保持可见，这是 Android 的正常行为）。
 */
@Composable
fun MessageLocatorDialog(
    messages: List<Msg>,
    listState: LazyListState,
    /**
     * 上屏窗口起点（长会话只加载尾部时 > 0）：行下标 → **绝对**消息下标换算用
     * `windowStart + row - rowOffset`（rowOffset = 存在「显示更早的消息」按钮时占 1 行）。
     */
    windowStart: Int = 0,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    val rowOffset = if (windowStart > 0) 1 else 0
    val currentIndex = (windowStart + listState.firstVisibleItemIndex - rowOffset)
        .coerceIn(0, (messages.size - 1).coerceAtLeast(0))
    var locatorQuery by remember { mutableStateOf("") }
    var locatorFilter by remember { mutableStateOf(0) } // 0=全部 1=用户 2=AI
    var filterMenuOpen by remember { mutableStateOf(false) }
    val filtered = remember(messages, locatorQuery, locatorFilter) {
        messages.mapIndexedNotNull { i, msg ->
            val query = locatorQuery.trim()
            val passFilter = when (locatorFilter) {
                1 -> msg is Msg.User
                2 -> msg !is Msg.User
                else -> true
            }
            val passQuery = query.isEmpty() || locatorPreview(msg).contains(query, ignoreCase = true)
            if (passFilter && passQuery) i to msg else null
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .clickable(onClick = onDismiss),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "消息定位",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "当前定位：第${currentIndex + 1}/${messages.size}条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 搜索框 + 筛选器（全部/用户/AI）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Search, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        BasicTextField(
                            value = locatorQuery,
                            onValueChange = { locatorQuery = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            decorationBox = { inner ->
                                Box {
                                    if (locatorQuery.isEmpty()) {
                                        Text(
                                            "搜索消息",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                        if (locatorQuery.isNotEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clickable(onClick = { locatorQuery = "" })
                                    .padding(4.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Close, "清空搜索",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                    // 筛选器：图案按键 + 选项列表卡片（全部/用户/AI）
                    Box {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    if (locatorFilter != 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                    RoundedCornerShape(10.dp),
                                )
                                .border(
                                    1.dp,
                                    if (locatorFilter != 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable(onClick = { filterMenuOpen = true }),
                        ) {
                            Icon(
                                Icons.Outlined.FilterList, "筛选消息",
                                tint = if (locatorFilter != 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = filterMenuOpen,
                            onDismissRequest = { filterMenuOpen = false },
                        ) {
                            listOf("全部消息", "用户消息", "AI消息").forEachIndexed { i, label ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            label,
                                            color = if (i == locatorFilter) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onBackground,
                                        )
                                    },
                                    onClick = {
                                        locatorFilter = i
                                        filterMenuOpen = false
                                    },
                                    leadingIcon = null,
                                    trailingIcon = if (i == locatorFilter) {
                                        {
                                            Icon(
                                                Icons.Outlined.Check, null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    } else null,
                                )
                            }
                        }
                    }
                }
                // 跳转提示（搜索行下方）
                Text(
                    "点击任意一条消息即可快速跳转",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (filtered.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().height(160.dp).padding(top = 8.dp),
                    ) {
                        Text(
                            "无匹配消息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(filtered, key = { _, p -> p.first }) { _, (idx, msg) ->
                            val isCurrent = idx == currentIndex
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        else MaterialTheme.colorScheme.surfaceContainerLow,
                                    )
                                    .border(
                                        1.dp,
                                        if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable(onClick = { onJump(idx) })
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.width(32.dp)) {
                                        Text(
                                            "${idx + 1}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFont),
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            if (msg is Msg.User) "用户" else "AI",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        locatorPreview(msg),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                PientButton(
                    "取消",
                    onClick = onDismiss,
                    primary = false,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageCard(
    msg: Msg,
    toolResult: Msg.ToolResult? = null,
    thinking: Msg.Thinking? = null,
    thinkingExpandedDefault: Boolean = false,
    onRequestPermission: () -> Unit,
) {
    when (msg) {
        is Msg.User -> UserBubble(msg)
        is Msg.Assistant -> AssistantCard(msg, thinking, thinkingExpandedDefault)
        is Msg.Thinking -> ThinkingCard(msg, thinkingExpandedDefault)
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

/** idx 之前最近的思考块**下标**（跨工具卡/结果/压缩条目扫描；遇用户/助手消息即止，无则 -1） */
private fun precedingThinkingIndex(messages: List<Msg>, idx: Int): Int {
    var i = idx - 1
    while (i >= 0) {
        when (messages[i]) {
            is Msg.Thinking -> return i
            is Msg.User, is Msg.Assistant -> return -1
            else -> i--
        }
    }
    return -1
}

// ───────────────────────────── 长按消息菜单（分支 + 复制 + 重新生成） ─────────────────────────────

/**
 * 长按消息气泡的上下文菜单（2026-09-02 分支功能设计 §4.1；2026-09-11 按 Operit 补两项）：
 * - 「从此处创建新会话」= fork（官方 RPC 原型）；
 * - 「复制消息」= 打开复制卡片（纯文本 / Markdown 源码分段，见 [MessageCopyCard]）；
 * - 「重新生成」= 重新请求该条**助手消息**（Operit `单条重新生成` 同款，仅 AI 消息显示）。
 * 锚定气泡：默认在气泡下方，近屏幕底部时翻转到上方；点外关闭无 scrim（外层处理）。
 */
@Composable
fun ForkContextMenu(
    anchor: Rect,
    forkEnabled: Boolean,
    isAssistant: Boolean,
    /** 重新生成仅最下方一条消息支持（2026-09-11 用户定）：非末条不显示该项 */
    showRegenerate: Boolean,
    regenerateEnabled: Boolean,
    onFork: () -> Unit,
    onCopy: () -> Unit,
    onQuote: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenW = configuration.screenWidthDp.dp
    val screenH = configuration.screenHeightDp.dp
    val menuW = 208.dp
    val rows = 2 + 1 + (if (showRegenerate) 1 else 0) // fork + 复制消息 + 引用 (+ 重新生成)
    val menuH = 44.dp * rows + 8.dp // 44dp/行 + 上下 4dp padding
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
                label = "复制消息",
                enabled = true,
                onClick = onCopy,
            )
            MenuRow(
                icon = Icons.Outlined.FormatQuote,
                label = "引用",
                enabled = true,
                onClick = onQuote,
            )
            if (showRegenerate) {
                MenuRow(
                    icon = Icons.Outlined.Refresh,
                    label = "重新生成",
                    enabled = regenerateEnabled,
                    onClick = onRegenerate,
                )
            }
        }
    }
}

/**
 * 引用块卡片（消息引用/追问，2026-09-11）：
 * 左侧 2dp 主色竖线 + 引用来源标签 + 引用原文（2 行省略）+ 可选右侧 × 取消。
 * 输入栏（待发送）与消息气泡（已发送）共用同一张卡——对齐 Hermes(@assistant-ui)
 * ComposerPrimitive.Quote/QuoteText/QuoteDismiss 与消息 Quote part 的同源形态。
 */
@Composable
fun QuoteCard(
    quote: Quote,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // ★ 高度必须由内容决定（IntrinsicSize.Min）：竖线用 fillMaxHeight 且父级高度无界时，
            //   会把它撑到父级最大高度（输入栏 dock 变全屏高、卡片跑到屏幕顶部 —— 2026-09-11 实测踩过）
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 6.dp),
    ) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 4.dp),
        ) {
            Text(
                if (quote.role == "assistant") "引用 AI 回答" else "引用用户消息",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                quote.text.trim().replace("\n", " "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Outlined.Close, "取消引用",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * 复制消息卡片（2026-09-11；参照 Operit `MessageCopyPreviewBottomSheet`）：
 * 标题「复制消息」+ 分段控制器（纯文本 / Markdown 源码）+ 内容区（可选中文本、可滚动）
 * + 右下「复制纯文本」/「复制 Markdown 源码」按键（文案随分段变化，Operit 同款）。
 * Pient 浮层家族：PientPanel + scrim 点外关闭、无右上 ×（与点外关闭重复的元素不加）。
 */
@Composable
fun MessageCopyCard(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val configuration = LocalConfiguration.current
    var mode by remember(text) { mutableIntStateOf(0) }
    // 纯文本态：按 Operit 一样用同一份 AST 转换（不放主线程——长回答逐字符转换可感）
    var plain by remember(text) { mutableStateOf<String?>(null) }
    LaunchedEffect(text) {
        plain = withContext(Dispatchers.Default) { markdownToPlainText(text) }
    }
    val display = if (mode == 0) plain.orEmpty() else text

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim)
                .clickable(onClick = onDismiss),
        )
        PientPanel(
            modifier = modifier
                .width((configuration.screenWidthDp - 48).dp)
                .heightIn(max = (configuration.screenHeightDp * 0.6f).dp)
                // 卡片自身吞掉点击，避免点卡片内容穿透到点外关闭层
                .clickable(
                    onClick = {},
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "复制消息",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                PientSegmented(
                    labels = listOf("纯文本", "Markdown 源码"),
                    selected = mode,
                    onSelect = { mode = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp),
                ) {
                    if (mode == 0 && plain == null) {
                        // 转换中（Operit 同款：先出转圈再出内容）
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    } else {
                        Text(
                            display,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        enabled = mode == 1 || plain != null,
                        onClick = {
                            clipboard.setText(AnnotatedString(display))
                            Toast.makeText(context, "消息已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Text(if (mode == 0) "复制纯文本" else "复制 Markdown 源码")
                    }
                }
            }
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
        // 引用块（2026-09-11）：气泡上方右对齐，宽与气泡同口径（85%）
        if (msg.quote != null) {
            BoxWithConstraints(Modifier.padding(bottom = 4.dp)) {
                QuoteCard(
                    quote = msg.quote,
                    modifier = Modifier.widthIn(max = maxWidth * 0.85f),
                )
            }
        }
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
private fun AssistantCard(
    msg: Msg.Assistant,
    thinking: Msg.Thinking? = null,
    thinkingExpandedDefault: Boolean = false,
) {
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
            ThinkingDisclosure(
                text = thinking.text,
                durationMs = thinking.durationMs,
                expandedDefault = thinkingExpandedDefault,
            )
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

// ───────────────────────────── 思考折叠块 ─────────────────────────────

/**
 * 思考折叠块（2026-09-12 按 **Hermes 桌面端** ThinkingDisclosure 重做）。
 * ★ 标题行不再带「思考程度档位」徽标（Pient 曾自加 `medium` 胶囊，2026-09-12 用户判为多余：
 *   档位是会话级设置，每条回答重复一遍没有信息量；档位仍可在模型选择器里看/改）。
 * 参考源：`hermes-agent/apps/desktop/src/components/assistant-ui/thread/message-parts.tsx`
 * （ThinkingDisclosure / ReasoningAccordionGroup）+ `components/chat/scaffold-row.tsx`。
 *
 * - 标题行文案（Hermes i18n `zh.ts` assistant.thread.* 逐字）：流式中「思考中」、
 *   完成后「思考了 3s」/ 不足 1s「思考了片刻」/ 无计时「已思考」（`formatElapsed`：<60s 为
 *   `3s`，≥60s 为 `1:20`）；
 * - 箭头在文字**右侧**（Hermes DisclosureRow：静息 alpha 0.4、展开 0.8），整行可点；
 * - 正文 = 思考 markdown（Hermes `text-xs leading-snug text-muted-foreground/85`
 *   → 12sp / 1.375 行高 / muted 85%，见 `MarkdownText(reasoning = true)`），无左边距（Hermes
 *   正文与标题行齐平）；
 * - 流式期间默认展开、正文限高 160dp（Hermes `max-h-40`）并**贴底跟随**增量；
 *   结束保持展开（Hermes live preview latch，判据见 ChatState.liveThinkingIndex）；
 * - 空思考不渲染（Hermes：无正文的思考组是纯噪音）。
 *
 * @param live 流式中：标题「思考中」+ 微光 + 右侧计时秒表、正文贴底
 * @param elapsedSeconds 流式已用秒数（计时只在上屏层跑，落库用 durationMs）
 * @param expandedDefault 展开初值（刚流式完 = true；历史载入 = false）
 */
@Composable
private fun ThinkingDisclosure(
    text: String,
    live: Boolean = false,
    elapsedSeconds: Int = 0,
    durationMs: Long? = null,
    expandedDefault: Boolean = false,
) {
    if (text.isBlank()) return
    // null = 用户没动过；此时按默认展开态（流式中/刚流式完 = 展开，历史 = 收起）
    var userOpen by remember { mutableStateOf<Boolean?>(null) }
    val open = userOpen ?: (live || expandedDefault)

    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { userOpen = !open }
                .padding(vertical = 4.dp),
        ) {
            ThinkingLabel(thoughtLabel(live, durationMs), live)
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (open) 0.8f else 0.4f),
                modifier = Modifier.padding(start = 4.dp).size(14.dp),
            )
            // 流式计时（Hermes：trailing 只在 pending 时出现；结束后时长已并入标题文案）
            if (live) {
                Text(
                    formatElapsedSeconds(elapsedSeconds.toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = MonoFont,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        if (open) {
            // 正文渲染：流式预览限高 160dp（Hermes `max-h-40`）并显示内容**尾部**
            //（Hermes 预览是「滚到底跟随」，移动端等价形态 = 只露尾部）。
            // ★ 绝不能用 verticalScroll：消息区在 LazyColumn 里、item 高度无界，
            //   嵌套垂直滚动会直接抛 IllegalStateException（infinity maximum height
            //   constraints；2026-09-12 实测崩溃一次）——限高 + 裁切即可，不引入滚动容器。
            if (live) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .clipToBounds()
                        .padding(bottom = 4.dp),
                ) {
                    MarkdownText(
                        text,
                        reasoning = true,
                        modifier = Modifier.wrapContentHeight(unbounded = true, align = Alignment.Bottom),
                    )
                }
            } else {
                MarkdownText(text, reasoning = true, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
            }
        }
    }
}

/** 思考标题文案（Hermes i18n 逐字：思考中 / 思考了 3s / 思考了片刻 / 已思考） */
private fun thoughtLabel(live: Boolean, durationMs: Long?): String = when {
    live -> "思考中"
    durationMs == null -> "已思考"
    durationMs < 1000L -> "思考了片刻"
    else -> "思考了 ${formatElapsedSeconds(durationMs / 1000)}"
}

/** Hermes `formatElapsed`：<60s → `3s`；≥60s → `1:20` */
private fun formatElapsedSeconds(seconds: Long): String =
    if (seconds < 60) "${seconds}s"
    else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/**
 * 思考标题文字（流式中带 Hermes `shimmer` 效果：一道高光从左向右扫过；静止态恒亮）。
 */
@Composable
private fun ThinkingLabel(label: String, live: Boolean) {
    val base = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelSmall
    if (!live) {
        Text(label, style = style, color = base)
        return
    }
    var width by remember { mutableStateOf(120f) }
    val transition = rememberInfiniteTransition(label = "thinkingShimmer")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "thinkingSweep",
    )
    Text(
        label,
        style = style.copy(
            // 高光带宽度 = 文字宽（扫过即整行亮一次）
            brush = Brush.linearGradient(
                colors = listOf(base.copy(alpha = 0.55f), base, base.copy(alpha = 0.55f)),
                start = Offset(sweep * 2f * width - width, 0f),
                end = Offset(sweep * 2f * width, 0f),
            ),
            alpha = 1f,
        ),
        modifier = Modifier.onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) },
    )
}

/**
 * 独立思考卡（其后没有助手回答时的兜底：进行中/被中止的交换）：
 * 保留 Pient 的卡片外框（surfaceContainerLow 0.6 + 1dp 描边、6dp 圆角），
 * 内部仍是同一份 [ThinkingDisclosure]，不再各写一套标题/箭头。
 */
@Composable
private fun ThinkingCard(msg: Msg.Thinking, expandedDefault: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp),
    ) {
        ThinkingDisclosure(
            text = msg.text,
            durationMs = msg.durationMs,
            expandedDefault = expandedDefault,
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

/**
 * 流式回复卡（2026-09-09 实现；2026-09-12 加思考预览）：
 * 思考先行（思考模式开启）——「思考中」+ 计时 + 实时正文贴底，随后才是逐片到达的回答正文与光标。
 */
@Composable
private fun StreamingCard(
    draft: String,
    thinking: String = "",
    thinkingStartedAt: Long = 0L,
) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursorAlpha",
    )
    // 流式秒表：思考起点已知就按它计时（Hermes ActivityTimerText 口径，1s 一跳）
    var elapsed by remember { mutableStateOf(0) }
    LaunchedEffect(thinkingStartedAt) {
        while (true) {
            elapsed = if (thinkingStartedAt > 0L) {
                ((System.currentTimeMillis() - thinkingStartedAt) / 1000).toInt()
            } else 0
            delay(500)
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        if (thinking.isNotBlank()) {
            ThinkingDisclosure(
                text = thinking,
                live = true,
                elapsedSeconds = elapsed,
            )
        }
        if (draft.isNotEmpty()) MarkdownText(draft)
        Text(
            "▍",
            color = MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
