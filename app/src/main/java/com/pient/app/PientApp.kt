package com.pient.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pient.app.data.AiConfigStore
import com.pient.app.data.ChatState
import com.pient.app.data.ChatStore
import com.pient.app.data.ModelPricingDefaults
import com.pient.app.data.SettingsStore
import com.pient.app.data.ThemeMode
import com.pient.app.data.UsageStore
import com.pient.app.runtime.PiHostService
import com.pient.app.runtime.PiRuntime
import com.pient.app.runtime.PiHost
import com.pient.app.ui.chat.ChatScreen
import com.pient.app.ui.onboarding.OnboardingScreen
import com.pient.app.ui.plugins.PluginsScreen
import com.pient.app.ui.settings.AboutScreen
import com.pient.app.ui.settings.BehaviorSettingsScreen
import com.pient.app.ui.settings.LanguageSettingsScreen
import com.pient.app.ui.settings.ModelConfigScreen
import com.pient.app.ui.settings.ProjectManagementScreen
import com.pient.app.ui.settings.SettingsScreen
import com.pient.app.ui.settings.SystemPermissionScreen
import com.pient.app.ui.settings.ThemeSettingsScreen
import com.pient.app.ui.settings.UsageScreen
import com.pient.app.ui.skills.SkillSearchScreen
import com.pient.app.ui.skills.SkillsScreen
import com.pient.app.ui.startup.StartupOverlay
import com.pient.app.ui.terminal.TerminalSetupScreen
import com.pient.app.ui.theme.AppBackgroundLayer
import com.pient.app.ui.theme.PientGlassProvisioning
import com.pient.app.ui.theme.PientTheme
import kotlinx.coroutines.Dispatchers
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
 * ★ 主题一致性三保障（2026-08-27 修复"亮色下页面仍黑 / 黑字不可见"）：
 * 1. 页面底色统一在本组件根部绘制 colorScheme.background —— 所有路由页面
 *    都不再依赖窗口底色（android:windowBackground 仅作首帧兜底）；
 * 2. 窗口底色随主题动态同步，亮色模式下启动/转场不露出暗色底；
 * 3. 明暗状态只来自 Compose 状态（themeMode + 系统 isSystemInDarkTheme），
 *    不使用 SideEffect + 全局变量同步（会滞后一帧，历史教训见 Theme.kt）。
 */
@Composable
fun PientApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pient_prefs", Context.MODE_PRIVATE) }
    var onboarded by remember { mutableStateOf(prefs.getBoolean("onboarded", false)) }
    // ★ 启动加载（2026-09-12）：原实现在**组合期同步读盘**（state.json 实测 505KB + 价格表资产
    //   + AI 配置 + 用量台账），主线程被占住 → 系统启动画面一直挂到第一帧，实测停留 3.6s。
    //   现改为：① ChatState 与「已加载」标记提到进程级（Activity 重建不重走开屏、也不重读盘）；
    //   ② 读盘整体下放 IO 线程；③ 加载期间由 StartupOverlay 覆盖（含数据未就绪时
    //   「创建项目 / 配置 AI」引导的误闪）。
    val chatState = remember {
        PientRuntime.chatState ?: ChatState().also { PientRuntime.chatState = it }
    }
    var ready by remember { mutableStateOf(PientRuntime.dataLoaded) }
    LaunchedEffect(Unit) {
        if (!PientRuntime.dataLoaded) {
            val startedAt = SystemClock.uptimeMillis()
            withContext(Dispatchers.IO) {
                // 内置模型价格表（assets/model_pricing.tsv，Operit 式内置定价）
                ModelPricingDefaults.load(context)
                // 会话记录与 AI 配置恢复（2026-09-09：项目/会话/消息记录跨重启保留）
                AiConfigStore.load(context)
                ChatStore.load(context, chatState)
                // 用量台账恢复（2026-09-11：模型用量信息页的真实数据源）
                UsageStore.load(context)
            }
            PientRuntime.dataLoaded = true
            // 最短展示：真机读盘可能百毫秒内完成，过短会像「闪一下」。
            // 行为设置里关掉「开屏动画」时不做这层等待——不显示开屏页，读盘完就直接进主界面。
            if (SettingsStore.startupAnimation) {
                val elapsed = SystemClock.uptimeMillis() - startedAt
                if (elapsed < STARTUP_MIN_SHOW_MS) delay(STARTUP_MIN_SHOW_MS - elapsed)
            }
        }
        ready = true
    }

    // pi 宿主（工具层底座）：数据加载完成后交给**前台服务**托管（开发计划 §6.4 保活）——
    // 常驻通知「Agent 运行中」+ START_STICKY，宿主（Node 子进程）随服务存活；
    // 服务内部做模型接线（需要服务商/模型配置，所以不能放在 Activity.onCreate）。
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        // Android 13+：没通知权限时前台服务照常跑，但用户看不到「Agent 运行中」→ 顺手要一次
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        PiHostService.start(context.applicationContext)
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

    // 当前项目 → agent 工作区（bash 工具的 cwd 与 Ubuntu 里的 /workspace 同一处）；
    // SAF 项目没有可给 agent 的文件系统路径 → 保持随包工作区（见 PiRuntime.setWorkspace）
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        snapshotFlow { chatState.currentProject to chatState.projects.toList() }
            .collect { (name, projects) ->
                val proj = projects.firstOrNull { it.name == name }
                PiRuntime.setWorkspaceForProject(context, proj?.path, proj?.uri != null)
            }
    }

    // 权限档位持久化（系统权限设置页 / 首启引导页选定），重启后保持
    LaunchedEffect(Unit) {
        snapshotFlow { SettingsStore.permissionTier }
            .collect { SettingsStore.savePermissionTier(context) }
    }

    // 开屏设置持久化（行为设置：是否播放开屏加载动画），重启后保持
    LaunchedEffect(Unit) {
        snapshotFlow { SettingsStore.startupAnimation }
            .collect { SettingsStore.saveStartupAnimation(context) }
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

    // AI 配置持久化（2026-09-09：服务商/密钥/模型/参数 + 连接测试标记；2026-09-11 加模型定价与汇率），重启后保持。
    // ★ 必须 gate 在 ready 之后（2026-09-12 实测事故）：AiConfigStore 现在是异步读盘，
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

    // 项目会话记录持久化（2026-09-09：项目/会话/消息记录全量落盘），重启后保持。
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
                (chatState.currentProject to chatState.currentSessionId) to
                chatState.selectedModelId to
                (chatState.thinkingEnabled to chatState.thinkingLevel to
                    chatState.streamingOutputEnabled)
        }.debounce(800).collect {
            withContext(Dispatchers.IO) { ChatStore.save(context, chatState) }
        }
    }

    // 用量台账持久化（2026-09-11：每次回复记一笔，防抖落盘 usage.json）。★ 同样 gate 在 ready 之后
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
        // ★ 玻璃材质基础设施（2026-09-12，输入框设置「输入框材质」）：
        // 底色 + 背景层放进「背景捕获层」，应用内容与其同级 —— 输入栏的磨砂/液态玻璃
        // 从中采样背景纹理；若把内容放进捕获层会造成渲染树自引用（Mdcito 同款红线）。
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
                    // 页面级路由 = 水平推入（2026-08-27）：新页从右滑入、旧页向左让位；
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
                        TerminalSetupScreen(nav = nav)
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
                    composable("about") {
                        AboutScreen(nav = nav)
                    }
                }

                // 开屏加载层（最后渲染 = 在最上层）：首屏数据未就绪期间盖住下层界面。
                // 行为设置里关掉「开屏动画」时整层不渲染（数据仍在后台加载，主界面直接进入；见 ChatScreen 的 startupReady 门控）。
                StartupOverlay(visible = !ready && SettingsStore.startupAnimation)
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

    /** pi 宿主是否已拉起过（进程级；Activity 重建不重复拉起） */
    var hostStarted = false
}

/** 开屏加载页最短展示时长（读盘过快时避免「闪一下」） */
private const val STARTUP_MIN_SHOW_MS = 500L

