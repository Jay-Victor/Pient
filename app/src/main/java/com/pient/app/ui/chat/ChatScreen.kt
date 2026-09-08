package com.pient.app.ui.chat

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import android.widget.Toast
import com.pient.app.data.Attachment
import com.pient.app.data.AttachmentKind
import com.pient.app.data.ChatState
import com.pient.app.data.DrawerMode
import com.pient.app.data.Panel
import com.pient.app.data.SettingsStore
import com.pient.app.ui.components.StatusBadge
import com.pient.app.ui.files.FilesPanel
import com.pient.app.ui.terminal.TerminalPanel
import com.pient.app.ui.theme.PientPanel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 聊天主页（P1）：顶栏常驻，下方区域在 消息区 / 文件内容预览区（P3）/ 终端页（P4）
 * 之间切换承载；侧栏抽屉与文件树均为同窗口浮层（玻璃真模糊要求）。
 */
@Composable
fun ChatScreen(chatState: ChatState, nav: NavController) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current
    val screenHpx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    var modelSheetOpen by remember { mutableStateOf(false) }
    var attachSheetOpen by remember { mutableStateOf(false) }
    var contextCardOpen by remember { mutableStateOf(false) }
    var systemPromptOpen by remember { mutableStateOf(false) }
    var urlDialogOpen by remember { mutableStateOf(false) }
    // 长按消息 → fork 上下文菜单（2026-09-02 分支功能设计 §4）：目标消息下标 + 气泡根坐标
    var forkMenuTarget by remember { mutableStateOf<Pair<Int, Rect>?>(null) }
    // 输入框文本与 @ 引用状态（提升到 ChatScreen：引用卡为悬浮浮层）。
    // TextFieldValue 承载光标位置：@ 选择文件后光标需落在 "@路径 " 末尾，
    // 且退格一键删除整段引用（String 状态无法控制光标）。
    var inputText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var mentionOpen by remember { mutableStateOf(false) }
    // @ 引用文件来源 = 当前项目真实文件树（未绑定项目/未加载时为空列表）
    val mentionFiles = remember(chatState.fileTreeRoot) {
        chatState.fileTreeRoot?.let { buildMentionFiles(it) } ?: emptyList()
    }
    // 文件树长按菜单「@ 提及插入输入框」：插入文本并展开引用卡
    LaunchedEffect(chatState.mentionInsertRequest) {
        chatState.mentionInsertRequest?.let { name ->
            inputText = TextFieldValue(inputText.text + "@$name ")
            mentionOpen = true
            chatState.mentionInsertRequest = null
        }
    }
    // 模型按键上缘 y（root px）：弹窗底部锚定到按键上缘（按键随输入框行数/IME 移动）
    var chipTopY by remember { mutableStateOf(0f) }
    val modelSheetBottomOffset =
        if (chipTopY > 0f) with(density) { (screenHpx - chipTopY).toDp() } else 8.dp
    // dock 上缘 y（root px）：@ 引用卡底部锚定到 dock 上缘上方 8dp（不覆盖输入栏）
    var dockTopY by remember { mutableStateOf(0f) }
    val mentionBottomOffset =
        if (dockTopY > 0f) with(density) { (screenHpx - dockTopY).toDp() + 8.dp } else 8.dp

    // 文件树随当前项目加载（@ 引用文件源；文件树面板打开时也会刷新）
    LaunchedEffect(chatState.currentProject) {
        chatState.refreshFileTree(context)
    }

    // ── 二次退出（Operit MainActivity.setupBackPressHandler 同款：2000ms 内再按返回即退出）──
    // 仅当聊天主页无任何浮层/抽屉打开时拦截；浮层打开时返回键维持原默认行为。
    val overlaysClosed = !modelSheetOpen && !attachSheetOpen && !contextCardOpen &&
        !systemPromptOpen && !urlDialogOpen && forkMenuTarget == null && !mentionOpen &&
        !chatState.drawerOpen
    var lastBackPress by remember { mutableStateOf(0L) }
    BackHandler(enabled = overlaysClosed) {
        val now = System.currentTimeMillis()
        if (now - lastBackPress > 2000L) {
            lastBackPress = now
            Toast.makeText(context, "再按一次退出应用", Toast.LENGTH_SHORT).show()
        } else {
            (context as? Activity)?.finish()
        }
    }

    // ── 抽屉手势（Operit PhoneLayout 同款实现）：
    //   根 Box 挂全局 drag 检测——全屏任意位置水平右滑打开（水平位移主导：
    //   |dx| > |dy| 才触发，不与消息列表滚动冲突）、左滑关闭；阈值 40px。
    //   子级手势（列表滚动/文本选择/输入框拖动）先消费事件，天然互不抢占。
    val drawerOpenNow by rememberUpdatedState(chatState.drawerOpen)
    var dragDx by remember { mutableStateOf(0f) }
    var dragDy by remember { mutableStateOf(0f) }

    // ── 抽屉展出方式（行为设置）：SLIDE = 水平滑出（默认）／ PERSPECTIVE ──
    // 手机 = 3D 透视（Operit PhoneLayout 同款，仅开关开启时）；
    // 平板 = 聊天页宽度压缩 + 侧边栏滑出（Operit TabletLayout 同款 width+offset 结构），
    // 为平板默认行为，与 3D 透视开关无关（2026-08-30 用户决策）。
    // 平板判定：screenWidthDp >= 600（OperitApp 同款）。
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val drawerMode = SettingsStore.drawerMode
    val use3D = drawerMode == DrawerMode.PERSPECTIVE && !isTablet
    val useCompress = isTablet
    val drawerWidth = 296.dp

    // 抽屉动画进度（Operit PhoneLayout：开 LowBouncy / 关 NoBouncy，stiffness 1000；
    // 平板压缩模式对齐 Operit TabletLayout 的 tween 280ms 宽度动画）
    val progress by animateFloatAsState(
        targetValue = if (chatState.drawerOpen) 1f else 0f,
        animationSpec = if (use3D) {
            spring(
                dampingRatio = if (chatState.drawerOpen) Spring.DampingRatioLowBouncy else Spring.DampingRatioNoBouncy,
                stiffness = 1000f,
            )
        } else {
            tween(durationMillis = 280)
        },
        label = "drawerProgress",
    )

    // ── Operit PhoneLayout 数值逐项对齐（enableNavigationAnimation 分支）──
    // 主内容：平移 82% 宽 + 下移 12dp + 缩放 0.92 + Y 轴 -7° + 圆角 24dp + 阴影 18dp；
    // 抽屉：-宽→0 滑入 + 缩放 0.92→1 + 透明度 0.72→1；scrim 透明（Operit 同款）。
    val contentTranslationX = when {
        use3D -> drawerWidth * (0.82f * progress)
        useCompress -> drawerWidth * progress
        else -> 0.dp
    }
    val contentTranslationY = if (use3D) 12.dp * progress else 0.dp
    val contentScale = if (use3D) 1f - (0.08f * progress) else 1f
    val contentRotationY = if (use3D) -7f * progress else 0f
    val contentCornerRadius = if (use3D) 24.dp * progress else 0.dp
    val contentShadowElevation = if (use3D) 18.dp * progress else 0.dp
    val drawerOffset = -drawerWidth * (1f - progress)
    val drawerScale = if (use3D) 0.92f + (0.08f * progress) else 1f
    val drawerAlpha = if (use3D) 0.72f + (0.28f * progress) else 1f

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragDx = 0f
                        dragDy = 0f
                    },
                    onDragEnd = {
                        dragDx = 0f
                        dragDy = 0f
                    },
                    onDragCancel = {
                        dragDx = 0f
                        dragDy = 0f
                    },
                ) { change, _ ->
                    change.consume()
                    val delta = change.position - change.previousPosition
                    dragDx += delta.x
                    dragDy += delta.y
                    if (abs(dragDx) > abs(dragDy)) {
                        if (!drawerOpenNow && dragDx > 40f) {
                            chatState.drawerOpen = true
                            dragDx = 0f
                            dragDy = 0f
                        } else if (drawerOpenNow && dragDx < -40f) {
                            chatState.drawerOpen = false
                            dragDx = 0f
                            dragDy = 0f
                        }
                    }
                }
            },
    ) {
        // ── 主内容（zIndex 1）：3D 模式 graphicsLayer 整体变换（Operit Surface 同款），
        //    平板压缩模式 = 宽度收缩 + 右移（Operit TabletLayout 同款 layout 层方案）──
        Box(
            Modifier
                .zIndex(1f)
                .then(
                    when {
                        use3D -> Modifier.graphicsLayer {
                            translationX = contentTranslationX.toPx()
                            translationY = contentTranslationY.toPx()
                            scaleX = contentScale
                            scaleY = contentScale
                            rotationY = contentRotationY
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            clip = true
                            shape = RoundedCornerShape(contentCornerRadius)
                            shadowElevation = contentShadowElevation.toPx()
                        }
                        useCompress -> Modifier
                            .width(configuration.screenWidthDp.dp - drawerWidth * progress)
                            .offset(x = drawerWidth * progress)
                        else -> Modifier
                    },
                ),
        ) {
        Column(Modifier.fillMaxSize()) {
            ChatTopBar(
                chatState = chatState,
                onMenu = { chatState.drawerOpen = !chatState.drawerOpen },
                onPanel = {
                    chatState.togglePanel(it)
                    attachSheetOpen = false // 切页时收起附件卡片
                },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (chatState.activePanel) {
                    Panel.MESSAGES -> MessagesPanel(
                        chatState = chatState,
                        scope = scope,
                        onMessageLongPress = { idx, rect -> forkMenuTarget = idx to rect },
                        onConfigureAi = {
                            attachSheetOpen = false
                            nav.navigate("model_config")
                        },
                    )
                    Panel.FILES -> FilesPanel(chatState)
                    Panel.TERMINAL -> TerminalPanel(chatState, nav)
                    Panel.TREE -> TreeCanvasPanel(chatState)
                }
            }
            // 输入栏 dock 仅在消息区显示（文件/终端页有各自交互区）；
            // 项目与 AI 配置齐备前不显示（2026-09-08：聊天页引导清单接管）
            if (chatState.activePanel == Panel.MESSAGES &&
                chatState.currentProject != null && chatState.aiConfigured
            ) {
                ChatInputBar(
                    chatState = chatState,
                    text = inputText,
                    onTextChange = {
                        // 一键删除整段 @ 引用：单字符退格 + 光标停在 token 末尾
                        // → 整个 "@路径 " 一起删掉（Operit normalizeMentionDeletion 同款）
                        inputText = normalizeMentionDeletion(inputText, it, mentionFiles)
                        mentionOpen = inputText.text.endsWith("@")
                    },
                    mentionFiles = mentionFiles,
                    onOpenModelSelector = { modelSheetOpen = true },
                    onOpenAttach = { attachSheetOpen = !attachSheetOpen },
                    onToggleContextCard = { contextCardOpen = !contextCardOpen },
                    onToggleSystemPrompt = { systemPromptOpen = !systemPromptOpen },
                    onSend = { text ->
                        // 首条消息自动建会话（2026-09-08：无 mock 会话后，发送即建当前项目首会话）
                        if (chatState.currentSessionId == null) chatState.newSession()
                        chatState.streamJob = scope.launch { chatState.streamReply(text) }
                    },
                    onAbort = { chatState.abort() },
                    modelSelectorOpen = modelSheetOpen,
                    onChipPositioned = { chipTopY = it },
                    onDockTopPositioned = { dockTopY = it },
                )
            }
        }
        }

        // ── 侧栏抽屉（zIndex 2）──
        if (use3D || useCompress) {
            // 3D 透视 / 平板压缩：progress 驱动抽屉滑入（Operit PhoneLayout 同款）。
            // 完全关闭时移出组合（不占命中区域）；3D 模式点外关闭层为透明 ——
            // Operit 同款：scrim 透明，3D 变换本身传达模态。
            // 平板压缩模式不设点外关闭层（Operit TabletLayout 常驻侧边栏语义）：
            // 否则全屏透明层会拦截压缩后聊天页的点击（顶栏终端/文件按钮等），
            // 点一次先关抽屉、点两次才进页面（2026-08-30 平板实测 bug）。
            if (chatState.drawerOpen || progress > 0.001f) {
                Box(Modifier.zIndex(2f)) {
                    if (chatState.drawerOpen && !useCompress) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clickable(onClick = { chatState.drawerOpen = false }),
                        )
                    }
                    SessionDrawer(
                        chatState = chatState,
                        // 切换会话：平板压缩保持展开（持久侧边栏语义），其余模式关闭
                        onClose = { if (!useCompress) chatState.drawerOpen = false },
                        onNavigate = { route ->
                            // 平板压缩模式：导航不关闭侧边栏（持久侧边栏语义，返回聊天页仍展开）
                            if (!useCompress) chatState.drawerOpen = false
                            nav.navigate(route)
                        },
                        modifier = Modifier.graphicsLayer {
                            translationX = drawerOffset.toPx()
                            scaleX = drawerScale
                            scaleY = drawerScale
                            alpha = drawerAlpha
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        },
                    )
                }
            }
        } else {
            // 水平滑出（默认）：现状 AnimatedVisibility 不变
            AnimatedVisibility(
                visible = chatState.drawerOpen,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it }),
                modifier = Modifier.zIndex(2f),
            ) {
                // ★ 遮罩层必须全屏铺底（Operit 同款：scrim zIndex 1.5 全屏、面板 zIndex 2 在其上）：
                //   面板背景从状态栏下开始 + 右侧圆角 → 面板布局盒内无背景的区域
                //   （状态栏条 / 右上右下圆角缺口）透出全屏 scrim 的压暗效果，罩子才完整。
                Box {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim)
                            .clickable(onClick = { chatState.drawerOpen = false }),
                    )
                    SessionDrawer(
                        chatState = chatState,
                        onClose = { chatState.drawerOpen = false },
                        onNavigate = { route ->
                            chatState.drawerOpen = false
                            nav.navigate(route)
                        },
                    )
                }
            }
        }

        // ── 模型选择器浮层 ──
        if (modelSheetOpen) {
            Box(Modifier.fillMaxSize().zIndex(3f)) {
                // ★ 点外关闭层必须在浮层之下（先画），否则会拦截浮层内所有点击
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(onClick = { modelSheetOpen = false }),
                )
                ModelSelectorSheet(
                    chatState = chatState,
                    onClose = { modelSheetOpen = false },
                    onManageModels = {
                        modelSheetOpen = false
                        nav.navigate("model_config")
                    },
                    bottomOffset = modelSheetBottomOffset,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }

        // ── 上下文用量卡（与模型选择器同款浮层：点外关闭、无 scrim、贴右下） ──
        if (contextCardOpen) {
            Box(Modifier.fillMaxSize().zIndex(3f)) {
                // ★ 点外关闭层必须在卡片之下（先画），否则会拦截卡片内所有点击
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(onClick = { contextCardOpen = false }),
                )
                ContextUsageCard(
                    chatState = chatState,
                    // 指示器与模型按键同行、上缘相同，复用同一锚定值
                    bottomOffset = modelSheetBottomOffset,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }

        // ── 系统提示词只读面板（2026-09-01，pi-web system 面板同款；浮层同家族规格） ──
        if (systemPromptOpen) {
            Box(Modifier.fillMaxSize().zIndex(3f)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(onClick = { systemPromptOpen = false }),
                )
                SystemPromptPanel(
                    prompt = chatState.systemPrompt,
                    bottomOffset = modelSheetBottomOffset,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }

        // ── "+" 附件菜单（与模型选择器同款浮层：点外关闭、无 scrim、贴右下） ──
        if (attachSheetOpen) {
            Box(Modifier.fillMaxSize().zIndex(3f)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(onClick = { attachSheetOpen = false }),
                )
                AttachmentSheet(
                    chatState = chatState,
                    onClose = { attachSheetOpen = false },
                    onOpenUrlDialog = {
                        attachSheetOpen = false
                        urlDialogOpen = true
                    },
                    bottomOffset = modelSheetBottomOffset,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }

        // ── URL 输入弹窗（附件菜单 → URL，Hermes url-dialog 同款） ──
        if (urlDialogOpen) {
            Box(Modifier.zIndex(3f)) {
                UrlDialog(
                    onDismiss = { urlDialogOpen = false },
                    onConfirm = { url ->
                        chatState.attachments += Attachment(url, AttachmentKind.URL)
                        urlDialogOpen = false
                    },
                )
            }
        }

        // ── @ 引用文件卡片（悬浮浮层：点外关闭、无 scrim、贴输入栏左上方） ──
        if (mentionOpen) {
            Box(Modifier.fillMaxSize().zIndex(3f)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(onClick = { mentionOpen = false }),
                )
                MentionFileCard(
                    files = mentionFiles,
                    onPick = { rel ->
                        // 去掉触发字符 '@'（引用卡仅在文本以 "@" 结尾时打开），
                        // 插入 "@路径 "（尾随空格提交 token），光标置末尾
                        val newText = inputText.text.dropLast(1) + "@$rel "
                        inputText = TextFieldValue(newText, selection = TextRange(newText.length))
                        mentionOpen = false
                    },
                    bottomOffset = mentionBottomOffset,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }

        // ── 长按消息上下文菜单（fork 会话外分支入口，2026-09-02 分支功能设计 §4）──
        if (forkMenuTarget != null) {
            val (forkIdx, forkRect) = forkMenuTarget!!
            Box(Modifier.fillMaxSize().zIndex(3f)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(onClick = { forkMenuTarget = null }),
                )
                ForkContextMenu(
                    anchor = forkRect,
                    forkEnabled = !chatState.isStreaming && chatState.currentSession?.running != true,
                    onFork = {
                        forkMenuTarget = null
                        chatState.forkSession(forkIdx)
                        Toast.makeText(context, "已创建新会话", Toast.LENGTH_SHORT).show()
                    },
                    onCopy = {
                        forkMenuTarget = null
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}

// ───────────────────────────── 顶部栏 ─────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatTopBar(
    chatState: ChatState,
    onMenu: () -> Unit,
    onPanel: (Panel) -> Unit,
) {
    PientPanel(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding() // 状态栏避让：总高 = 56dp + 状态栏高
            .height(56.dp),
        shape = RoundedCornerShape(0.dp),
    ) {
        // 未绑定项目（首次进入）：顶栏只保留最左侧侧边栏唤出按键——
        // 无会话名/状态徽标/分支/终端/文件按键（2026-09-08 用户定）
        if (chatState.currentProject == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
            ) {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Outlined.Menu, "会话侧栏", tint = MaterialTheme.colorScheme.onBackground)
                }
            }
            return@PientPanel
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
        ) {
        IconButton(onClick = onMenu) {
            Icon(Icons.Outlined.Menu, "会话侧栏", tint = MaterialTheme.colorScheme.onBackground)
        }
        // 中：会话标题 + 运行状态徽标
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            val title = chatState.currentSession?.title ?: "新会话"
            // 过长标题：默认 Ellipsis；长按期间跑马灯滚动显示全文
            var marquee by remember { mutableStateOf(false) }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (marquee) Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                        ) else Modifier,
                    )
                    .pointerInput(title) {
                        detectTapGestures(
                            onLongPress = { marquee = true },
                            onPress = {
                                tryAwaitRelease()
                                marquee = false
                            },
                        )
                    },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val running = chatState.isStreaming || chatState.currentSession?.running == true
                if (running) {
                    StatusBadge("运行中", MaterialTheme.colorScheme.primary)
                } else {
                    StatusBadge("空闲", MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // 未绑定项目时状态行不显示项目名（2026-09-08：引导页接管）
                chatState.currentProject?.let { proj ->
                    Text(
                        "  ·  $proj",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // 右：分支 / 终端 / 文件（顶栏下方区域四态切换入口，激活图标高亮主色）
        // 三键行为完全一致：仅对应面板激活时 primary，否则 onBackground
        // （2026-09-02 用户拍板：分支键不做 hasBranches 指示灯）。
        val active = chatState.activePanel
        IconButton(onClick = { onPanel(Panel.TREE) }) {
            Icon(
                Icons.Outlined.AccountTree, "会话分支",
                tint = if (active == Panel.TREE) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground,
            )
        }
        IconButton(onClick = { onPanel(Panel.TERMINAL) }) {
            Icon(
                Icons.Outlined.Terminal, "终端",
                tint = if (active == Panel.TERMINAL) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground,
            )
        }
        IconButton(onClick = { onPanel(Panel.FILES) }) {
            Icon(
                Icons.Outlined.Description, "文件",
                tint = if (active == Panel.FILES) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground,
            )
        }
        }
    }
}

// ───────────────────────────── 消息区 ─────────────────────────────

@Composable
private fun MessagesPanel(
    chatState: ChatState,
    scope: kotlinx.coroutines.CoroutineScope,
    onMessageLongPress: (Int, Rect) -> Unit,
    onConfigureAi: () -> Unit,
) {
    if (chatState.currentProject == null || !chatState.aiConfigured) {
        // 2026-09-08 用户定：项目与 AI 配置两者齐备前，消息区显示引导清单
        // （任一未完成即显示，已完成步骤打勾提示）
        FirstRunGuide(chatState = chatState, onConfigureAi = onConfigureAi)
        return
    }
    ChatMessages(
        messages = chatState.currentMessages,
        isStreaming = chatState.isStreaming,
        streamDraft = chatState.streamDraft,
        onMessageLongPress = onMessageLongPress,
    )
}
