package com.pient.app.ui.chat

import com.pient.app.data.i18n.L
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import com.pient.app.data.UsageStore
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import android.util.Log
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.pient.app.data.Attachment
import com.pient.app.data.ContextPolicy
import com.pient.app.data.Msg
import com.pient.app.data.Quote
import com.pient.app.data.SettingsStore
import com.pient.app.data.ToolStatus
import com.pient.app.data.markdownToPlainText
import com.pient.app.ui.components.MarkdownText
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
    onMessageLongPress: ((MessageMenuTarget) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()

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
    /** 各消息条目的根坐标**原点**（positionInRoot）：长按触点 = 它 + 按下点（2026-09-17） */
    val bubblePos = remember { HashMap<Int, Offset>() }
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

    // 渲染项（2026-09-14 Hermes 对齐的工具行）：连续 ≥2 个「活动型」工具调用折成一行摘要，
    // 文件编辑（write/edit）作为交付物单列；成对工具结果并入工具行、不再单渲染。
    //
    // ★ 这里**不能包 remember(messages, …)**：messages 是同一个 SnapshotStateList 实例，
    //   流式期间 appendEntry 只是原地追加 → remember 的键不变、渲染项永远是旧的
    //   （实测症状：RUNNING 的工具行一直不出现，直到回合结束 isStreaming 翻转才蹦出来）。
    //   直接调用：函数体读列表 → 订阅列表变化 → 追加即重算（窗口内条目数有上限，够快）。
    val renderItems = buildChatRenderItems(messages, startIndex, isStreaming)

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
            verticalArrangement = Arrangement.spacedBy(0.dp),
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
                            L.chat.showEarlier,
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
            items(renderItems.size, key = { renderItems[it].key }) { i ->
                val item = renderItems[i]
                // 工具运行：一行灰色摘要（运行中 shimmer + 单行 ticker；点开铺开各行）
                if (item is ChatRender.Run) {
                    Box(Modifier.padding(top = renderGap(renderItems, messages, i))) {
                        ToolRunGroup(
                            calls = item.indices.map { messages[it] as Msg.ToolCall },
                            results = item.indices.map { idx -> messages.getOrNull(idx + 1) as? Msg.ToolResult },
                            live = item.live,
                        )
                    }
                    return@items
                }
                val idx = item.key
                val msg = messages[idx]
                // 每条条目**就地渲染**（2026-09-16 按「节点详情卡」同款口径改写）：思考有自己的条目与
                // 位置，不再并进紧随其后的回答卡 —— 并入会把思考摆到工具行**下方**（落库顺序里思考在
                // 工具之后），而真实发生顺序是 思考 → 工具 → … → 回答，观感上就是流程错位。
                // 与画布「节点详情」（TreeCanvasPanel）逐条同序：两处不再各排一套。
                // 长按 fork 入口：仅 User/Assistant 气泡响应（2026-09-02 分支功能设计 §4.1）
                val longPressable = msg is Msg.User || msg is Msg.Assistant
                Box(
                    Modifier
                        .padding(top = renderGap(renderItems, messages, i))
                        .onGloballyPositioned {
                            bubbleBounds[idx] = it.boundsInRoot()
                            bubblePos[idx] = it.positionInRoot()
                        }
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
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        try {
                                            withTimeout(viewConfig.longPressTimeoutMillis) {
                                                waitForUpOrCancellation()
                                            }
                                        } catch (_: PointerEventTimeoutCancellationException) {
                                            onMessageLongPress?.invoke(
                                                MessageMenuTarget(
                                                    index = idx,
                                                    anchor = bubbleBounds[idx] ?: Rect.Zero,
                                                    // 触点（根坐标）= 条目根原点 + 按下点（按下点相对条目局部）
                                                    press = (bubblePos[idx] ?: Offset.Zero) + down.position,
                                                ),
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
                        // 展开初值：刚流式完的思考块（本运行内的 live preview）保持展开，历史载入的收起
                        thinkingExpandedDefault = msg is Msg.Thinking && idx == liveThinkingIndex,
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
                    Icons.Outlined.ExpandMore, L.chat.backToBottom,
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
                        L.chat.messageLocator,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        L.chat.locatorPosition(currentIndex + 1, messages.size),
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
                                            L.chat.searchMessages,
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
                                    Icons.Outlined.Close, L.common.clearSearch,
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
                                Icons.Outlined.FilterList, L.chat.filterMessages,
                                tint = if (locatorFilter != 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = filterMenuOpen,
                            onDismissRequest = { filterMenuOpen = false },
                        ) {
                            listOf(L.chat.filterAll, L.chat.filterUser, L.chat.filterAi).forEachIndexed { i, label ->
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
                    L.chat.locatorHint,
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
                            L.chat.noMatchingMessages,
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
                                            if (msg is Msg.User) L.chat.roleUser else "AI",
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
                    L.common.cancel,
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
    thinkingExpandedDefault: Boolean = false,
) {
    when (msg) {
        is Msg.User -> UserBubble(msg)
        is Msg.Assistant -> AssistantCard(msg)
        is Msg.Thinking -> ThinkingCard(msg, thinkingExpandedDefault)
        is Msg.ToolCall -> ToolRow(msg, toolResult)
        // 未成对的结果（理论上不该出现）：合成一行同款工具行，不再另设结果卡
        is Msg.ToolResult -> ToolRow(
            call = Msg.ToolCall(msg.toolName, "", ToolStatus.DONE, detail = msg.full ?: msg.preview),
            result = msg,
        )
        is Msg.Compaction -> CompactionCard(msg)
    }
}

// ───────────────────────────── 思考并入回答块的判定 ─────────────────────────────

// ───────────────────────────── 长按消息菜单（分支 + 复制 + 引用） ─────────────────────────────

/**
 * 长按菜单的目标（2026-09-17）：上屏下标 + 气泡根坐标 rect + **触点**的根坐标。
 * 定位以触点为基准（旧实现只传 rect = 整条消息的 bounds，长回答能有好几屏高 → 菜单会飞到最上方）。
 */
data class MessageMenuTarget(
    val index: Int,
    val anchor: Rect,
    val press: Offset,
)

/** 菜单排版常量（MenuRow 固定 44dp/行，见 [MenuRow]） */
private val MenuWidth = 208.dp
private val MenuRowHeight = 44.dp
private val MenuVertPadding = 8.dp
private val MenuEdgeMargin = 8.dp
private val MenuGap = 8.dp

/**
 * 长按菜单的落点（2026-09-17 重做：**锚定触点**）。
 *
 * 实战口径（四处同一条思路）：
 * - Android 平台 `PopupMenu` / `MenuPopupHelper`：有空间就放在锚点下方，否则翻到上方；两边都放不下
 *   就给「可用高度上限 + 滚动」（`getMaxAvailableHeight`），不会贴到屏幕边缘；
 * - Material 3 `DropdownMenu`：锚点在屏幕**下半部分**时优先上翻（top = anchor.top − menuH），
 *   下方优先放在 anchor.bottom 处，两者都夹在垂直边距内；
 * - Flutter `showMenu` / `_PopupMenuRouteLayout`：菜单永远贴着调用方传入的**触点**摆，再夹进
 *   8dp 屏幕内边距（`_kMenuScreenPadding`），装不下就限高成可滚动；
 * - iOS 上下文菜单：贴着触点出现（配合源视图预览），不飞到屏幕边缘。
 * ⇒ 共同点 = 锚定**手指按下的那一点**；仅当气泡整条都在可视区内时，才用气泡边缘（经典「贴着气泡弹」观感）。
 */
private fun menuOffset(
    press: Offset,
    anchor: Rect,
    container: Size,
    menu: Size,
    margin: Float,
    gap: Float,
): Offset {
    // 横向：看**触点在哪半边**（条目 rect 是全宽的，用它的中心判会永远落回左对齐）——
    // 按在右半边（用户气泡）→ 菜单右缘贴条目右缘；按在左半边（AI 气泡）→ 左缘贴左缘（M3 同款：先对齐起点，再对齐终点）
    val xRaw = if (press.x > container.width / 2f) anchor.right - menu.width else anchor.left
    val x = xRaw.coerceIn(margin, (container.width - menu.width - margin).coerceAtLeast(margin))
    // 纵向参考边：气泡**整条都在可视区内**（短消息）就用它的边缘 —— 这才是「贴着气泡弹」的经典观感；
    // 长消息（被滚动裁掉一头）一律改用触点：否则「放气泡上方」会把菜单顶到离手指好几百像素之外
    // （实测 1746px 高的回答：press.y=1500 会算到 y=36 —— 又是一个「太上方」）
    val itemVisible = anchor.top >= margin && anchor.bottom <= container.height - margin
    val belowRef = if (itemVisible) anchor.bottom else press.y
    val aboveRef = if (itemVisible) anchor.top else press.y
    val maxY = (container.height - menu.height - margin).coerceAtLeast(margin)
    val y = when {
        belowRef + gap + menu.height <= container.height - margin -> belowRef + gap
        aboveRef - gap - menu.height >= margin -> aboveRef - gap - menu.height
        // 两边都放不下（长消息 + 小窗口）：以触点为中心夹进容器 —— 宁可遮住一段气泡，也不要飞到远处的屏幕边
        else -> press.y - menu.height / 2f
    }.coerceIn(margin, maxY)
    return Offset(x, y)
}

/**
 * 长按消息的上下文菜单（fork / 复制 / 引用；2026-09-17 起移除「重新生成」——重做走会话内分支）。
 *
 * 定位：见 [menuOffset] —— 锚定长按**触点**，容器 = 本菜单挂载的那层（聊天页根 Box，键盘弹起时它自己会缩）；
 * 点外关闭（无 scrim，外层处理）。
 */
@Composable
fun ForkContextMenu(
    anchor: Rect,
    press: Offset,
    forkEnabled: Boolean,
    isAssistant: Boolean,
    onFork: () -> Unit,
    onCopy: () -> Unit,
    onQuote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val rows = 3 // fork + 复制消息 + 引用
    val menuW = MenuWidth
    val menuH = MenuRowHeight * rows + MenuVertPadding
    // 容器 = 挂载层的真实约束（同一帧就能拿到，不需要等布局回调）：不用 screenHeightDp 是因为
    // 量的是整屏 —— 键盘弹起 / 顶栏占位时菜单会算错位置
    BoxWithConstraints(modifier) {
        val container = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val menuSize = with(density) { Size(menuW.toPx(), menuH.toPx()) }
        val place = menuOffset(
            press = press,
            anchor = anchor,
            container = container,
            menu = menuSize,
            margin = with(density) { MenuEdgeMargin.toPx() },
            gap = with(density) { MenuGap.toPx() },
        )
        // 落点打点（几何类 UI 的取证口径：一次长按 = 一行数字，别靠看图猜）
        LaunchedEffect(press, anchor, container) {
            Log.i("PientChat", "长按菜单落点：press=${press.x.toInt()},${press.y.toInt()} " +
                "anchor=${anchor.left.toInt()},${anchor.top.toInt()},${anchor.right.toInt()},${anchor.bottom.toInt()} " +
                "container=${container.width.toInt()}x${container.height.toInt()} menu=${menuSize.width.toInt()}x${menuSize.height.toInt()} " +
                "→ (${place.x.toInt()},${place.y.toInt()})")
        }
        PientPanel(
            modifier = Modifier
                .offset(
                    x = with(density) { place.x.toDp() },
                    y = with(density) { place.y.toDp() },
                )
                .width(menuW),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                MenuRow(
                    icon = Icons.Outlined.ForkRight,
                    label = L.chat.forkFromHere,
                    enabled = forkEnabled,
                    onClick = onFork,
                )
                MenuRow(
                    icon = Icons.Outlined.ContentCopy,
                    label = L.chat.copyMessage,
                    enabled = true,
                    onClick = onCopy,
                )
                MenuRow(
                    icon = Icons.Outlined.FormatQuote,
                    label = L.chat.quote,
                    enabled = true,
                    onClick = onQuote,
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
                if (quote.role == "assistant") L.chat.quoteAssistant else L.chat.quoteUser,
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
                    Icons.Outlined.Close, L.chat.removeQuote,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * 复制消息卡片（2026-09-11；参照 Operit `MessageCopyPreviewBottomSheet`）：
 * 标题「复制消息」+ 分段控制器（纯文本 / Markdown 源码 / XML）+ 内容区（可选中文本、可滚动）
 * + 右下按键（文案随分段变化：复制纯文本 / 复制 Markdown 源码 / 复制 XML，Operit 同款）。
 * 分段 0/1 = 这一条消息本身（正文）；分段 2 = 该回合 AI 侧全量（思考过程 + 工具调用过程 + 正文，
 * 取数见 `data/MessageXml.kt` 的 [com.pient.app.data.turnXml]，2026-09-17 用户加的）。
 * Pient 浮层家族：PientPanel + scrim 点外关闭、无右上 ×（与点外关闭重复的元素不加）。
 */
@Composable
fun MessageCopyCard(
    text: String,
    /** 分段 2 的内容：目标消息所属回合的 AI 侧全量（长按用户消息时是它自己的 `<user>`） */
    xml: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val configuration = LocalConfiguration.current
    var mode by remember(text, xml) { mutableIntStateOf(0) }
    // 纯文本态：按 Operit 一样用同一份 AST 转换（不放主线程——长回答逐字符转换可感）
    var plain by remember(text) { mutableStateOf<String?>(null) }
    LaunchedEffect(text) {
        plain = withContext(Dispatchers.Default) { markdownToPlainText(text) }
    }
    val display = when (mode) {
        0 -> plain.orEmpty()
        1 -> text
        else -> xml
    }

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
                    L.chat.copyMessage,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                PientSegmented(
                    labels = listOf(L.chat.plainText, L.chat.markdownSource, L.chat.xml),
                    selected = mode,
                    onSelect = { mode = it },
                    // 目标下标取不到内容时（理论上的失效态）XML 分段不可点，避免复制到空串
                    enabled = listOf(true, true, xml.isNotBlank()),
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
                        enabled = mode != 0 || plain != null,
                        onClick = {
                            clipboard.setText(AnnotatedString(display))
                            Toast.makeText(context, L.chat.messageCopied, Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Text(
                            when (mode) {
                                0 -> L.chat.copyPlainText
                                1 -> L.chat.copyMarkdownSource
                                else -> L.chat.copyXml
                            },
                        )
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
        MarkdownText(msg.markdown, modifier = Modifier.padding(top = 2.dp))
        if (msg.usage != null) {
            // 费用口径与用量页一致（2026-09-16 修）：走 UsageStore.cnyCostOf（服务商回传的费用优先，
            // 否则按内置价格表折算成 ¥）。原来直接显示 usage.costUsd —— DeepSeek 这类不返回费用
            // 的服务商每条都显示 `$0.0`，而且和用量页的 ¥ 币种也对不上。
            val modelName = msg.model.orEmpty()
            val costCny = UsageStore.cnyCostOf(UsageStore.providerOf(modelName), modelName, msg.usage)
            Text(
                "in ${tok(msg.usage.inTokens)} · out ${tok(msg.usage.outTokens)} · " +
                    "cache ${tok(msg.usage.cacheTokens)} · ¥${money(costCny)}",
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
 *
 * 可见性 = `internal`：节点详情卡（画布 FAB1）也用它，规格同源（《分支功能设计》§3.6）。
 */
@Composable
internal fun ThinkingDisclosure(
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

    Column(Modifier.fillMaxWidth().alpha(if (open) 1f else ScaffoldFade)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { userOpen = !open },
        ) {
            ThinkingLabel(thoughtLabel(live, durationMs), live)
            ScaffoldCaret(open = open)
            // 流式计时（Hermes ActivityTimerText：0.56rem / tracking .02em / midground-55；
            // 只在 pending 时出现，结束后时长已并入标题文案）
            if (live) {
                Text(
                    formatElapsedSeconds(elapsedSeconds.toLong()),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        letterSpacing = 0.18.sp,
                    ),
                    color = scaffoldMetaColor(),
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
    live -> L.chat.thinkingLive
    durationMs == null -> L.chat.thought
    durationMs < 1000L -> L.chat.thoughtBriefly
    else -> L.chat.thoughtFor(formatElapsedSeconds(durationMs / 1000))
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
    // Hermes SCAFFOLD_LABEL_CLASS：11px / 18px 行高 / 前景 64%（与工具行、run 摘要同一支灰）
    val style = scaffoldLabelStyle()
    val base = scaffoldLabelColor()
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
 * 独立思考块（其后没有助手回答时的兜底：进行中/被中止的交换；与回答之间隔着工具卡时也走这里）：
 * 内部统一是同一份 [ThinkingDisclosure]，不再各写一套标题/箭头。
 * [boxed] = true 保留 Pient 的卡片外框（surfaceContainerLow 0.6 + 1dp 描边、6dp 圆角）；
 * false = 无外框形态，与「思考并入回答卡」以及流式期间（[StreamingCard] 内的同一份
 * ThinkingDisclosure）视觉连续——同一块思考不该因为中间插了工具卡就换一副壳。
 */
@Composable
private fun ThinkingCard(msg: Msg.Thinking, expandedDefault: Boolean = false) {
    // 统一无外框（2026-09-16）：与流式期间的思考预览、以及画布「节点详情」里的思考同一形态
    Column(Modifier.fillMaxWidth()) {
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
 * 金额显示（消息卡用量行；2026-09-16）：≥1 元两位小数、≥0.01 三位、更小四位 ——
 * 一条消息通常只有几厘钱，固定两位会全显示成 ¥0.00，看不出差别。
 */
private fun money(v: Double): String = when {
    v <= 0.0 -> "0"
    v >= 1.0 -> String.format(java.util.Locale.US, "%.2f", v)
    v >= 0.01 -> String.format(java.util.Locale.US, "%.3f", v)
    else -> String.format(java.util.Locale.US, "%.4f", v)
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
                L.chat.contextCompacted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 6.dp),
            )
            // 压缩原因来自 pi 的 compaction_start/end（manual / threshold / overflow）——
            // 移动端看不到 pi 的 footer 提示，这行就是「这次为什么压」的答案
            if (msg.reason != null) {
                Text(
                    ContextPolicy.compactReasonLabel(msg.reason),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(
                L.chat.compactTokens(tok(msg.tokensBefore), tok(msg.saved)),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = MonoFont,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                L.chat.viewSummary,
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

// ───────────────────────────── 渲染项（Hermes 工具运行分组） ─────────────────────────────

/** 渲染项：单条消息，或一次「工具运行」（≥2 个连续活动型工具调用折成一行摘要）。 */
private sealed interface ChatRender {
    /** 列表 key（同批消息里唯一、滚动时稳定）= 该组第一条消息的下标 */
    val key: Int

    data class One(override val key: Int) : ChatRender
    data class Run(override val key: Int, val indices: List<Int>, val live: Boolean) : ChatRender
}

/**
 * 把消息切成渲染项（Hermes `ToolGroupSlot` + `splitRunItems` 的口径）：
 * - 成对工具结果（ToolCall 紧跟 ToolResult）并入工具行，不单列；
 * - 「活动型」工具调用（read/grep/find/ls/bash/其他）连续 ≥2 个 → 一次 Run（一行摘要）；
 * - 文件编辑/写入（[isCardTool]）是交付物，打断 run、各自成行（Hermes 同款切分）；
 * - 运行中的判定 = 正在流式 **且该 run 一直延伸到列表末尾**（Hermes 的尾部约束：
 *   回合结束或后面又来了别的条目 → 视为已结束、可折叠）。
 */
private fun buildChatRenderItems(
    messages: List<Msg>,
    startIndex: Int,
    isStreaming: Boolean,
): List<ChatRender> {
    val out = ArrayList<ChatRender>()
    var i = startIndex
    while (i < messages.size) {
        val msg = messages[i]
        // 成对结果：已并进前一条工具行
        if (msg is Msg.ToolResult && i > 0 && messages[i - 1] is Msg.ToolCall) {
            i++
            continue
        }
        if (msg is Msg.ToolCall && isActivityTool(msg.name)) {
            val indices = ArrayList<Int>()
            var j = i
            while (j < messages.size) {
                val m = messages[j]
                if (m is Msg.ToolCall && isActivityTool(m.name)) {
                    indices.add(j)
                    j += if (messages.getOrNull(j + 1) is Msg.ToolResult) 2 else 1
                } else {
                    break
                }
            }
            if (indices.size >= 2) {
                val last = indices.last()
                val end = if (messages.getOrNull(last + 1) is Msg.ToolResult) last + 1 else last
                out.add(ChatRender.Run(i, indices, isStreaming && end >= messages.size - 1))
                i = last + 1
                continue
            }
        }
        out.add(ChatRender.One(i))
        i++
    }
    return out
}

// ───────────────────────────── 会话块节奏（Hermes styles.css） ─────────────────────────────

/**
 * 会话块节奏（数值 = Hermes `styles.css`）：
 * `--conversation-turn-gap` 6px（消息之间 / 用户消息与回复之间）、
 * `--turn-block-gap` 12px（同一条回复内的块之间）、
 * `--scaffold-block-gap` = turn/3 = 4px（脚手架彼此相邻，例如工具行/思考标题行背靠背）、
 * `--paragraph-gap` 11.2px（正文↔正文：同一阅读栏的分段）。
 */
private enum class BlockKind { HUMAN, SCAFFOLD, PROSE }

private fun blockKindOf(msg: Msg): BlockKind = when (msg) {
    is Msg.User -> BlockKind.HUMAN
    is Msg.Assistant -> BlockKind.PROSE
    // 工具行/run 摘要/思考标题/压缩条：Hermes 里都是「脚手架」
    is Msg.ToolCall, is Msg.ToolResult, is Msg.Thinking, is Msg.Compaction -> BlockKind.SCAFFOLD
}

private fun blockKindOf(item: ChatRender, messages: List<Msg>): BlockKind = when (item) {
    is ChatRender.Run -> BlockKind.SCAFFOLD
    is ChatRender.One -> blockKindOf(messages[item.key])
}

/** 该项与上一项之间应有的上边距（Hermes 的 adjacency 规则搬到一维列表上）。 */
private fun renderGap(items: List<ChatRender>, messages: List<Msg>, i: Int): Dp {
    if (i <= 0) return 0.dp
    val prev = blockKindOf(items[i - 1], messages)
    val cur = blockKindOf(items[i], messages)
    return when {
        prev == BlockKind.HUMAN || cur == BlockKind.HUMAN -> 6.dp
        prev == BlockKind.SCAFFOLD && cur == BlockKind.SCAFFOLD -> 4.dp
        prev == BlockKind.PROSE && cur == BlockKind.PROSE -> 11.dp
        else -> 12.dp
    }
}

/** 消息定位预览文案（换行折叠为空格，超长由列表行 Ellipsis 截断） */
private fun locatorPreview(msg: Msg): String = when (msg) {
    is Msg.User -> msg.text.replace('\n', ' ')
    is Msg.Assistant -> msg.markdown.replace('\n', ' ')
    is Msg.Thinking -> L.chat.previewThinking(msg.level)
    is Msg.ToolCall -> L.chat.previewTool(msg.name)
    is Msg.ToolResult -> L.chat.previewResult(msg.preview.replace('\n', ' '))
    is Msg.Compaction -> L.chat.previewCompacted(msg.saved)
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
