package com.pient.app.ui.chat

import com.pient.app.data.i18n.L
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import android.widget.Toast
import com.pient.app.data.Attachment
import com.pient.app.data.AttachmentKind
import com.pient.app.data.ChatState
import com.pient.app.data.DrawerMode
import com.pient.app.data.PanelMaterial
import com.pient.app.data.Msg
import com.pient.app.data.Panel
import com.pient.app.data.Quote
import com.pient.app.data.SettingsStore
import com.pient.app.data.SidebarStyle
import com.pient.app.data.turnXml
import com.pient.app.ui.components.isTabletLayout
import com.pient.app.ui.components.StatusBadge
import com.pient.app.ui.files.FilesPanel
import com.pient.app.ui.terminal.TerminalPanel
import com.pient.app.ui.theme.LocalWaterGlassState
import com.pient.app.ui.theme.PientPanel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.fletchmckee.liquid.liquefiable
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.collectAsState
import com.pient.app.ui.components.PientButton
import androidx.compose.foundation.border
import com.pient.app.PientRuntime

/**
 * 聊天主页（P1）：顶栏常驻，下方区域在 消息区 / 文件内容预览区（P3）/ 终端页（P4）
 * 之间切换承载；侧栏抽屉与文件树均为同窗口浮层（玻璃真模糊要求）。
 *
 * @param startupReady 首屏数据（项目/会话/AI 配置）是否已读盘完成。关掉「开屏动画」时开屏页
 *   不渲染，这个标志用来压住「数据未就绪 → 引导清单误闪」（原先由开屏页盖住）。
 */
@Composable
fun ChatScreen(chatState: ChatState, nav: NavController, startupReady: Boolean = true) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current
    val screenHpx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    var modelSheetOpen by remember { mutableStateOf(false) }
    var attachSheetOpen by remember { mutableStateOf(false) }
    var contextCardOpen by remember { mutableStateOf(false) }
    var systemPromptOpen by remember { mutableStateOf(false) }
    var urlDialogOpen by remember { mutableStateOf(false) }
    // 消息定位弹窗与消息区滚动状态（2026-09-09 提升到 ChatScreen 根层：弹窗 scrim 需全屏
    // 覆盖顶栏与系统状态栏；listState 提升后 ChatMessages 与定位弹窗共享同一滚动状态）
    var locatorOpen by remember { mutableStateOf(false) }
    val messagesListState = rememberLazyListState()
    // 消息列表的可视高度（dp）——「一屏」的计量单位：优先用实测视口高度，
    // 首帧还没测量时用屏幕高 × 0.72 估（扣掉顶栏与输入栏）。长会话窗口按它折算屏数。
    //
    // ★ 必须走 derivedStateOf，不能在组合期直接读 layoutInfo：layoutInfo 是**粗粒度**状态，
    //   每次测量都可能换一个新对象，直接在组合期读它 → 整页（顶栏/抽屉/输入栏/消息区）
    //   跟着反复重组，白白占用 UI 线程（2026-09-12 滚动卡顿排查实测：滑动时 ChatScreen
    //   每秒重组 8~19 次，绝大多数由这个视口高度读取触发）。派生块把「读」收窄成
    //   **视口高度真的变了才通知**。
    val listViewportDp by remember(density) {
        derivedStateOf {
            val px = messagesListState.layoutInfo.viewportSize.height
            with(density) { (if (px > 0) px else (screenHpx * 0.72f).toInt()).toDp().value }
        }
    }
    // 长按消息 → fork 上下文菜单（2026-09-02 分支功能设计 §4）：目标消息下标 + 气泡根坐标 + 触点
    var forkMenuTarget by remember { mutableStateOf<MessageMenuTarget?>(null) }
    // 复制消息卡（2026-09-11）：内容在打开时快照，避免下标失效；null = 未打开
    var copyCardText by remember { mutableStateOf<String?>(null) }
    // 复制消息卡的 XML 分段（2026-09-17）：与 copyCardText 同一次打开时快照 —— 该回合 AI 侧全量
    var copyCardXml by remember { mutableStateOf("") }
    // 待发送引用块（引用某条消息追问；2026-09-11）
    var pendingQuote by remember { mutableStateOf<Quote?>(null) }
    var inputFocusTick by remember { mutableIntStateOf(0) }
    // 输入框文本与 @ 引用状态（提升到 ChatScreen：引用卡为悬浮浮层）。
    // TextFieldValue 承载光标位置：@ 选择文件后光标需落在 "@路径 " 末尾，
    // 且退格一键删除整段引用（String 状态无法控制光标）。
    var inputText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

    // pi 就绪态（2026-09-15 拍板 B）：进页面即测一次（通道没起时顺带起一次）
    LaunchedEffect(Unit) { chatState.refreshPiReadiness() }

    // 被阻断的发送等一次性提示（用完即清）
    LaunchedEffect(chatState.blockedNote) {
        val note = chatState.blockedNote ?: return@LaunchedEffect
        Toast.makeText(context, note, Toast.LENGTH_SHORT).show()
        chatState.blockedNote = null
    }
    // 被拦下的发送：把文本还给输入栏并聚焦（2026-09-16：pi 还在处理上一轮时不再硬发，
    // 见 ChatState.streamReply 开头的拦截 —— 草稿不能因为"没发出去"而丢）
    LaunchedEffect(chatState.draftRestore) {
        val t = chatState.draftRestore ?: return@LaunchedEffect
        inputText = TextFieldValue(t, TextRange(t.length))
        chatState.draftRestore = null
        inputFocusTick++
    }
    // @ 引用查询（派生态，2026-09-12）：光标前最近一个「词首 @」到光标之间的文本即筛选串；
    // null = 不在引用输入中（引用已提交 "@路径 " 之后、词中 @、或压根没 @）。
    // 查询串随每次输入变化 → 引用卡候选列表实时筛选，不需要额外的开关状态。
    val mentionQuery = remember(inputText.text, inputText.selection) { findMentionQueryAt(inputText) }
    // 点外/返回键关闭后，同一查询串不再自动弹出（继续输入改变查询串 = 重新出现）
    var mentionDismissedQuery by remember { mutableStateOf<String?>(null) }
    val mentionOpen = mentionQuery != null && mentionQuery.text != mentionDismissedQuery
    // 查询结束（引用提交 / 文本改动让 @ 不再紧跟光标）→ 清掉关闭标记：
    // 否则「关闭一次后清空重输 @」会因为查询串又是同一串（常见为空串）而不再弹出
    LaunchedEffect(mentionQuery) {
        if (mentionQuery == null) mentionDismissedQuery = null
    }
    // ★ 润色 / 回退回填是唯一例外：它的查询串在下面那条 effect 里**已经**记成已关闭，
    //   这里判据只看 mentionQuery 是否为 null，不会把刚记下的关闭标记清掉。
    // 润色结果 / 回退内容回填输入框（2026-09-16 输入栏润色；一次性信号，口径同 draftRestore）。
    // 光标落到末尾 —— 回填后是接着看/接着改的。
    LaunchedEffect(chatState.polishApply) {
        val t = chatState.polishApply ?: return@LaunchedEffect
        val v = TextFieldValue(t, TextRange(t.length))
        // 回填是**程序写入**、不是用户正在敲 @ 引用：先把此刻的查询串记为「已关闭」再落文本，
        // 否则刚润色完就弹一张「引用文件 / 无匹配文件」卡 —— findMentionQueryAt 只看
        // 「以空白开头的 @ 到光标之间没有空白」，润色后的整句（`请阅读 @x.md，并…`）正好长这样。
        mentionDismissedQuery = findMentionQueryAt(v)?.text
        inputText = v
        chatState.polishApply = null
    }
    // @ 引用文件来源 = 当前项目真实文件树（未绑定项目/未加载时为空列表）
    val mentionFiles = remember(chatState.fileTreeRoot) {
        chatState.fileTreeRoot?.let { buildMentionFiles(it) } ?: emptyList()
    }
    // 候选 = 按光标处查询串筛选（名称前缀 > 名称包含 > 路径包含，同级保持文件树顺序）
    val mentionCandidates = remember(mentionFiles, mentionQuery?.text) {
        filterMentionFiles(mentionFiles, mentionQuery?.text.orEmpty())
    }
    // 文件树长按菜单「@ 提及插入输入框」：请求里已是成品引用文本（`@路径 ` / `@"含空格 路径" `，
    // 由 FileTreePanel 用 mentionTextFor 生成），这里只做追加；引用卡不再弹出
    LaunchedEffect(chatState.mentionInsertRequest) {
        chatState.mentionInsertRequest?.let { mention ->
            // 光标停在插入文本末尾（与候选卡点选同一口径）—— 此前默认落在文本开头，
            // 长按插入后接着打字会跑到最前面
            val merged = inputText.text + mention
            inputText = TextFieldValue(merged, selection = TextRange(merged.length))
            chatState.mentionInsertRequest = null
            // 插引用 = 用户改动了提示词 → 润色态作废（回退键变回润色键）
            chatState.clearPolishRevert()
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

    // 输入栏背后内容层（2026-09-12，修「玻璃输入框看着像遮罩、内容滑过不透」）：
    // 输入栏改为覆盖在面板内容之上（Operit ClassicChatInputSection 同款 —— 其输入栏是
    // align(BottomCenter) 浮层），面板内容单独录一层 backdrop / 标记 liquefiable 供玻璃采样，
    // 从而「内容滑过输入栏时能从玻璃里透出模糊的内容」。输入栏与该层同级 → 不会渲染树自引用。
    val panelBackdrop = rememberLayerBackdrop()
    val waterGlassState = LocalWaterGlassState.current
    val chatGlassOn = SettingsStore.inputBarMaterial != PanelMaterial.DEFAULT
    // 侧边栏玻璃（2026-09-12，侧边栏设置）：抽屉是面板内容层之上的浮层（zIndex 2 vs 1），
    // 采样的是同一路「面板内容」backdrop —— 该层不含抽屉自身，故不会出现「采样层里套着
    // 玻璃节点」的自引用（Mdcito 记录的红线 / 渲染树递归）。
    // 抽屉顶栏那一条带采样不到内容层时会落到主题背景层（combined backdrop 的另一路），
    // 观感与顶栏本身一致（都是浅色实面）。
    val sidebarGlassOn = SettingsStore.sidebarMaterial != PanelMaterial.DEFAULT
    // 内容层是否需要录层 / 标记液化层：任一玻璃开启即需要（两处玻璃共用这一路 backdrop）
    val contentGlassOn = chatGlassOn || sidebarGlassOn
    var dockHeightPx by remember { mutableIntStateOf(0) }
    val dockInset = with(density) { dockHeightPx.toDp() }

    // 文件树随当前项目加载（@ 引用文件源；文件树面板打开时也会刷新）
    LaunchedEffect(chatState.currentProject) {
        chatState.refreshFileTree(context)
    }

    // ── 返回键优先级链（2026-09-11 修复：此前侧栏展开时单击返回直接退出应用）──
    // 按渲染层级从高到低逐层关闭最上层浮层/抽屉（zIndex 3 浮层的渲染顺序见下方 when 分支，
    // 后渲染者在上；侧栏为 zIndex 2 故最后关），全部关完才进入二次退出。
    // 旧实现 `BackHandler(enabled = overlaysClosed)` 在浮层/抽屉打开时**禁用**拦截，而
    // Pient 的浮层都是自绘「点外关闭」面板、全项目仅 ChatScreen 与 ModelConfigScreen 两处
    // BackHandler → 返回键落到系统默认行为 = finish Activity，即抽屉展开时单击返回 = 退出。
    var lastBackPress by remember { mutableStateOf(0L) }
    BackHandler {
        when {
            locatorOpen -> locatorOpen = false
            copyCardText != null -> copyCardText = null
            forkMenuTarget != null -> forkMenuTarget = null
            // 引用卡关闭 = 记住当前查询串（同一串不再弹；继续输入即重新筛选显示）
            mentionOpen -> mentionDismissedQuery = mentionQuery.text
            urlDialogOpen -> urlDialogOpen = false
            attachSheetOpen -> attachSheetOpen = false
            systemPromptOpen -> systemPromptOpen = false
            contextCardOpen -> contextCardOpen = false
            modelSheetOpen -> modelSheetOpen = false
            // 文件关闭确认弹窗（文件预览页内，同样是自绘浮层）
            chatState.closingTabIndex != null -> chatState.closingTabIndex = null
            chatState.drawerOpen -> chatState.drawerOpen = false
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPress > 2000L) {
                    lastBackPress = now
                    Toast.makeText(context, L.chat.pressAgainToExit, Toast.LENGTH_SHORT).show()
                } else {
                    (context as? Activity)?.finish()
                }
            }
        }
    }

    // ── 抽屉手势（Operit PhoneLayout 同款实现）：
    //   根 Box 挂全局 drag 检测——全屏任意位置水平右滑打开（水平位移主导：
    //   |dx| > |dy| 才触发，不与消息列表滚动冲突）、左滑关闭；阈值 40px。
    //   子级手势（列表滚动/文本选择/输入框拖动）先消费事件，天然互不抢占。
    val drawerOpenNow by rememberUpdatedState(chatState.drawerOpen)
    var dragDx by remember { mutableStateOf(0f) }
    var dragDy by remember { mutableStateOf(0f) }
    // 多指手势监视（捏合缩放预览等）：只读不消费，仅用于让抽屉拖动让位 ——
    // 否则捏合时第一根手指的水平位移会累计进 dragDx，超过 40px 就把侧栏滑出来
    // （放大缩小时侧栏乱弹/内容被推走，缩放自然“不丝滑”）。
    val multiPointerDown = remember { mutableStateOf(false) }

    // ── 抽屉展出方式（行为设置）：SLIDE = 水平滑出（默认）／ PERSPECTIVE ／ PUSH ──
    // 手机 = 三选一；平板 = 固定压缩滑出（2026-09-10 用户决策：平板不支持
    // 3D 透视展开 / 推动展开），与手机端所选展出方式无关。
    // 手机 = 3D 透视（Operit PhoneLayout 同款，仅选中 PERSPECTIVE 时）；
    // 平板 = 聊天页宽度压缩 + 侧边栏滑出（Operit TabletLayout 同款 width+offset 结构），
    // 为平板默认行为（2026-08-30 用户决策）。
    val configuration = LocalConfiguration.current
    val isTablet = isTabletLayout()
    val drawerMode = SettingsStore.drawerMode
    val use3D = drawerMode == DrawerMode.PERSPECTIVE && !isTablet
    // 推动展开（2026-09-10，仅手机）：侧栏滑入的同时主内容整体右移一个侧栏宽——两者由同一
    // progress 驱动，逐帧同步；用 offset（不改变布局尺寸）推移，页面内部不回排版面。
    val usePush = drawerMode == DrawerMode.PUSH && !isTablet
    val useCompress = isTablet
    // 平板端 = 常驻侧边栏语义（导航切换/点外一律不收起，2026-08-30 用户决策）；手机端点击即收
    val persistentDrawer = isTablet
    val drawerWidth = 296.dp
    // ★ 悬浮侧栏（侧边栏样式）整体外缩 12dp：面板右缘 = 296dp + 12dp = 308dp。
    //   **所有按「侧栏宽」算的位移/宽度/点外阈值一律用 drawerRevealWidth（面板右缘）**——
    //   仍按 296dp 算会让侧栏右缘（含右描边）压在聊天页上 12dp
    //   （2026-09-12 用户报「侧栏右侧部分遮挡聊天页」；实测：内容右移 777px=296dp、
    //   面板右缘 808.5px=308dp → 重叠 31.5px=12dp）。贴边样式 inset = 0，
    //   三模式的数值与改造前逐项一致（3D 的 0.82×宽仍是 Operit 口径，不受影响）。
    val sidebarFloating = SettingsStore.sidebarStyle == SidebarStyle.FLOATING
    val drawerRevealWidth = if (sidebarFloating) drawerWidth + SidebarFloatingInset else drawerWidth

    // 抽屉动画进度（Operit PhoneLayout：开 LowBouncy / 关 NoBouncy，stiffness 1000；
    // 平板压缩模式对齐 Operit TabletLayout 的 tween 280ms 宽度动画；
    // 推动展开与水平滑出同为 300ms tween——侧栏与主内容同步位移）
    val progress by animateFloatAsState(
        targetValue = if (chatState.drawerOpen) 1f else 0f,
        animationSpec = if (use3D) {
            spring(
                dampingRatio = if (chatState.drawerOpen) Spring.DampingRatioLowBouncy else Spring.DampingRatioNoBouncy,
                stiffness = 1000f,
            )
        } else if (usePush) {
            tween(durationMillis = 300)
        } else {
            tween(durationMillis = 280)
        },
        label = "drawerProgress",
    )

    // ── Operit PhoneLayout 数值逐项对齐（enableNavigationAnimation 分支）──
    // 主内容：平移 82% 宽 + 下移 12dp + 缩放 0.92 + Y 轴 -7° + 圆角 24dp
    // （Operit 原版还有 18dp 阴影，用户 2026-09-09 定：多余，移除）；
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
    // 抽屉自身滑入起始位：按「面板右缘」退到屏幕外（贴边=296dp / 悬浮=308dp），
    // 否则悬浮样式在动画起点会露出面板右端 12dp 的一小条
    val drawerOffset = -drawerRevealWidth * (1f - progress)
    val drawerScale = if (use3D) 0.92f + (0.08f * progress) else 1f
    val drawerAlpha = if (use3D) 0.72f + (0.28f * progress) else 1f
    // 面板右缘（px）——点外关闭判定用：点击 x 超过该值才算「点侧栏之外」
    // （悬浮样式下 296dp 位置其实是侧栏本体的右端，按 296dp 判定会把「点侧栏右端」当点外）
    val drawerRevealWidthPx = with(LocalDensity.current) { drawerRevealWidth.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // 多指监视器（Initial pass：不消费事件，因此不影响任何既有手势）
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        multiPointerDown.value = e.changes.count { it.pressed } > 1
                        if (e.changes.none { it.pressed }) break
                    }
                    multiPointerDown.value = false
                }
            }
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
                    if (multiPointerDown.value) {   // 双指（捏合）不驱动抽屉
                        dragDx = 0f
                        dragDy = 0f
                        return@detectDragGestures
                    }
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
        //    平板压缩模式 = 宽度收缩 + 右移（Operit TabletLayout 同款 layout 层方案），
        //    推动展开 = 整体右移一个侧栏宽（offset 不改变布局尺寸，页面内部不回排版面）──
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
                        }
                        usePush -> Modifier.offset(x = drawerRevealWidth * progress)
                        useCompress -> Modifier
                            .width(configuration.screenWidthDp.dp - drawerRevealWidth * progress)
                            .offset(x = drawerRevealWidth * progress)
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
            // pi 运行时就绪条（2026-09-15 用户拍板 B）：**pi = 唯一产品路径** ——
            // 未就绪时明确告知 + 给「环境配置」修复入口（不再静默降级到没有工具的直连内核）
            if (chatState.piReadiness == ChatState.PiReadiness.Unready) {
                PiUnreadyStrip(
                    reason = chatState.piUnreadyReason,
                    onRetry = { chatState.refreshPiReadiness() },
                    onFix = { nav.navigate("terminal_setup") },
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 2.dp),
                )
            }
            // 工具能力已整体移除（2026-09-14）：「宿主未就绪 → 工具不可用」这类降级提示不存在
            // （原 HostNotReadyStrip 早已删除）。
            // 面板内容 + 覆盖其上的输入栏（2026-09-12：输入栏 dock 改为浮层，
            // 面板内容伸到屏幕底部、可从玻璃里透出 —— Operit 输入栏 align(BottomCenter) 同款）
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        // 仅在（任一处）玻璃材质下录层/标记液化层：默认材质时不做额外合成
                        .then(if (contentGlassOn) Modifier.layerBackdrop(panelBackdrop) else Modifier)
                        .then(
                            if (contentGlassOn && waterGlassState != null) {
                                Modifier.liquefiable(waterGlassState)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    when (chatState.activePanel) {
                        Panel.MESSAGES -> MessagesPanel(
                            chatState = chatState,
                            startupReady = startupReady,
                            scope = scope,
                            listState = messagesListState,
                            viewportDp = listViewportDp,
                            bottomInset = dockInset,
                            onOpenLocator = { locatorOpen = true },
                            onMessageLongPress = { target -> forkMenuTarget = target },
                            onConfigureAi = {
                                attachSheetOpen = false
                                nav.navigate("model_config")
                            },
                            onOpenEnvSetup = {
                                attachSheetOpen = false
                                nav.navigate("terminal_setup")
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
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .onGloballyPositioned { dockHeightPx = it.size.height },
                    ) {
                    ChatInputBar(
                        chatState = chatState,
                        text = inputText,
                        onTextChange = {
                            // 一键删除整段 @ 引用：单字符退格 + 光标停在 token 末尾
                            // → 整个 "@路径 " 一起删掉（Operit normalizeMentionDeletion 同款）
                            inputText = normalizeMentionDeletion(inputText, it, mentionFiles)
                            // 用户改动了润色结果 → 润色态作废（回退键变回润色键，2026-09-16 spec）
                            chatState.clearPolishRevert()
                        },
                        mentionFiles = mentionFiles,
                        onOpenModelSelector = { modelSheetOpen = true },
                        onOpenAttach = { attachSheetOpen = !attachSheetOpen },
                        onToggleContextCard = { contextCardOpen = !contextCardOpen },
                        onToggleSystemPrompt = {
                            systemPromptOpen = !systemPromptOpen
                            // 打开面板时向 pi 取**真实下发的那一份**（App 侧已不再持有提示词）
                            if (systemPromptOpen) scope.launch { chatState.refreshSystemPrompt() }
                        },
                        onSend = { text ->
                            // pi 未就绪 → 阻断发送，并把草稿**原样还回**输入栏（2026-09-15 拍板 B；
                            // 输入栏在点击时已自行清空文本，这里补回来，避免"消息没发出去还丢了草稿"）
                            if (chatState.piReadiness == ChatState.PiReadiness.Unready) {
                                inputText = TextFieldValue(text, TextRange(text.length))
                                Toast.makeText(context, L.chat.piUnreadyToast, Toast.LENGTH_SHORT).show()
                            } else {
                                // 首条消息自动建会话（2026-09-08：无 mock 会话后，发送即建当前项目首会话）
                                if (chatState.currentSessionId == null) chatState.newSession()
                                val q = pendingQuote
                                // 本轮跑在 ChatState 自己的 scope 上，**不是**这里的 UI scope —— 离开页面
                                // 不该静默取消一轮（2026-09-16 真机实测的根因，见 ChatState.startTurn）
                                chatState.startTurn(text, q)
                                pendingQuote = null // 引用随消息落库（Msg.User.quote），输入栏引用卡随之清空
                            }
                        },
                        onAbort = { chatState.abort() },
                        modelSelectorOpen = modelSheetOpen,
                        quote = pendingQuote,
                        onRemoveQuote = { pendingQuote = null },
                        focusTick = inputFocusTick,
                        onChipPositioned = { chipTopY = it },
                        onDockTopPositioned = { dockTopY = it },
                        backdrop = panelBackdrop,
                    )
                    }
                }
            }
        }
        }

        // ── 侧栏抽屉（zIndex 2）──
        if (use3D || useCompress || usePush) {
            // 3D 透视 / 平板压缩 / 推动展开：同一 progress 驱动抽屉滑入（Operit PhoneLayout 同款）。
            // 完全关闭时移出组合（不占命中区域）；3D 与推动展开的点外关闭层为透明 ——
            // Operit 同款：scrim 透明，3D 变换/内容推移本身传达模态。
            // 平板端不设点外关闭层（常驻侧边栏语义）：否则全屏透明层会拦截压缩/推移后
            // 聊天页的点击（顶栏终端/文件按钮等），点一次先关抽屉、点两次才进页面
            // （2026-08-30 平板实测 bug）。
            if (chatState.drawerOpen || progress > 0.001f) {
                Box(Modifier.zIndex(2f)) {
                    if (chatState.drawerOpen && !persistentDrawer) {
                        // ★ 点外关闭层：只在侧栏宽（296dp）之外的点击才收起。
                        //   抽屉面板是纯视觉层（无指针处理），其空白区（行间隙/状态栏条/
                        //   空列表区）的点击会穿透到本层——加 x 判定后这些点击被忽略，
                        //   抽屉保持展开；侧栏内交互组件自身的点击天然先被消费不受影响。
                        Box(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        if (offset.x > drawerRevealWidthPx) chatState.drawerOpen = false
                                    }
                                },
                        )
                    }
                    SessionDrawer(
                        chatState = chatState,
                        // 切换会话：平板端保持展开（持久侧边栏语义），其余关闭
                        onClose = { if (!persistentDrawer) chatState.drawerOpen = false },
                        onNavigate = { route ->
                            // 平板端：导航不关闭侧边栏（持久侧边栏语义，返回聊天页仍展开）
                            if (!persistentDrawer) chatState.drawerOpen = false
                            nav.navigate(route)
                        },
                        backdrop = panelBackdrop,
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
            // 水平滑出（默认）：抽屉滑动与遮罩淡入拆为两层、同规格 tween(300ms) 同步——
            // 遮罩固定全屏铺底（覆盖顶栏/输入 dock 全区域）只做透明度 0→1 淡入，
            // 抽屉从左滑入；抽屉滑出到位时遮罩恰好完全显现（原实现两者同盒滑动，
            // 遮罩随盒从左边推出：动画前半程右侧屏幕无遮罩）。
            AnimatedVisibility(
                visible = chatState.drawerOpen,
                enter = fadeIn(tween(durationMillis = 300)),
                exit = fadeOut(tween(durationMillis = 300)),
                modifier = Modifier.zIndex(2f),
            ) {
                // ★ 遮罩层全屏铺底 + 点外关闭（x > 侧栏宽 296dp 才收起；侧栏内
                //   空白区穿透下来的点击被忽略，修复「点侧栏内某些位置抽屉收起」）。
                //   淡入淡出不改变布局位置——x 判定基于未偏移的布局坐标，始终有效。
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                if (offset.x > drawerRevealWidthPx) chatState.drawerOpen = false
                            }
                        },
                )
            }
            AnimatedVisibility(
                visible = chatState.drawerOpen,
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(durationMillis = 300),
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(durationMillis = 300),
                ),
                modifier = Modifier.zIndex(2f),
            ) {
                SessionDrawer(
                    chatState = chatState,
                    onClose = { chatState.drawerOpen = false },
                    onNavigate = { route ->
                        chatState.drawerOpen = false
                        nav.navigate(route)
                    },
                    backdrop = panelBackdrop,
                )
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
            // 打开时拉一次 pi 的上下文用量真值（get_session_stats.contextUsage；2026-09-16）
            LaunchedEffect(Unit) { chatState.requestContextUsage() }
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
                    // 手动压缩（移动端对桌面端 `/compact` 的等价入口）；结果以压缩卡形式落到聊天页
                    onCompact = {
                        scope.launch {
                            // null = 成功；非空 = 如实回报的原因（pi 原生 compact）
                            val reason = chatState.compactNow()
                            Toast.makeText(
                                context,
                                reason ?: L.chat.compactDone,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
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

        // ── @ 引用文件卡片（悬浮浮层：点外关闭、无 scrim、贴输入栏左上方；候选随输入实时筛选） ──
        if (mentionOpen) {
            Box(Modifier.fillMaxSize().zIndex(3f)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(onClick = { mentionDismissedQuery = mentionQuery.text }),
                )
                MentionFileCard(
                    files = mentionCandidates,
                    query = mentionQuery.text,
                    onPick = { picked ->
                        // 把「@ + 已输入筛选字符」整段替换为引用文本（@路径 / @"含空格 路径"，
                        // 尾随空格提交 token），光标置末尾 —— 格式的唯一实现在 mentionTextFor
                        val start = mentionQuery.start
                        val end = mentionQuery.endExclusive
                        val mention = mentionTextFor(picked.path, picked.isDir)
                        val newText = inputText.text.replaceRange(start, end, mention)
                        inputText = TextFieldValue(newText, selection = TextRange(start + mention.length))
                        // 同上：插入引用也算用户改动了提示词 → 润色态作废
                        chatState.clearPolishRevert()
                    },
                    bottomOffset = mentionBottomOffset,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }

        // ── 长按消息上下文菜单（fork 会话外分支入口，2026-09-02 分支功能设计 §4）──
        if (forkMenuTarget != null) {
            val target = forkMenuTarget!!
            val forkIdx = target.index
            Box(Modifier.fillMaxSize().zIndex(3f)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(onClick = { forkMenuTarget = null }),
                )
                ForkContextMenu(
                    anchor = target.anchor,
                    press = target.press,
                    forkEnabled = !chatState.isStreaming && chatState.currentSession?.running != true,
                    isAssistant = chatState.currentMessages.getOrNull(forkIdx) is Msg.Assistant,
                    onFork = {
                        forkMenuTarget = null
                        // 会话外分支 = pi 原生 navigate（回合末尾锚点）+ clone（含 fork 点）：
                        // 结果走 ChatState.blockedNote 通道给一句 Toast（成功「已创建新会话」/ 失败说明）
                        chatState.forkFromMessage(forkIdx)
                    },
                    onCopy = {
                        // 打开时快照内容：Markdown 源码态 = 助手回答原文；用户消息 = 消息文本
                        copyCardText = when (val m = chatState.currentMessages.getOrNull(forkIdx)) {
                            is Msg.User -> m.text
                            is Msg.Assistant -> m.markdown
                            else -> null
                        }
                        // XML 分段：该回合 AI 侧全量（思考过程 + 工具调用过程 + 正文）
                        copyCardXml = turnXml(chatState.currentMessages, forkIdx)
                        forkMenuTarget = null
                    },
                    onQuote = {
                        // 引用该条消息（可追问）：挂到输入栏引用块 + 聚焦输入框接着打字
                        pendingQuote = when (val m = chatState.currentMessages.getOrNull(forkIdx)) {
                            is Msg.User -> Quote(m.text, "user")
                            is Msg.Assistant -> Quote(m.markdown, "assistant")
                            else -> null
                        }
                        forkMenuTarget = null
                        inputFocusTick++
                    },
                )
            }
        }
        // ── 复制消息卡（2026-09-11；页面根层：scrim 全屏覆盖顶栏与状态栏）──
        if (copyCardText != null) {
            Box(Modifier.fillMaxSize().zIndex(3f)) {
                MessageCopyCard(
                    text = copyCardText!!,
                    xml = copyCardXml,
                    onDismiss = { copyCardText = null },
                )
            }
        }
        // ── 消息定位弹窗（2026-09-09 从消息区提升到页面根层：scrim 全屏覆盖顶栏与状态栏） ──
        if (locatorOpen) {
            Box(Modifier.fillMaxSize().zIndex(3f)) {
                val total = chatState.currentMessages.size
                val windowStart = chatState.messageWindowStart(
                    chatState.currentSessionId,
                    chatState.currentMessages,
                    listViewportDp,
                )
                MessageLocatorDialog(
                    messages = chatState.currentMessages,
                    listState = messagesListState,
                    windowStart = windowStart,
                    onDismiss = { locatorOpen = false },
                    onJump = { idx ->
                        // 跳转必须用 ChatScreen 根层的 scope：弹窗内 own scope 会随
                        // onDismiss 一起被取消，animateScrollToItem 启动即中止（点条目不跳转的根因）。
                        // 先关弹窗再滚动，跳转动画在聊天列表上完整可见。
                        // 目标可能是被窗口挡住的更早消息：先把它纳入窗口再滚（2026-09-12）
                        chatState.ensureMessageVisible(
                            chatState.currentSessionId,
                            chatState.currentMessages,
                            listViewportDp,
                            idx,
                        )
                        val start = chatState.messageWindowStart(
                            chatState.currentSessionId,
                            chatState.currentMessages,
                            listViewportDp,
                        )
                        val row = idx - start + (if (start > 0) 1 else 0)
                        locatorOpen = false
                        scope.launch { messagesListState.animateScrollToItem(row.coerceAtLeast(0)) }
                    },
                )
            }
        }
    }
}

// 宿主已冻结（2026-09-14）：原「pi 宿主未就绪提示条」随宿主一并删除

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
                    Icon(Icons.Outlined.Menu, L.chat.sessionDrawer, tint = MaterialTheme.colorScheme.onBackground)
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
            Icon(Icons.Outlined.Menu, L.chat.sessionDrawer, tint = MaterialTheme.colorScheme.onBackground)
        }
        // 中：会话标题 + 运行状态徽标
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            val title = chatState.currentSession?.title ?: L.chat.newSession
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
                    StatusBadge(L.common.running, MaterialTheme.colorScheme.primary)
                } else {
                    StatusBadge(L.chat.idle, MaterialTheme.colorScheme.onSurfaceVariant)
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
                Icons.Outlined.AccountTree, L.chat.sessionBranches,
                tint = if (active == Panel.TREE) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground,
            )
        }
        IconButton(onClick = { onPanel(Panel.TERMINAL) }) {
            Icon(
                Icons.Outlined.Terminal, L.chat.terminal,
                tint = if (active == Panel.TERMINAL) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground,
            )
        }
        IconButton(onClick = { onPanel(Panel.FILES) }) {
            Icon(
                Icons.Outlined.Description, L.common.file,
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
    /** 首屏数据是否已读盘完成（关掉开屏动画时用来压住引导清单误闪） */
    startupReady: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    listState: LazyListState,
    /** 列表可视高度（dp）——长会话窗口按它折算「几屏内容」 */
    viewportDp: Float,
    bottomInset: Dp = 0.dp,
    onOpenLocator: () -> Unit,
    onMessageLongPress: (MessageMenuTarget) -> Unit,
    onConfigureAi: () -> Unit,
    onOpenEnvSetup: () -> Unit,
) {
    if (!startupReady) {
        // 首屏数据未就绪：先留空（等待期间由开屏页盖住；关掉开屏动画时也不显示引导清单，
        // 否则会先闪一屏「创建项目 / 配置 AI」再跳回真实会话）
        return
    }
    if (chatState.currentProject == null || !chatState.aiConfigured) {
        // 2026-09-08 用户定：项目与 AI 配置两者齐备前，消息区显示引导清单
        // （任一未完成即显示，已完成步骤打勾提示）
        // **环境（pi 就绪）不参与这条判定**（2026-09-15 修）：`piReadiness` 冷启动时是 Unknown，
        // 拿它当门会「每次打开都先闪一屏引导」；而且 pi 起不来时会把用户已有的会话整个顶掉。
        // 环境问题的唯一出口 = 聊天页上方那条就绪条（PiUnreadyStrip：说明 + 重试 + 环境配置，
        // 并负责拦发送、保住草稿）；引导清单里那一步只是给「全新用户」看的清单项。
        FirstRunGuide(
            chatState = chatState,
            onConfigureAi = onConfigureAi,
            onOpenEnvSetup = onOpenEnvSetup,
        )
        return
    }
    ChatMessages(
        messages = chatState.currentMessages,
        isStreaming = chatState.isStreaming,
        streamDraft = chatState.streamDraft,
        listState = listState,
        // 思考模式：流式思考实时预览（Hermes 口径的「思考中」+ 正文贴底）+ 刚流式完的块保持展开。
        // 展示侧同样按开关门控（数据侧 runChat 已门控；此处兜住「流式途中关掉思考模式」——
        // 关掉即不再显示，且同一口径：思考模式关闭 = 思考内容永不上屏）。
        streamThinking = if (chatState.thinkingEnabled) chatState.streamThinking else "",
        streamThinkingStartedAt = if (chatState.thinkingEnabled) chatState.streamThinkingStartedAt else 0L,
        liveThinkingIndex = chatState.liveThinkingIndex,
        bottomInset = bottomInset,
        // 长会话上屏窗口：按**内容高度**计价（≈2 屏内容，早于它的靠「显示更早的消息」翻页）
        startIndex = chatState.messageWindowStart(
            chatState.currentSessionId,
            chatState.currentMessages,
            viewportDp,
        ),
        onShowEarlier = {
            chatState.showEarlierMessages(
                chatState.currentSessionId,
                chatState.currentMessages,
                viewportDp,
            )
        },
        onOpenLocator = onOpenLocator,
        onMessageLongPress = onMessageLongPress,
    )
}

/**
 * pi 运行时就绪条（2026-09-15 用户拍板 B：pi 是**唯一产品路径**）。
 *
 * 未就绪时聊天页必须**明确阻断**（不再静默改走那条没有工具能力的直连内核），
 * 并给一眼可见的修复入口：「环境配置」= Ubuntu / pi 的检测与安装页；「重试」= 重测就绪态
 * （通道没起时顺带起一次）。
 */
@Composable
private fun PiUnreadyStrip(
    reason: String,
    onRetry: () -> Unit,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            Icons.Outlined.WarningAmber, null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                L.chat.piUnready,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                reason.ifBlank { L.chat.piUnreadyHint },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            L.chat.retry,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onRetry)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
        Text(
            L.common.environmentConfig,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onFix)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}
