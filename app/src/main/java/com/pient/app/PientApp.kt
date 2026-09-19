package com.pient.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.widget.Toast
import android.os.SystemClock
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pient.app.data.AiConfigStore
import com.pient.app.data.ApkDownloader
import com.pient.app.data.AppUpdate
import com.pient.app.data.ChatState
import com.pient.app.data.ChatStore
import com.pient.app.data.ModelPricingDefaults
import com.pient.app.data.SettingsStore
import com.pient.app.data.ThemeMode
import com.pient.app.data.UpdateCenter
import com.pient.app.data.UsageStore
import com.pient.app.data.i18n.L
import com.pient.app.ui.chat.ChatScreen
import com.pient.app.ui.onboarding.OnboardingScreen
import com.pient.app.ui.plugins.PluginsScreen
import com.pient.app.ui.settings.AboutScreen
import com.pient.app.ui.settings.BehaviorSettingsScreen
import com.pient.app.ui.settings.ChangelogScreen
import com.pient.app.ui.settings.LanguageSettingsScreen
import com.pient.app.ui.settings.LogManagementScreen
import com.pient.app.ui.settings.LogViewerScreen
import com.pient.app.ui.settings.ModelConfigScreen
import com.pient.app.ui.settings.ProjectManagementScreen
import com.pient.app.ui.settings.SettingsScreen
import com.pient.app.ui.settings.SystemPermissionScreen
import com.pient.app.ui.settings.ThemeSettingsScreen
import com.pient.app.ui.settings.UpdateDialog
import com.pient.app.ui.settings.UsageScreen
import com.pient.app.ui.skills.SkillSearchScreen
import com.pient.app.ui.skills.SkillsScreen
import com.pient.app.ui.startup.StartupOverlay
import com.pient.app.ui.terminal.TerminalSetupScreen
import com.pient.app.ui.theme.AppBackgroundLayer
import com.pient.app.ui.theme.PientGlassProvisioning
import com.pient.app.ui.theme.PientTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext

/**
 * 导航骨架：
 * - onboarding：首启引导（仅首次启动/数据清除后，SharedPreferences 门控）
 * - chat：聊天主页（顶栏常驻，下方区域承载消息/文件/终端）
 * - skills / skill_search / plugins / settings / model_config /
 *   theme_settings / language_settings：侧栏与设置页二级页面
 * 主题：外观模式三选（深色/亮色/跟随系统）即时生效；ChatState 提升到
 * 导航外层，跨页面保活（侧栏切页返回后聊天状态不丢）。
 *
 * ★ 主题一致性三保障（"亮色下页面仍黑 / 黑字不可见"）：
 * 1. 页面底色统一在本组件根部绘制 colorScheme.background —— 所有路由页面
 *    都不依赖窗口底色（android:windowBackground 仅作首帧兜底）；
 * 2. 窗口底色随主题动态同步，亮色模式下启动/转场不露出暗色底；
 * 3. 明暗状态只来自 Compose 状态（themeMode + 系统 isSystemInDarkTheme），
 *    不使用 SideEffect + 全局变量同步（会滞后一帧）。
 */
@Composable
fun PientApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pient_prefs", Context.MODE_PRIVATE) }
    var onboarded by remember { mutableStateOf(prefs.getBoolean("onboarded", false)) }
    // ★ 启动加载：读盘不能放在组合期同步做——state.json（505KB 量级）+ 价格表资产
    //   + AI 配置 + 用量台账会把主线程占住 → 系统启动画面一直挂到第一帧。
    //   做法：① ChatState 与「已加载」标记提到进程级（Activity 重建不重走开屏、也不重读盘）；
    //   ② 读盘整体下放 IO 线程；③ 加载期间由 StartupOverlay 覆盖（含数据未就绪时
    //   「创建项目 / 配置 AI」引导的误闪）。
    val chatState = remember {
        PientRuntime.chatState ?: ChatState().also { PientRuntime.chatState = it }
    }
    var ready by remember { mutableStateOf(PientRuntime.dataLoaded) }
    LaunchedEffect(Unit) {
        // 读盘闸门：Activity 重建 / 双重组合会让这段 LaunchedEffect 跑两次，
        // 若只在读盘**完成后**才置 dataLoaded —— 第二次进来又读一遍，同一份数据落两遍
        // （state.json 的 entries 会翻倍）。所以：谁认领谁读盘，其余调用者等同一份
        // 结果（顺带避免"读盘未完成就用空状态写盘"的事故）。
        if (PientRuntime.claimLoad()) {
            val startedAt = SystemClock.uptimeMillis()
            withContext(Dispatchers.IO) {
                // 内置模型价格表（assets/model_pricing.tsv）
                ModelPricingDefaults.load(context)
                // 会话记录与 AI 配置恢复（项目/会话/消息记录跨重启保留）
                AiConfigStore.load(context)
                ChatStore.load(context, chatState)
                // 用量台账恢复（模型用量信息页的真实数据源）
                UsageStore.load(context)
            }
            PientRuntime.finishLoad()
            // 最短展示：读盘可能百毫秒内完成，过短会像「闪一下」。
            // 行为设置里关掉「开屏动画」时不做这层等待——不显示开屏页，读盘完就直接进主界面。
            if (SettingsStore.startupAnimation) {
                val elapsed = SystemClock.uptimeMillis() - startedAt
                if (elapsed < STARTUP_MIN_SHOW_MS) delay(STARTUP_MIN_SHOW_MS - elapsed)
            }
        } else {
            PientRuntime.awaitLoad()   // 别的组合正在读盘：等它读完再放行，别用空状态写盘
        }
        ready = true
    }

    // 应用上下文注入（内核各层从这里取 context；对话 / 压缩等都要它）
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        AppCtx.set(context.applicationContext)
        // 工作区指针：**必须在 AppCtx 注入之后**（读盘阶段 AppCtx 还是 null，那次调用会跳过）
        chatState.syncWorkspaceToCurrentProject()
    }

    // 通知点开回终端页：前台服务通知把面板请求留在 PientRuntime.pendingPanel
    // （通知到达时 ChatState 还没建），这里就绪后消费一次。**key 里必须带 pendingPanel 本身**：
    // 应用已在前台时只有它会变（见 PientRuntime.pendingPanel 注释）。
    LaunchedEffect(chatState, ready, PientRuntime.pendingPanel) {
        val panel = PientRuntime.pendingPanel ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        PientRuntime.pendingPanel = null
        if (panel == com.pient.app.runtime.PiKeepAlive.PANEL_TERMINAL) {
            chatState.activePanel = com.pient.app.data.Panel.TERMINAL
        }
    }
    val nav = rememberNavController()

    // 外观模式（深色 / 亮色 / 跟随系统）—— 状态感知，切换即时生效
    val systemDark = isSystemInDarkTheme()
    val dark = when (SettingsStore.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    // 主题选择持久化（重启后保持深色/亮色/跟随系统、主题色、自定义主题色与界面方案）
    LaunchedEffect(Unit) {
        snapshotFlow {
            SettingsStore.themeMode to SettingsStore.accent to SettingsStore.darkScheme to
                SettingsStore.lightScheme to SettingsStore.customAccentEnabled to
                SettingsStore.customAccentHue
        }.collect { SettingsStore.saveTheme(context) }
    }

    // 文件预览页设置持久化（行为设置），重启后保持
    LaunchedEffect(Unit) {
        snapshotFlow { SettingsStore.filePreviewNoWrap }
            .collect { SettingsStore.saveFilePreviewNoWrap(context) }
    }

    // 抽屉展出方式持久化（行为设置），重启后保持
    LaunchedEffect(Unit) {
        snapshotFlow { SettingsStore.drawerMode }
            .collect { SettingsStore.saveDrawerMode(context) }
    }

    // 权限档位持久化（系统权限设置页 / 首启引导页选定），重启后保持
    LaunchedEffect(Unit) {
        snapshotFlow { SettingsStore.permissionTier }
            .collect { SettingsStore.savePermissionTier(context) }
    }

    // 执行环境 + 环境内软件选择持久化（环境配置页），重启后保持
    LaunchedEffect(Unit) {
        snapshotFlow {
            Triple(SettingsStore.execEnv, SettingsStore.aptMirror, SettingsStore.selectedComponents)
        }.collect { SettingsStore.saveEnvironment(context) }
    }

    // 开屏设置持久化（行为设置：是否播放开屏加载动画），重启后保持
    LaunchedEffect(Unit) {
        snapshotFlow { SettingsStore.startupAnimation }
            .collect { SettingsStore.saveStartupAnimation(context) }
    }

    // 后台保活设置持久化（行为设置：后台常驻通知 —— 应用启动时按它重挂常驻前台服务），重启后保持
    LaunchedEffect(Unit) {
        snapshotFlow { SettingsStore.residentNotification }
            .collect { SettingsStore.saveResidentNotification(context) }
    }

    // 消息通知设置持久化（行为设置：通知 / 提示音 / 震动），重启后保持
    LaunchedEffect(Unit) {
        snapshotFlow {
            Triple(SettingsStore.replyNotify, SettingsStore.replyNotifySound, SettingsStore.replyNotifyVibrate)
        }.collect { SettingsStore.saveReplyNotify(context) }
    }

    // 更新检查设置持久化（关于页：启动自动检查 + 更新源），重启后保持
    LaunchedEffect(Unit) {
        snapshotFlow { SettingsStore.updateAutoCheck to SettingsStore.updateSource }
            .collect { SettingsStore.saveUpdateSettings(context) }
    }

    // 开屏自动检查更新（关于页设置开着时才查；查到更新的版本才弹窗，本次运行只查一次）
    LaunchedEffect(ready) {
        if (ready) UpdateCenter.autoCheckIfEnabled(context)
    }

    // 输入框设置持久化（样式 + 材质 + 透明度/纹理强度），重启后保持
    LaunchedEffect(Unit) {
        snapshotFlow {
            SettingsStore.inputBarStyle to SettingsStore.inputBarMaterial to
                SettingsStore.inputBarTransparency to SettingsStore.inputBarFrostIntensity
        }.collect { SettingsStore.saveInputBar(context) }
    }

    // 侧边栏设置持久化（样式 + 材质 + 透明度/纹理强度），重启后保持
    LaunchedEffect(Unit) {
        snapshotFlow {
            SettingsStore.sidebarStyle to SettingsStore.sidebarMaterial to
                SettingsStore.sidebarTransparency to SettingsStore.sidebarFrostIntensity
        }.collect { SettingsStore.saveSidebar(context) }
    }

    // 背景设置持久化（媒体类型/图片/视频/模糊/亮度/视频播放），重启后保持
    LaunchedEffect(Unit) {
        snapshotFlow {
            SettingsStore.backgroundMediaType to SettingsStore.backgroundImageUri to
                SettingsStore.backgroundVideoUri to SettingsStore.backgroundBlurEnabled to
                SettingsStore.backgroundBlurRadius to SettingsStore.backgroundBrightness to
                SettingsStore.videoBackgroundMuted to SettingsStore.videoBackgroundLoop to
                SettingsStore.videoTrimStartSec to SettingsStore.videoTrimEndSec to
                SettingsStore.videoCropMode to SettingsStore.videoPlaybackSpeed
        }.collect { SettingsStore.saveBackground(context) }
    }

    // 字体设置持久化（来源/内置字体/自定义字体文件/字号），重启后保持
    LaunchedEffect(Unit) {
        snapshotFlow {
            SettingsStore.fontSource to SettingsStore.builtinFontName to
                SettingsStore.customFontPath to SettingsStore.customFontLabel to
                SettingsStore.fontSize
        }.collect { SettingsStore.saveFont(context) }
    }

    // AI 配置持久化（服务商/密钥/模型/参数 + 连接测试标记；模型定价与汇率），重启后保持。
    // ★ 必须 gate 在 ready 之后：AiConfigStore 是异步读盘，
    //   snapshotFlow 一旦先启动就会把「尚未读盘的空配置」当成初值写盘 → 把用户配置清空。
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        snapshotFlow {
            listOf(
                AiConfigStore.configs.mapValues { it.value },
                AiConfigStore.aiConfigured,
                AiConfigStore.pricing.toMap(),
                AiConfigStore.usdToCnyRate,
            )
        }.collect { AiConfigStore.save(context) }
    }

    // 项目会话记录持久化（项目/会话/消息记录全量落盘），重启后保持。
    // snapshotFlow 内遍历全部会话与消息：任意增删改都会触发；写盘放 IO 线程防卡 UI。
    // ★ 同样 gate 在 ready 之后：否则空 ChatState 会被先写盘（同上事故）。
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        snapshotFlow {
            chatState.projects.toList() to
                chatState.sessions.mapValues { it.value.toList() } to
                chatState.messagesBySession.mapValues { it.value.toList() } to
                chatState.entriesBySession.mapValues { it.value.toList() } to
                chatState.leafBySession.toMap() to
                chatState.piDesiredLeaf.toMap() to
                (chatState.currentProject to chatState.currentSessionId) to
                chatState.selectedModelId to
                (chatState.thinkingEnabled to chatState.thinkingLevel)
        }.debounce(800).collect {
            withContext(Dispatchers.IO) { ChatStore.save(context, chatState) }
        }
    }

    // 用量台账持久化（每次回复记一笔，防抖落盘 usage.json）。★ 同样 gate 在 ready 之后
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        snapshotFlow { UsageStore.records.toList() }.debounce(800).collect {
            withContext(Dispatchers.IO) { UsageStore.save(context) }
        }
    }

    // 系统栏图标明暗 + 窗口底色跟随 App 主题
    // （亮色主题下若沿用暗色窗口底/白色状态栏图标，会出现"页面黑块、看不清"）
    val view = LocalView.current
    val windowBgArgb = MaterialTheme.colorScheme.background.toArgb() // SideEffect 非 Composable，先解析
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        activity.window.setBackgroundDrawable(ColorDrawable(windowBgArgb))
    }

    PientTheme(darkTheme = dark, accent = SettingsStore.accentFor(dark), scheme = SettingsStore.schemeFor(dark)) {
        // ★ 全屏页面底色：亮色模式下页面整体变白、暗色模式下变黑，
        // 卡片/消息气泡等容器在底色之上用各自的 surface 令牌分层。
        // 自定义背景（背景设置标签）作为最底层覆盖其上（未设置时不绘制，底色保持）。
        //
        // ★ 玻璃材质基础设施（输入框设置「输入框材质」）：
        // 底色 + 背景层放进「背景捕获层」，应用内容与其同级 —— 输入栏的磨砂/液态玻璃
        // 从中采样背景纹理；若把内容放进捕获层会造成渲染树自引用（红线）。
        PientGlassProvisioning(
            backgroundContent = {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                AppBackgroundLayer()
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = nav,
                    startDestination = if (onboarded) "chat" else "onboarding",
                    // 页面级路由 = 水平推入：新页从右滑入、旧页向左让位；
                    // 返回时旧页从左滑回、当前页向右退出。API 34+ 自动支持预测性返回手势。
                    enterTransition = { slideInHorizontally(tween(300)) { it } },
                    exitTransition = { slideOutHorizontally(tween(300)) { -it } },
                    popEnterTransition = { slideInHorizontally(tween(300)) { -it } },
                    popExitTransition = { slideOutHorizontally(tween(300)) { it } },
                ) {
                    composable("onboarding") {
                        OnboardingScreen(onDone = {
                            prefs.edit().putBoolean("onboarded", true).apply()
                            nav.navigate("chat") { popUpTo(0) { inclusive = true } }
                        })
                    }
                    composable("chat") {
                        ChatScreen(chatState = chatState, nav = nav, startupReady = ready)
                    }
                    composable("skills") {
                        SkillsScreen(nav = nav)
                    }
                    composable("skill_search") {
                        SkillSearchScreen(nav = nav)
                    }
                    composable("plugins") {
                        PluginsScreen(nav = nav)
                    }
                    composable("settings") {
                        SettingsScreen(nav = nav)
                    }
                    composable("model_config") {
                        ModelConfigScreen(nav = nav, chatState = chatState)
                    }
                    composable("theme_settings") {
                        ThemeSettingsScreen(nav = nav)
                    }
                    composable("language_settings") {
                        LanguageSettingsScreen(nav = nav)
                    }
                    composable("behavior_settings") {
                        BehaviorSettingsScreen(nav = nav)
                    }
                    composable("terminal_setup") {
                        TerminalSetupScreen(nav = nav, chatState = chatState)
                    }
                    composable("usage") {
                        UsageScreen(nav = nav)
                    }
                    composable("project_management") {
                        ProjectManagementScreen(nav = nav, chatState = chatState)
                    }
                    composable("system_permissions") {
                        SystemPermissionScreen(nav = nav)
                    }
                    // 应用日志管理：导出/查看/清空（见 ui/settings/LogManagementScreen.kt）
                    composable("app_logs") {
                        LogManagementScreen(nav = nav)
                    }
                    composable("log_viewer") {
                        LogViewerScreen(nav = nav)
                    }
                    composable("about") {
                        AboutScreen(nav = nav)
                    }
                    composable("changelog") {
                        ChangelogScreen(nav = nav)
                    }
                }

                // 开屏加载层（最后渲染 = 在最上层）：首屏数据未就绪期间盖住下层界面。
                // 行为设置里关掉「开屏动画」时整层不渲染（数据仍在后台加载，主界面直接进入；见 ChatScreen 的 startupReady 门控）。
                StartupOverlay(visible = !ready && SettingsStore.startupAnimation)

                // 更新弹窗：与页面同级的浮层（开屏自动检查查到新版本时也在这里弹，与从关于页点进来是同一个）
                if (UpdateCenter.dialogVisible) {
                    val localVersion = remember { AppUpdate.localVersion(context)?.first }
                    UpdateDialog(
                        state = UpdateCenter.checkState,
                        downloadState = ApkDownloader.state,
                        installPermissionNeeded = UpdateCenter.installPermissionNeeded,
                        appVersion = localVersion ?: L.common.unknown,
                        onRetry = { UpdateCenter.check(context) },
                        onDownload = { mirrorUrl -> UpdateCenter.startDownload(context, mirrorUrl) },
                        onPause = { UpdateCenter.pauseDownload() },
                        onResume = { UpdateCenter.resumeDownload() },
                        onCancelDownload = { UpdateCenter.cancelDownload(context) },
                        onInstall = { path -> UpdateCenter.install(context, path) },
                        onOpenInstallSettings = { UpdateCenter.openInstallPermissionSettings(context) },
                        onOpenDownloadPage = { url ->
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        onDismiss = { UpdateCenter.dismissDialog() },
                    )
                }
            }
        }
    }
}

/**
 * 进程级运行时（Activity 重建不丢）：
 * - chatState：首屏数据只读一次盘，重建时复用同一个 ChatState 实例；
 * - dataLoaded：仅「本进程首次组合」需要走开屏加载页（重建/回前台不再闪开屏）。
 */
/** 进程级运行时状态（Activity 重建不重走开屏/不重读盘）；前台服务也读它，故为 internal */
internal object PientRuntime {
    var chatState: ChatState? = null
    var dataLoaded = false

    /**
     * 待消费的「打开哪个面板」请求：前台服务通知点开时由 MainActivity 写入，
     * PientApp 在 ChatState 就绪后消费一次（通知到达时 ChatState 可能还没建，所以先存着）。
     * 值 = [com.pient.app.runtime.PiKeepAlive.PANEL_TERMINAL] 等。
     *
     * **必须是 Compose 可观察状态**：应用已经在前台时点通知只会触发 onNewIntent（chatState/ready
     * 都没变），普通变量不会让消费用的 LaunchedEffect 重跑 —— 面板就切不过去。
     */
    var pendingPanel by androidx.compose.runtime.mutableStateOf<String?>(null)

    /**
     * 应用是否在前台可见：MainActivity 的 onStart/onStop 维护。
     * 用途 =「保活被系统停掉」这类说明挑渠道：前台用 Toast（用户正看着屏幕），后台发通知。
     */
    @Volatile
    var appVisible = false

    /** 读盘闸门：并发/重复组合只允许一次读盘，其余 await 同一份结果 */
    private val lock = Any()
    private var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    /** @return true = 本次调用负责读盘；false = 已有别的调用在读（应 [awaitLoad]） */
    fun claimLoad(): Boolean = synchronized(lock) {
        if (dataLoaded) return false
        if (gate == null) {
            gate = kotlinx.coroutines.CompletableDeferred()
            true
        } else false
    }

    suspend fun awaitLoad() {
        gate?.await()
    }

    fun finishLoad() {
        synchronized(lock) {
            dataLoaded = true
            gate?.complete(Unit)
        }
    }
}

/** 开屏加载页最短展示时长（读盘过快时避免「闪一下」） */
private const val STARTUP_MIN_SHOW_MS = 500L

