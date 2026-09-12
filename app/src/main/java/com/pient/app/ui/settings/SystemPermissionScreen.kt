package com.pient.app.ui.settings

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.pient.app.data.PermissionTier
import com.pient.app.data.RootGateway
import com.pient.app.data.SettingsStore
import com.pient.app.data.ShizukuGateway
import com.pient.app.data.SystemPermissions
import com.pient.app.data.ToolPolicy
import com.pient.app.ui.components.ArcSpinner
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.components.PientSegmented
import com.pient.app.ui.components.SectionHeader
import com.pient.app.ui.components.StatusBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * 系统权限设置页（设计计划 P8 权限中心 / 开发计划 6.1 三级权限体系）
 *
 * 结构（照 Operit `PermissionLevelCard` + `ShizukuDemoScreen` 对齐，视觉走 Pient 卡片令牌）：
 *  ① 权限档位卡：档位分段选择（标准 / 调试 / Root，可预览）→ 档位说明 → 「设为当前档位」/「当前使用中」
 *     → 该档位对应的权限清单（基础权限四项 + 档位专属项，逐项实时状态、点击直达授权）；
 *  ② 设置向导（当前档位未就绪时出现）：Shizuku 三步 / Root 两步的渐进引导。
 *
 * 全部状态都是**真实检测**（无 mock）：基础权限四项读系统授权状态（与首启引导页共用
 * [SystemPermissions] 一份实现）；Shizuku 走官方 SDK（installed / running / authorized 三态）；
 * Root 走 su 通道检测。连接/授权入口一律跳系统页或官方应用，不在本页伪造结果。
 */
@Composable
fun SystemPermissionScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── 实时状态 ──
    var status by remember { mutableStateOf(SystemPermissions.status(context)) }
    var shizukuInstalled by remember { mutableStateOf(ShizukuGateway.installed(context)) }
    var shizukuRunning by remember { mutableStateOf(ShizukuGateway.running()) }
    var shizukuAuthorized by remember { mutableStateOf(ShizukuGateway.authorized()) }
    var deviceRooted by remember { mutableStateOf(RootGateway.deviceRooted(context)) }
    var rootGranted by remember { mutableStateOf(false) }
    var rootProbed by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var rootRequesting by remember { mutableStateOf(false) }

    // 展示中的档位（可预览，与「当前生效档位」分离，同 Operit displayedPermissionLevel / preferredPermissionLevel）
    var displayed by remember { mutableStateOf(SettingsStore.permissionTier) }
    val activeTier = SettingsStore.permissionTier

    /** 读取全部真实状态（binder / 文件系统查询放 IO 线程） */
    suspend fun readStatus() = withContext(Dispatchers.IO) {
        status = SystemPermissions.status(context)
        shizukuInstalled = ShizukuGateway.installed(context)
        shizukuRunning = ShizukuGateway.running()
        shizukuAuthorized = ShizukuGateway.authorized()
        deviceRooted = RootGateway.deviceRooted(context)
    }

    fun refresh() {
        scope.launch {
            refreshing = true
            readStatus()
            refreshing = false
        }
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    // 运行时权限回调（Android 10 及以下的存储权限 / 位置权限）：回来无条件重检
    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { scope.launch { readStatus() } }

    // ── 授权入口 ──
    fun grantStorage() {
        if (SystemPermissions.needsRuntimeStorage) {
            runtimeLauncher.launch(SystemPermissions.runtimeStoragePermissions)
        } else if (!SystemPermissions.openStorageSettings(context)) {
            toast("无法打开存储权限设置")
        }
    }

    fun grantOverlay() {
        if (!SystemPermissions.openOverlaySettings(context)) toast("无法打开悬浮窗权限设置")
    }

    fun grantBattery() {
        if (!SystemPermissions.openBatterySettings(context)) toast("无法打开电池优化设置")
    }

    fun grantLocation() {
        runtimeLauncher.launch(SystemPermissions.runtimeLocationPermissions)
    }

    /** Shizuku：按当前进度发对应动作（未装→下载页 / 未运行→打开应用 / 未授权→请求授权） */
    fun grantShizuku() {
        when {
            !shizukuInstalled -> if (!ShizukuGateway.openUrl(context, ShizukuGateway.DOWNLOAD_URL)) {
                toast("无法打开 Shizuku 下载页")
            }
            !shizukuRunning -> if (!ShizukuGateway.openApp(context)) {
                toast("无法打开 Shizuku 应用")
            }
            !shizukuAuthorized -> if (!ShizukuGateway.requestPermission()) {
                toast("请先在 Shizuku 应用中启动服务")
            }
        }
    }

    /** Root：真执行一次 su（首次触发 Root 管理器授权框） */
    fun requestRoot() {
        scope.launch {
            rootRequesting = true
            val granted = RootGateway.requestAccess()
            rootGranted = granted
            rootProbed = true
            if (granted) deviceRooted = true
            rootRequesting = false
            toast(if (granted) "已获得 Root 权限" else "未获得 Root 权限（设备未 Root 或授权被拒绝）")
        }
    }

    fun setActiveTier(tier: PermissionTier) {
        SettingsStore.permissionTier = tier
        val hint = when {
            tierReady(tier, status, shizukuInstalled, shizukuRunning, shizukuAuthorized, deviceRooted, rootGranted) -> null
            tier == PermissionTier.DEBUGGER -> "需先完成 Shizuku 安装与授权"
            tier == PermissionTier.ROOT -> "需设备已 Root 并授予 Pient 权限"
            else -> "基础权限未全部授权"
        }
        toast(if (hint == null) "已切换为「${tier.title}」" else "已切换为「${tier.title}」 · $hint")
    }

    // 进入页面即读一次；从系统设置/授权弹窗返回（ON_RESUME）自动重检
    LaunchedEffect(Unit) { readStatus() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch { readStatus() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Shizuku 授权结果回调（SDK 静态监听）
    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            shizukuAuthorized = granted
            toast(if (granted) "已获得 Shizuku 授权" else "Shizuku 授权被拒绝")
        }
        ShizukuGateway.addPermissionResultListener(listener)
        onDispose { ShizukuGateway.removePermissionResultListener(listener) }
    }

    Column(Modifier.fillMaxSize()) {
        // ── 顶栏（与其他设置二级页一致） ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Outlined.ArrowBack, "返回",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = { nav.popBackStack() }),
            )
            Text(
                "系统权限设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            // ═══════════ 权限档位卡 ═══════════
            SectionHeader("权限档位", icon = Icons.Outlined.AdminPanelSettings)
            PermissionCardBox {
                Column(Modifier.padding(14.dp)) {
                    // 卡头：图标 + 标题 + 刷新（刷新中换成旋转圆弧）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (displayed == PermissionTier.ROOT) Icons.Outlined.Lock else Icons.Outlined.Shield,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "权限档位",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable(enabled = !refreshing, onClick = { refresh() }),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (refreshing) {
                                ArcSpinner(size = 16.dp)
                            } else {
                                Icon(
                                    Icons.Outlined.Refresh, "刷新权限状态",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    CardDivider()
                    Spacer(Modifier.height(12.dp))

                    // 档位选择（三档，可预览）
                    PientSegmented(
                        labels = PermissionTier.values().map { it.title },
                        selected = displayed.ordinal,
                        onSelect = { displayed = PermissionTier.values()[it] },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(10.dp))
                    AnimatedContent(
                        targetState = displayed,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "tierDesc",
                    ) { tier ->
                        Text(
                            tier.desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // 设为当前档位 / 当前使用中
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (displayed != activeTier) {
                            PientButton(
                                "设为当前档位",
                                onClick = { setActiveTier(displayed) },
                                modifier = Modifier.widthIn(max = 200.dp),
                                height = 38,   // 与同页「设置向导」按钮同高
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.CheckCircle, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    "当前使用中",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    CardDivider()
                    Spacer(Modifier.height(6.dp))

                    // 该档位对应的权限清单
                    AnimatedContent(
                        targetState = displayed,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "tierPerms",
                    ) { tier ->
                        TierPermissionList(
                            tier = tier,
                            status = status,
                            shizukuInstalled = shizukuInstalled,
                            shizukuRunning = shizukuRunning,
                            shizukuAuthorized = shizukuAuthorized,
                            deviceRooted = deviceRooted,
                            rootGranted = rootGranted,
                            rootProbed = rootProbed,
                            onStorage = { grantStorage() },
                            onBattery = { grantBattery() },
                            onLocation = { grantLocation() },
                            onOverlay = { grantOverlay() },
                            onShizuku = { grantShizuku() },
                            onRoot = { requestRoot() },
                        )
                    }
                }
            }

            // ═══════════ 工具级授权（开发计划 §6.3：全局默认 + 单工具例外） ═══════════
            SectionHeader("工具级授权", icon = Icons.Outlined.Shield)
            PermissionCardBox {
                Column(Modifier.padding(14.dp)) {
                    var toolDefault by remember { mutableStateOf(ToolPolicy.ASK) }
                    var toolPolicies by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
                    var expandedPolicyKey by remember { mutableStateOf<String?>(null) }
                    fun reloadPolicies() {
                        val (d, m) = ToolPolicy.snapshot(context)
                        toolDefault = d
                        toolPolicies = m
                    }
                    fun applyToolPolicy(tool: String?, policy: String) {
                        if (tool == null) {
                            ToolPolicy.setDefault(context, policy)
                        } else {
                            ToolPolicy.setToolPolicy(context, tool, policy)
                        }
                        reloadPolicies()
                        expandedPolicyKey = null
                        toast("已设为「${policyLabel(policy)}」")
                    }
                    LaunchedEffect(Unit) { reloadPolicies() }

                    GroupLabel("调用策略", "权限守门扩展执行")
                    Text(
                        "AI 调用工具前按这里的策略执行：允许 = 直接执行；每次询问 = 弹三选授权；" +
                            "禁止 = 直接拦下并把原因回给模型。授权弹窗里的「始终允许」也写到这里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    ToolPolicyRow(
                        title = "默认策略",
                        desc = "未单独设置的工具都按它执行",
                        policy = toolDefault,
                        expanded = expandedPolicyKey == KEY_DEFAULT_POLICY,
                        onClick = {
                            expandedPolicyKey =
                                if (expandedPolicyKey == KEY_DEFAULT_POLICY) null else KEY_DEFAULT_POLICY
                        },
                        onPick = { p -> applyToolPolicy(null, p) },
                    )
                    ToolPolicy.TOOLS.forEach { tool ->
                        CardDivider()
                        ToolPolicyRow(
                            title = tool,
                            desc = TOOL_DESCS[tool].orEmpty(),
                            policy = toolPolicies[tool] ?: toolDefault,
                            expanded = expandedPolicyKey == tool,
                            onClick = {
                                expandedPolicyKey = if (expandedPolicyKey == tool) null else tool
                            },
                            onPick = { p -> applyToolPolicy(tool, p) },
                        )
                    }
                }
            }

            // ═══════════ 设置向导（档位未就绪时） ═══════════
            val wizardTier = when {
                tierReady(displayed, status, shizukuInstalled, shizukuRunning, shizukuAuthorized, deviceRooted, rootGranted) -> null
                displayed == PermissionTier.STANDARD -> null      // 标准档无外部组件，缺的只是上面的四项授权
                else -> displayed
            }
            if (wizardTier != null) {
                SectionHeader("设置向导", icon = Icons.Outlined.Build)
                PermissionCardBox {
                    Column(Modifier.padding(14.dp)) {
                        when (wizardTier) {
                            PermissionTier.DEBUGGER -> ShizukuWizard(
                                installed = shizukuInstalled,
                                running = shizukuRunning,
                                authorized = shizukuAuthorized,
                                onAction = { grantShizuku() },
                                onGuide = {
                                    if (!ShizukuGateway.openUrl(context, ShizukuGateway.GUIDE_URL)) {
                                        toast("无法打开 Shizuku 激活教程")
                                    }
                                },
                            )
                            else -> RootWizard(
                                rooted = deviceRooted,
                                granted = rootGranted,
                                requesting = rootRequesting,
                                onRequest = { requestRoot() },
                                onGuide = {
                                    if (!ShizukuGateway.openUrl(context, ROOT_GUIDE_URL)) {
                                        toast("无法打开 Root 教程")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────── 档位就绪判定 ───────────────────────────

/** 档位是否已具备生效条件（标准=基础权限四项齐备；调试=Shizuku 三步齐备；Root=已 Root 且已授权） */
private fun tierReady(
    tier: PermissionTier,
    status: SystemPermissions.Status,
    shizukuInstalled: Boolean,
    shizukuRunning: Boolean,
    shizukuAuthorized: Boolean,
    deviceRooted: Boolean,
    rootGranted: Boolean,
): Boolean = when (tier) {
    PermissionTier.STANDARD -> status.allReady
    PermissionTier.DEBUGGER -> shizukuInstalled && shizukuRunning && shizukuAuthorized
    PermissionTier.ROOT -> deviceRooted && rootGranted
}

// ─────────────────────────── 权限清单 ───────────────────────────

@Composable
private fun TierPermissionList(
    tier: PermissionTier,
    status: SystemPermissions.Status,
    shizukuInstalled: Boolean,
    shizukuRunning: Boolean,
    shizukuAuthorized: Boolean,
    deviceRooted: Boolean,
    rootGranted: Boolean,
    rootProbed: Boolean,
    onStorage: () -> Unit,
    onBattery: () -> Unit,
    onLocation: () -> Unit,
    onOverlay: () -> Unit,
    onShizuku: () -> Unit,
    onRoot: () -> Unit,
) {
    Column {
        GroupLabel("基础权限", "${status.readyCount}/${SystemPermissions.TOTAL}")
        PermissionStatusRow("存储权限", status.storage, onClick = onStorage)
        PermissionStatusRow("电池优化豁免", status.battery, onClick = onBattery)
        PermissionStatusRow("位置权限", status.location, onClick = onLocation)
        PermissionStatusRow("悬浮窗权限", status.overlay, onClick = onOverlay)

        when (tier) {
            PermissionTier.DEBUGGER -> {
                Spacer(Modifier.height(8.dp))
                GroupLabel("Shizuku 服务")
                PermissionStatusRow("已安装 Shizuku 应用", shizukuInstalled, pendingText = "去下载 →", onClick = onShizuku)
                PermissionStatusRow("服务运行中", shizukuRunning, pendingText = "去启动 →", onClick = onShizuku)
                PermissionStatusRow("已授权 Pient", shizukuAuthorized, pendingText = "去授权 →", onClick = onShizuku)
            }
            PermissionTier.ROOT -> {
                Spacer(Modifier.height(8.dp))
                GroupLabel("Root 通道")
                PermissionStatusRow(
                    "设备已 Root",
                    deviceRooted,
                    pendingText = "未检测到",
                    onClick = null,   // 设备是否 Root 由设备决定，本页只能如实显示
                )
                PermissionStatusRow(
                    "已授予 Pient su 权限",
                    rootGranted,
                    pendingText = if (rootProbed) "已拒绝" else "未验证",
                    onClick = onRoot,
                )
            }
            PermissionTier.STANDARD -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    "标准权限开箱即用：无需安装任何额外组件，四项基础权限齐备即可使用日常 Agent 能力。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 权限状态行（标题 + 右侧状态；未授权时整行可点直达授权页 —— 交互红线：整块可点） */
@Composable
private fun PermissionStatusRow(
    title: String,
    granted: Boolean,
    pendingText: String = "去授权 →",
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 9.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (granted) {
            Text(
                "已授权 ✓",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pendingText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    " ⚠",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ─────────────────────────── 设置向导 ───────────────────────────

private const val ROOT_GUIDE_URL = "https://github.com/topjohnwu/Magisk"

@Composable
private fun ShizukuWizard(
    installed: Boolean,
    running: Boolean,
    authorized: Boolean,
    onAction: () -> Unit,
    onGuide: () -> Unit,
) {
    WizardHeader(
        icon = Icons.Outlined.Build,
        title = "Shizuku 服务",
        badge = when {
            !installed -> "未安装" to MaterialTheme.colorScheme.error
            !running -> "未运行" to MaterialTheme.colorScheme.error
            !authorized -> "未授权" to MaterialTheme.colorScheme.error
            else -> "已就绪" to MaterialTheme.colorScheme.primary
        },
    )
    Text(
        "Shizuku 以 ADB 权限运行，无需解锁 Bootloader；设备重启后服务需要重新激活（无线调试配对或一次 ADB 授权），授权本身不会丢失。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
    Spacer(Modifier.height(10.dp))
    WizardStepRow(1, "安装 Shizuku 应用", installed, "去下载 →") { onAction() }
    WizardStepRow(2, "启动 Shizuku 服务", running, "打开 Shizuku →") { onAction() }
    WizardStepRow(3, "授权 Pient 使用 Shizuku", authorized, "请求授权 →") { onAction() }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PientButton(
            when {
                !installed -> "下载 Shizuku"
                !running -> "启动 Shizuku"
                else -> "请求授权"
            },
            onClick = onAction,
            modifier = Modifier.weight(1f),
            height = 38,
        )
        PientButton("查看教程", onClick = onGuide, primary = false, modifier = Modifier.weight(1f), height = 38)
    }
}

@Composable
private fun RootWizard(
    rooted: Boolean,
    granted: Boolean,
    requesting: Boolean,
    onRequest: () -> Unit,
    onGuide: () -> Unit,
) {
    WizardHeader(
        icon = Icons.Outlined.Lock,
        title = "Root 通道",
        badge = when {
            !rooted -> "未检测到 Root" to MaterialTheme.colorScheme.error
            !granted -> "未授权" to MaterialTheme.colorScheme.error
            else -> "已就绪" to MaterialTheme.colorScheme.primary
        },
    )
    Text(
        "Pient 通过 su 通道获得最高级系统能力（chroot 终端环境、系统级文件操作）。首次请求会由 Root 管理器（Magisk / KernelSU / APatch）弹出授权框；未 Root 的设备可继续使用标准 / 调试权限。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
    Spacer(Modifier.height(10.dp))
    WizardStepRow(1, "设备已获取 Root 权限", rooted, "查看教程 →") { onGuide() }
    WizardStepRow(2, "授予 Pient su 权限", granted, "请求授权 →") { onRequest() }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PientButton(
            "请求 Root 授权",
            onClick = onRequest,
            modifier = Modifier.weight(1f),
            loading = requesting,
            height = 38,
        )
        PientButton("查看教程", onClick = onGuide, primary = false, modifier = Modifier.weight(1f), height = 38)
    }
}

@Composable
private fun WizardHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, badge: Pair<String, androidx.compose.ui.graphics.Color>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        StatusBadge(badge.first, badge.second)
    }
}

/** 向导步骤行：序号圆点 + 标题 + （未完成时）右侧动作文字；已完成 = 主色 ✓ */
@Composable
private fun WizardStepRow(
    index: Int,
    title: String,
    done: Boolean,
    actionText: String,
    onAction: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (!done) it.clickable(onClick = onAction) else it }
            .padding(vertical = 7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    if (done) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    CircleShape,
                )
                .border(
                    1.dp,
                    if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(
                    Icons.Outlined.CheckCircle, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp),
                )
            } else {
                Text(
                    "$index",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        if (done) {
            Text(
                "已完成 ✓",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                actionText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ─────────────────────────── 卡片容器 ───────────────────────────

// ─────────────────────────── 工具级授权行（开发计划 §6.3） ───────────────────────────

private const val KEY_DEFAULT_POLICY = "__default__"

/** 七工具一句话说明（顺序同 ToolPolicy.TOOLS） */
private val TOOL_DESCS = mapOf(
    "read" to "读取文件",
    "write" to "写文件",
    "edit" to "改文件",
    "bash" to "执行命令",
    "grep" to "内容搜索",
    "find" to "按名查找",
    "ls" to "列目录",
)

private fun policyLabel(policy: String): String = when (policy) {
    ToolPolicy.ALLOW -> "允许"
    ToolPolicy.FORBID -> "禁止"
    else -> "每次询问"
}

/** 策略行：标题 + 说明 + 当前策略 + 展开箭头；展开后三选（允许 / 每次询问 / 禁止，选中打勾） */
@Composable
private fun ToolPolicyRow(
    title: String,
    desc: String,
    policy: String,
    expanded: Boolean,
    onClick: () -> Unit,
    onPick: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (desc.isNotBlank()) {
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Text(
                policyLabel(policy),
                style = MaterialTheme.typography.labelMedium,
                color = when (policy) {
                    ToolPolicy.ALLOW -> MaterialTheme.colorScheme.primary
                    ToolPolicy.FORBID -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp).size(18.dp),
            )
        }
        if (expanded) {
            PolicyOptionRow("允许", "直接执行，不再询问", ToolPolicy.ALLOW, policy, onPick)
            PolicyOptionRow("每次询问", "每次调用都弹授权（默认）", ToolPolicy.ASK, policy, onPick)
            PolicyOptionRow("禁止", "直接拦下，并把原因回给模型", ToolPolicy.FORBID, policy, onPick)
        }
    }
}

/** 策略选项行（缩进一格；选中 = 主色 + 对勾，整行可点） */
@Composable
private fun PolicyOptionRow(
    label: String,
    desc: String,
    value: String,
    current: String,
    onPick: (String) -> Unit,
) {
    val selected = value == current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(value) }
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            )
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.CheckCircle, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 分组卡片容器（与设置页 SettingsGroup 同款：surfaceContainerLow + 16dp 圆角 + 描边） */
@Composable
private fun PermissionCardBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
    ) {
        content()
    }
}

/** 卡内分隔线（内容已有 14dp 内边距，此处不再缩进） */
@Composable
private fun CardDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

/** 卡内小分组标题 */
@Composable
private fun GroupLabel(text: String, trailing: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
